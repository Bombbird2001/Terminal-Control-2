from unittest.mock import AsyncMock, MagicMock, patch

import discord
import fastapi
import pytest
from fastapi.testclient import TestClient

import tc2_multiplayer_notifier_bot as bot


@pytest.fixture
def http_app():
	"""FastAPI app without Discord lifespan for HTTP-only tests."""
	app = fastapi.FastAPI()
	app.post('/servers_update')(bot.servers_update)
	return app


@pytest.fixture
def mock_bot_user_id():
	mock_client = MagicMock()
	mock_client.user = MagicMock(id=42)
	with patch('tc2_multiplayer_notifier_bot.client', mock_client):
		yield 42


@pytest.fixture(autouse=True)
def reset_bot_state():
	bot.initialized = False
	bot.status_message_ids.clear()
	bot.last_payload_snapshots.clear()
	bot.relay_games.clear()
	yield
	bot.initialized = False
	bot.status_message_ids.clear()
	bot.last_payload_snapshots.clear()
	bot.relay_games.clear()


class TestBuildStatusPayload:
	def test_single_game(self):
		content, embeds = bot.build_status_payload([
			bot.Server(airportName='KJFK', players=1, maxPlayers=4),
		])
		assert content == '🎮 **1 public multiplayer game** is ready to join!'
		assert len(embeds) == 1
		assert embeds[0].title == '🛫 KJFK · 1/4 players'

	def test_multiple_games(self):
		content, embeds = bot.build_status_payload([
			bot.Server(airportName='A', players=0, maxPlayers=2),
			bot.Server(airportName='B', players=2, maxPlayers=2),
		])
		assert content == '🎮 **2 public multiplayer games** are ready to join!'
		assert embeds[0].title == '🛫 A · 1/2 players'
		assert embeds[1].title == '🛫 B · 2/2 players'

	def test_empty_list(self):
		content, embeds = bot.build_status_payload([])
		assert content == '✈️ **No public multiplayer games** open right now - hop in-game to host one!'
		assert embeds == []

	def test_single_player_grammar(self):
		_, embeds = bot.build_status_payload([
			bot.Server(airportName='SOLO', players=1, maxPlayers=1),
		])
		assert embeds[0].title == '🛫 SOLO · 1/1 player'

	def test_cap_10_overflow_message(self):
		servers = [
			bot.Server(airportName=f'APT{i:02d}', players=1, maxPlayers=4)
			for i in range(12)
		]
		content, embeds = bot.build_status_payload(servers)
		assert len(embeds) == bot.MAX_STATUS_EMBEDS
		assert embeds[0].title == '🛫 APT00 · 1/4 players'
		assert embeds[-1].title == '🛫 APT09 · 1/4 players'
		assert content == '🎮 **12 public multiplayer games** are ready to join! (+ 2 more)'


class TestMergeRelayGames:
	def test_merge_two_relays(self):
		bot.relay_games['us'] = [bot.Server(airportName='A', players=1, maxPlayers=2)]
		bot.relay_games['eu'] = [
			bot.Server(airportName='B', players=1, maxPlayers=2),
			bot.Server(airportName='C', players=1, maxPlayers=2),
		]
		merged = bot.merge_relay_games()
		assert len(merged) == 3
		assert [server.airportName for server in merged] == ['A', 'B', 'C']

	def test_relay_empty_clears_only_that_relay(self):
		bot.relay_games['us'] = [bot.Server(airportName='A', players=1, maxPlayers=2)]
		bot.relay_games['eu'] = [bot.Server(airportName='B', players=1, maxPlayers=2)]
		bot.relay_games['us'] = []
		merged = bot.merge_relay_games()
		assert len(merged) == 1
		assert merged[0].airportName == 'B'


class TestPayloadSnapshot:
	def test_snapshot_is_hashable_and_stable(self):
		content, embeds = bot.build_status_payload([
			bot.Server(airportName='X', players=1, maxPlayers=2),
		])
		assert bot.payload_snapshot(content, embeds) == bot.payload_snapshot(content, embeds)

	def test_snapshot_changes_when_embeds_change(self):
		content_a, embeds_a = bot.build_status_payload([
			bot.Server(airportName='A', players=1, maxPlayers=2),
		])
		content_b, embeds_b = bot.build_status_payload([
			bot.Server(airportName='B', players=1, maxPlayers=2),
		])
		assert bot.payload_snapshot(content_a, embeds_a) != bot.payload_snapshot(content_b, embeds_b)


class TestGetDiscordToken:
	def test_missing_token_raises(self, monkeypatch):
		monkeypatch.delenv('TC2_DISCORD_BOT_TOKEN', raising=False)
		monkeypatch.delenv('DISCORD_BOT_TOKEN', raising=False)
		with pytest.raises(RuntimeError, match='TC2_DISCORD_BOT_TOKEN is not set'):
			bot.get_discord_token()

	def test_returns_tc2_token(self, monkeypatch):
		monkeypatch.setenv('TC2_DISCORD_BOT_TOKEN', 'tc2-token')
		assert bot.get_discord_token() == 'tc2-token'


def make_text_channel(channel_id: int = 100, name: str = 'tc2-multiplayer-bot') -> MagicMock:
	channel = MagicMock()
	channel.id = channel_id
	channel.name = name
	channel.send = AsyncMock()
	channel.fetch_message = AsyncMock()
	channel.get_partial_message = MagicMock(side_effect=lambda message_id: make_message(message_id))
	channel.history = MagicMock()
	return channel


def make_message(message_id: int = 999) -> MagicMock:
	message = MagicMock(spec=discord.Message)
	message.id = message_id
	message.edit = AsyncMock()
	return message


class TestChannelCanManageStatus:
	def test_requires_read_message_history(self):
		channel = MagicMock(spec=discord.TextChannel)
		me = MagicMock(spec=discord.Member)
		channel.permissions_for.return_value = MagicMock(
			read_messages=True, send_messages=True, read_message_history=False,
		)
		assert bot.channel_can_manage_status(channel, me) is False

	def test_accepts_full_permissions(self):
		channel = MagicMock(spec=discord.TextChannel)
		me = MagicMock(spec=discord.Member)
		channel.permissions_for.return_value = MagicMock(
			read_messages=True, send_messages=True, read_message_history=True,
		)
		assert bot.channel_can_manage_status(channel, me) is True


class TestScanChannelForStatusMessage:
	@pytest.mark.asyncio
	async def test_returns_latest_bot_message(self, mock_bot_user_id):
		channel = make_text_channel()
		older = MagicMock(author=MagicMock(id=mock_bot_user_id), id=1)
		newer = MagicMock(author=MagicMock(id=mock_bot_user_id), id=2)
		other = MagicMock(author=MagicMock(id=12345), id=3)

		async def history(limit):
			for message in [newer, other, older]:
				yield message

		channel.history = history

		assert await bot.scan_channel_for_status_message(channel) == 2

	@pytest.mark.asyncio
	async def test_returns_none_when_no_bot_messages(self, mock_bot_user_id):
		channel = make_text_channel()
		other = MagicMock(author=MagicMock(id=12345), id=3)

		async def history(limit):
			yield other

		channel.history = history

		assert await bot.scan_channel_for_status_message(channel) is None


class TestFetchStatusMessage:
	@pytest.mark.asyncio
	async def test_uses_cached_partial_message_without_fetch(self):
		channel = make_text_channel(channel_id=10)
		bot.status_message_ids[10] = 55

		result = await bot.fetch_status_message(channel)

		assert result.id == 55
		channel.get_partial_message.assert_called_once_with(55)
		channel.fetch_message.assert_not_awaited()

	@pytest.mark.asyncio
	async def test_scans_history_when_cache_missing(self, mock_bot_user_id):
		channel = make_text_channel(channel_id=11)

		with patch.object(bot, 'scan_channel_for_status_message', AsyncMock(return_value=77)):
			result = await bot.fetch_status_message(channel)

		assert result.id == 77
		assert bot.status_message_ids[11] == 77
		channel.get_partial_message.assert_called_with(77)

	@pytest.mark.asyncio
	async def test_returns_none_when_no_message_found(self):
		channel = make_text_channel(channel_id=12)

		with patch.object(bot, 'scan_channel_for_status_message', AsyncMock(return_value=None)):
			result = await bot.fetch_status_message(channel)

		assert result is None
		channel.send.assert_not_awaited()


class TestUpdateStatusMessage:
	@pytest.mark.asyncio
	async def test_skips_discord_when_payload_unchanged(self):
		channel = make_text_channel(channel_id=20)
		servers = [bot.Server(airportName='X', players=1, maxPlayers=2)]
		content, embeds = bot.build_status_payload(servers)
		bot.last_payload_snapshots[channel.id] = bot.payload_snapshot(content, embeds)

		with patch.object(bot, 'fetch_status_message', AsyncMock()) as fetch_status:
			await bot.update_status_message(channel, servers)

		fetch_status.assert_not_awaited()

	@pytest.mark.asyncio
	async def test_edits_existing_message(self):
		channel = make_text_channel(channel_id=21)
		message = make_message()
		servers = [bot.Server(airportName='X', players=2, maxPlayers=4)]

		with patch.object(bot, 'fetch_status_message', AsyncMock(return_value=message)):
			await bot.update_status_message(channel, servers)

		message.edit.assert_awaited_once()
		channel.send.assert_not_awaited()
		assert bot.last_payload_snapshots[21] == bot.payload_snapshot(*bot.build_status_payload(servers))

	@pytest.mark.asyncio
	async def test_sends_when_no_existing_message(self):
		channel = make_text_channel(channel_id=22)
		message = make_message()
		channel.send.return_value = message
		servers = [bot.Server(airportName='X', players=1, maxPlayers=2)]

		with patch.object(bot, 'fetch_status_message', AsyncMock(return_value=None)):
			await bot.update_status_message(channel, servers)

		channel.send.assert_awaited_once()
		message.edit.assert_not_awaited()
		assert bot.status_message_ids[22] == message.id

	@pytest.mark.asyncio
	async def test_clears_embeds_for_empty_server_list(self):
		channel = make_text_channel(channel_id=23)
		message = make_message()

		with patch.object(bot, 'fetch_status_message', AsyncMock(return_value=message)):
			await bot.update_status_message(channel, [])

		message.edit.assert_awaited_once_with(
			content='✈️ **No public multiplayer games** open right now - hop in-game to host one!',
			embeds=[],
		)

	@pytest.mark.asyncio
	async def test_retries_after_edit_http_exception(self):
		channel = make_text_channel(channel_id=24)
		message = make_message()
		replacement = make_message(1234)
		servers = [bot.Server(airportName='X', players=1, maxPlayers=2)]
		message.edit.side_effect = [discord.HTTPException(MagicMock(), 'edit failed'), None]

		with patch.object(
			bot,
			'fetch_status_message',
			AsyncMock(side_effect=[message, replacement]),
		):
			await bot.update_status_message(channel, servers)

		assert 24 not in bot.status_message_ids
		assert replacement.edit.await_count == 1
		channel.send.assert_not_awaited()


class TestRecoverStatusMessageIds:
	@pytest.mark.asyncio
	async def test_stores_recovered_ids(self):
		channel = make_text_channel(channel_id=30)

		with patch.object(bot, 'iter_status_channels', side_effect=lambda: iter([channel])):
			with patch.object(bot, 'scan_channel_for_status_message', AsyncMock(return_value=404)):
				await bot.recover_status_message_ids()

		assert bot.status_message_ids[30] == 404


class TestServersUpdateEndpoint:
	def test_returns_200_before_discord_ready(self, http_app):
		with TestClient(http_app) as client:
			response = client.post(
				'/servers_update',
				json={
					'relayId': 'test',
					'servers': [{'airportName': 'TEST', 'players': 1, 'maxPlayers': 4}],
				},
			)
		assert response.status_code == 200
		assert bot.relay_games['test'][0].airportName == 'TEST'

	@pytest.mark.asyncio
	async def test_updates_channels_with_merged_games(self):
		channel = make_text_channel()
		bot.initialized = True
		body = bot.ServersUpdateRequest(
			relayId='relay-a',
			servers=[bot.Server(airportName='TEST', players=1, maxPlayers=4)],
		)

		with patch.object(bot, 'iter_status_channels', side_effect=lambda: iter([channel])):
			with patch.object(bot, 'update_status_message', AsyncMock()) as update_status:
				await bot.servers_update(body)

		update_status.assert_awaited_once()
		merged_arg = update_status.await_args.args[1]
		assert len(merged_arg) == 1
		assert merged_arg[0].airportName == 'TEST'

	@pytest.mark.asyncio
	async def test_servers_update_stores_by_relay_id(self):
		channel = make_text_channel()
		bot.initialized = True

		with patch.object(bot, 'iter_status_channels', side_effect=lambda: iter([channel])):
			with patch.object(bot, 'update_status_message', AsyncMock()) as update_status:
				await bot.servers_update(bot.ServersUpdateRequest(
					relayId='relay-a',
					servers=[bot.Server(airportName='A', players=1, maxPlayers=2)],
				))
				await bot.servers_update(bot.ServersUpdateRequest(
					relayId='relay-b',
					servers=[bot.Server(airportName='B', players=1, maxPlayers=2)],
				))

		assert update_status.await_count == 2
		second_merged = update_status.await_args_list[1].args[1]
		assert len(second_merged) == 2
		assert [server.airportName for server in second_merged] == ['A', 'B']


class TestServersUpdatePayloadValidation:
	def test_rejects_invalid_json(self, http_app):
		with TestClient(http_app) as client:
			response = client.post('/servers_update', json={'relayId': 'x'})
		assert response.status_code == 422

	def test_rejects_missing_relay_id(self, http_app):
		with TestClient(http_app) as client:
			response = client.post(
				'/servers_update',
				json={'servers': [{'airportName': 'TEST', 'players': 1, 'maxPlayers': 4}]},
			)
		assert response.status_code == 422

	def test_rejects_missing_servers(self, http_app):
		with TestClient(http_app) as client:
			response = client.post('/servers_update', json={'relayId': 'test'})
		assert response.status_code == 422

	def test_rejects_invalid_server_entry(self, http_app):
		with TestClient(http_app) as client:
			response = client.post(
				'/servers_update',
				json={'relayId': 'test', 'servers': [{'airportName': 'TEST'}]},
			)
		assert response.status_code == 422

	def test_accepts_valid_request(self, http_app):
		with TestClient(http_app) as client:
			response = client.post(
				'/servers_update',
				json={
					'relayId': 'test',
					'servers': [{'airportName': 'TEST', 'players': 1, 'maxPlayers': 4}],
				},
			)
		assert response.status_code == 200
		assert bot.relay_games['test'][0].airportName == 'TEST'
