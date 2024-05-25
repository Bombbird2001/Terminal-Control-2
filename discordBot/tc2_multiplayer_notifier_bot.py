import asyncio
import discord
import fastapi
import os
from discord.utils import get
from pydantic import BaseModel
from typing import List


app = fastapi.FastAPI()
TOKEN = os.environ['DISCORD_BOT_TOKEN']
intents = discord.Intents(3072, guilds=True)
client = discord.Client(intents=intents)
initialized = False


class Server(BaseModel):
	airportName: str
	players: int
	maxPlayers: int


@app.post('/servers_update')
async def servers_update(servers: List[Server]):
	global initialized
	if not initialized:
		return
	embeds = [discord.Embed(title=f"{server.airportName} - {max(server.players, 1)}/{server.maxPlayers} player{'' if server.maxPlayers == 1 else 's'}") for server in servers]
	for guild in client.guilds:
		notify_role = get(guild.roles, name="Notify Multiplayer")
		for channel in guild.channels:
			if str(channel.type) != 'text':
				continue
			if channel.name != "tc2-multiplayer-bot":
				continue
			permissions = channel.permissions_for(guild.me)
			if permissions.read_messages and permissions.send_messages:
				await channel.send(f"{notify_role.mention if notify_role is not None and len(embeds) > 0 else ''}\n"
								   f"{len(embeds)} public multiplayer game{'' if len(embeds) == 1 else 's'} available",
								   embeds=embeds)
	

@client.event
async def on_ready():
	print(f'{client.user} has connected to Discord!')
	global initialized
	initialized = True


async def main():
	discord_task = asyncio.create_task(client.start(TOKEN))
	try:
		await discord_task
	except asyncio.exceptions.CancelledError:
		print("Interrupted")


asyncio.create_task(main())
