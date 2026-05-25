import asyncio
import discord
import fastapi
import logging
import os
from contextlib import asynccontextmanager
from pydantic import BaseModel
from typing import Iterator, List

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
logger = logging.getLogger('tc2_multiplayer_notifier')


def get_discord_token() -> str:
	token = os.environ.get('TC2_DISCORD_BOT_TOKEN')
	if not token:
		raise RuntimeError('TC2_DISCORD_BOT_TOKEN is not set')
	return token


@asynccontextmanager
async def lifespan(_app: fastapi.FastAPI):
	discord_task = asyncio.create_task(client.start(get_discord_token()))
	yield
	await client.close()
	discord_task.cancel()
	try:
		await discord_task
	except asyncio.CancelledError:
		pass


app = fastapi.FastAPI(lifespan=lifespan)
intents = discord.Intents(3072, guilds=True)
client = discord.Client(intents=intents)
initialized = False

status_message_ids: dict[int, int] = {}
last_payload_snapshots: dict[int, tuple[str, tuple[str, ...]]] = {}

MAX_STATUS_EMBEDS = 10


class Server(BaseModel):
	airportName: str
	players: int
	maxPlayers: int


class ServersUpdateRequest(BaseModel):
	relayId: str
	servers: list[Server]


relay_games: dict[str, list[Server]] = {}


def merge_relay_games() -> list[Server]:
	merged: list[Server] = []
	for games in relay_games.values():
		merged.extend(games)
	merged.sort(key=lambda server: server.airportName)
	return merged


def build_status_payload(servers: List[Server]) -> tuple[str, list[discord.Embed]]:
	total_count = len(servers)
	display_servers = servers[:MAX_STATUS_EMBEDS]
	overflow = total_count - len(display_servers)
	embeds = [
		discord.Embed(
			title=(
				f"🛫 {server.airportName} · {max(server.players, 1)}/{server.maxPlayers} "
				f"player{'' if server.maxPlayers == 1 else 's'}"
			)
		)
		for server in display_servers
	]
	if total_count == 0:
		content = "✈️ **No public multiplayer games** open right now - hop in-game to host one!"
	elif total_count == 1:
		content = "🎮 **1 public multiplayer game** is ready to join!"
	else:
		content = f"🎮 **{total_count} public multiplayer games** are ready to join!"
		if overflow > 0:
			content += f" (+ {overflow} more)"
	return content, embeds


def payload_snapshot(content: str, embeds: list[discord.Embed]) -> tuple[str, tuple[str, ...]]:
	return content, tuple(embed.title for embed in embeds)


def channel_can_manage_status(channel: discord.TextChannel, me: discord.Member) -> bool:
	permissions = channel.permissions_for(me)
	return (
		permissions.read_messages
		and permissions.send_messages
		and permissions.read_message_history
	)


def iter_status_channels() -> Iterator[discord.TextChannel]:
	for guild in client.guilds:
		me = guild.me
		if me is None:
			continue
		for channel in guild.text_channels:
			if channel.name != "tc2-multiplayer-bot":
				continue
			if channel_can_manage_status(channel, me):
				yield channel
			else:
				logger.info(
					'Skipping #%s (%s): bot needs View Channel, Send Messages, and Read Message History',
					channel.name, guild.name,
				)


async def scan_channel_for_status_message(channel: discord.TextChannel) -> int | None:
	if client.user is None:
		logger.debug('scan #%s: client.user is None', channel.name)
		return None
	permissions = channel.permissions_for(channel.guild.me)
	if not permissions.read_message_history:
		logger.warning('scan #%s: missing read_message_history permission', channel.name)
		return None
	async for message in channel.history(limit=50):
		if message.author.id == client.user.id:
			logger.info('scan #%s: found bot message id=%s', channel.name, message.id)
			return message.id
	logger.info('scan #%s: no bot messages in last 50', channel.name)
	return None


def cached_status_message(channel: discord.TextChannel) -> discord.Message | None:
	"""Return a message reference for edit without Read Message History (no fetch)."""
	cached_id = status_message_ids.get(channel.id)
	if cached_id is None:
		return None
	logger.info('cache #%s (channel_id=%s): using partial message id=%s', channel.name, channel.id, cached_id)
	return channel.get_partial_message(cached_id)


async def fetch_status_message(channel: discord.TextChannel) -> discord.Message | None:
	cached = cached_status_message(channel)
	if cached is not None:
		return cached

	message_id = await scan_channel_for_status_message(channel)
	if message_id is not None:
		status_message_ids[channel.id] = message_id
		logger.info('fetch #%s: using scanned message id=%s', channel.name, message_id)
		return channel.get_partial_message(message_id)
	logger.info('fetch #%s: no status message found', channel.name)
	return None


async def recover_status_message_ids() -> None:
	for channel in iter_status_channels():
		message_id = await scan_channel_for_status_message(channel)
		if message_id is not None:
			status_message_ids[channel.id] = message_id
			logger.info('Recovered status message %s in #%s (%s)', message_id, channel.name, channel.guild.name)


async def update_status_message(channel: discord.TextChannel, servers: List[Server]) -> None:
	content, embeds = build_status_payload(servers)
	snapshot = payload_snapshot(content, embeds)
	if last_payload_snapshots.get(channel.id) == snapshot:
		logger.info('update #%s: unchanged payload, skipping', channel.name)
		return

	message = await fetch_status_message(channel)
	if message is None:
		message = await channel.send(content=content, embeds=embeds)
		status_message_ids[channel.id] = message.id
		logger.info('update #%s: sent new message id=%s', channel.name, message.id)
	else:
		try:
			await message.edit(content=content, embeds=embeds or [])
			logger.info('update #%s: edited message id=%s', channel.name, message.id)
		except discord.HTTPException as error:
			logger.error('update #%s: edit failed for message %s: %s', channel.name, message.id, error)
			status_message_ids.pop(channel.id, None)
			last_payload_snapshots.pop(channel.id, None)
			message = await fetch_status_message(channel)
			if message is None:
				message = await channel.send(content=content, embeds=embeds)
				status_message_ids[channel.id] = message.id
				logger.info('update #%s: sent replacement message id=%s after edit failure', channel.name, message.id)
			else:
				await message.edit(content=content, embeds=embeds or [])
				logger.info('update #%s: edited recovered message id=%s after edit failure', channel.name, message.id)

	last_payload_snapshots[channel.id] = snapshot


@app.post('/servers_update')
async def servers_update(body: ServersUpdateRequest):
	relay_games[body.relayId] = body.servers
	combined = merge_relay_games()
	logger.info(
		'servers_update: relay=%s relay_games=%s merged=%s initialized=%s',
		body.relayId, len(body.servers), len(combined), initialized,
	)
	if not initialized:
		logger.warning('servers_update: ignored because Discord client is not ready')
		return
	channels = list(iter_status_channels())
	logger.info('servers_update: updating %s channel(s)', len(channels))
	for channel in channels:
		try:
			await update_status_message(channel, combined)
		except Exception as error:
			logger.exception('Failed to update status in #%s (%s)', channel.name, channel.guild.name)


@client.event
async def on_ready():
	logger.info('%s has connected to Discord!', client.user)
	global initialized
	await recover_status_message_ids()
	initialized = True
