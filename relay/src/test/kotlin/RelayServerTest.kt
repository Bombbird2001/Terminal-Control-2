import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.files.StubExternalFileHandler
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.integrations.StubAchievementHandler
import com.bombbird.terminalcontrol2.integrations.StubDiscordHandler
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.networking.hostserver.PublicServer
import com.bombbird.terminalcontrol2.networking.playerclient.PublicClient
import com.bombbird.terminalcontrol2.networking.relaygateway.RelayGatewayHost
import com.bombbird.terminalcontrol2.relay.RelayEndpoint
import com.bombbird.terminalcontrol2.relay.RelayServer
import com.bombbird.terminalcontrol2.sounds.StubTextToSpeech
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Kotest FunSpec class for testing relay server functionality */
object RelayServerTest: FunSpec() {
    init {
        val relayGateway = RelayGatewayHost(
            LOCALHOST,
            "http://$LOCALHOST",
            RelayEndpoint.RELAY_ENDPOINT_PORT,
            Secrets.RELAY_INSTANCES[0].relayEndpointAuthPw,
            Secrets.RELAY_INSTANCES[0].relayEndpointCreatePw,
        )

        // Start the relay server and endpoint
        RelayServer.main(arrayOf("test"))
        waitForRelayTcpReady()

        GAME = TerminalControl2(StubExternalFileHandler, StubTextToSpeech, StubDiscordHandler, StubAchievementHandler)

        test("No games running") {
            HttpRequest.sendPublicGamesRequest(relayGateway).size shouldBe 0
        }

        test("Create room, disconnect client then host") {
            val host = connectAsHost(relayGateway)

            val games = HttpRequest.sendPublicGamesRequest(relayGateway)
            games.size shouldBe 1
            games[0].roomId shouldBe host.getRoomConnectionInfo()?.roomId

            val client = connectAsClient(relayGateway, games[0].roomId.shouldNotBeNull(), host = host)
            client.disconnect()
            host.disconnect()

            HttpRequest.sendPublicGamesRequest(relayGateway).size shouldBe 0
        }

        test("Create room, disconnect host") {
            val host = connectAsHost(relayGateway)

            HttpRequest.sendPublicGamesRequest(relayGateway).apply {
                size shouldBe 1
                get(0).roomId shouldBe host.getRoomConnectionInfo()?.roomId
            }

            val client = connectAsClient(relayGateway, host.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), host = host)
            host.disconnect()
            client.isConnected.shouldBeFalse()

            HttpRequest.sendPublicGamesRequest(relayGateway).size shouldBe 0
        }

        test("Too many players") {
            val host = connectAsHost(relayGateway)

            HttpRequest.sendPublicGamesRequest(relayGateway).apply {
                size shouldBe 1
                get(0).roomId shouldBe host.getRoomConnectionInfo()?.roomId
            }

            val roomId = host.getRoomConnectionInfo()?.roomId.shouldNotBeNull()
            connectAsClient(relayGateway, roomId, host = host)
            connectAsClient(relayGateway, roomId, host = host)
            connectAsClient(relayGateway, roomId, host = host)
            connectAsClient(relayGateway, roomId, host = host)

            HttpRequest.sendPublicGamesRequest(relayGateway).size shouldBe 0

            shouldThrow<NullPointerException> {
                connectAsClient(relayGateway, roomId, host = host)
            }

            host.disconnect()
        }

        test("Incorrect room ID") {
            val host = connectAsHost(relayGateway)

            HttpRequest.sendPublicGamesRequest(relayGateway).apply {
                size shouldBe 1
                get(0).roomId shouldBe host.getRoomConnectionInfo()?.roomId
            }

            shouldThrow<NullPointerException> {
                connectAsClient(relayGateway, 0)
            }

            shouldThrow<NullPointerException> {
                connectAsClient(relayGateway, 350)
            }

            shouldThrow<NullPointerException> {
                connectAsClient(relayGateway, 777)
            }

            shouldThrow<NullPointerException> {
                connectAsClient(relayGateway, Short.MAX_VALUE)
            }

            host.disconnect()
        }

        test("Multiple rooms") {
            val host1 = connectAsHost(relayGateway)
            val host2 = connectAsHost(relayGateway)
            val host3 = connectAsHost(relayGateway)

            HttpRequest.sendPublicGamesRequest(relayGateway).apply {
                size shouldBe 3
                val idList = map { room -> room.roomId }
                host1.getRoomConnectionInfo()?.roomId shouldBeIn idList
                host2.getRoomConnectionInfo()?.roomId shouldBeIn idList
                host3.getRoomConnectionInfo()?.roomId shouldBeIn idList
            }

            connectAsClient(relayGateway, host1.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), host = host1)
            connectAsClient(relayGateway, host2.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), host = host2)
            connectAsClient(relayGateway, host3.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), host = host3)
            connectAsClient(relayGateway, host1.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), host = host1)
            connectAsClient(relayGateway, host2.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), host = host2)
            connectAsClient(relayGateway, host3.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), host = host3)

            HttpRequest.sendPublicGamesRequest(relayGateway).apply {
                size shouldBe 3
                val idList = map { room -> room.roomId }
                host1.getRoomConnectionInfo()?.roomId shouldBeIn idList
                host2.getRoomConnectionInfo()?.roomId shouldBeIn idList
                host3.getRoomConnectionInfo()?.roomId shouldBeIn idList
            }

            host1.disconnect()
            host2.disconnect()
            host3.disconnect()
        }

        test("Same UUID for multiple games") {
            val host = connectAsHost(relayGateway)

            HttpRequest.sendPublicGamesRequest(relayGateway).apply {
                size shouldBe 1
                get(0).roomId shouldBe host.getRoomConnectionInfo()?.roomId
            }

            val client = connectAsClient(relayGateway, host.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), host = host)

            shouldThrow<NullPointerException> {
                connectAsClient(relayGateway, host.getRoomConnectionInfo()?.roomId.shouldNotBeNull(), generateNewUUID = false, host = host)
            }

            HttpRequest.sendPublicGamesRequest(relayGateway).size shouldBe 1

            client.disconnect()
            host.disconnect()

            HttpRequest.sendPublicGamesRequest(relayGateway).size shouldBe 0
        }

        afterSpec {
            RelayServer.stop()
        }
    }

    /** Waits until the relay TCP port accepts connections */
    private fun waitForRelayTcpReady() {
        repeat(100) {
            try {
                Socket().use { it.connect(InetSocketAddress(LOCALHOST, RelayServer.RELAY_TCP_PORT), 200) }
                return
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        error("Relay TCP server did not start on port ${RelayServer.RELAY_TCP_PORT}")
    }

    /** Connects to the relay server as host, creating a new game in the process, and returns the [PublicServer] object */
    private fun connectAsHost(relayGateway: RelayGatewayHost): PublicServer {
        myUuid = UUID.randomUUID()
        val host = PublicServer(GameServer.testGameServer(), { _, _ -> }, {}, {}, "TEST", relayGateway)
        host.beforeStart().shouldBeTrue()
        host.start()
        host.isConnected.shouldBeTrue()

        Thread.sleep(1000)

        return host
    }

    /** Disconnects host from the relay server */
    private fun PublicServer.disconnect() {
        stop()
        isConnected.shouldBeFalse()

        Thread.sleep(1000)
    }

    /**
     * Connects to the relay server as client to the room ID, and returns the [PublicClient] object
     * @param relayGateway relay gateway connection information
     * @param roomId The room ID to connect to
     * @param generateNewUUID Whether to generate a new UUID for the new client
     * @param host optional host to take relay TCP/UDP ports from (defaults to fixed relay ports)
     */
    private fun connectAsClient(
        relayGateway: RelayGatewayHost,
        roomId: Short,
        generateNewUUID: Boolean = true,
        host: PublicServer? = null,
    ): PublicClient {
        if (generateNewUUID)
            myUuid = UUID.randomUUID()
        val client = PublicClient(relayGateway)
        client.beforeConnect(roomId)
        client.start()
        val roomInfo = host?.getRoomConnectionInfo()
        client.connect(
            5000,
            relayGateway.relayAddress,
            roomInfo?.tcpPort ?: RelayServer.RELAY_TCP_PORT,
            roomInfo?.udpPort ?: RelayServer.RELAY_UDP_PORT,
        )
        client.isConnected.shouldBeTrue()

        Thread.sleep(1000)

        return client
    }

    /** Disconnect client from the relay server */
    private fun PublicClient.disconnect() {
        stop()
        isConnected.shouldBeFalse()

        Thread.sleep(1000)
    }
}