import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.files.StubExternalFileHandler
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.integrations.StubAchievementHandler
import com.bombbird.terminalcontrol2.integrations.StubDiscordHandler
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.networking.hostserver.PublicServer
import com.bombbird.terminalcontrol2.networking.playerclient.PublicClient
import com.bombbird.terminalcontrol2.relay.RelayServer
import com.bombbird.terminalcontrol2.sounds.StubTextToSpeech
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import java.util.UUID

/** Kotest FunSpec class for testing relay server functionality */
object RelayServerTest: FunSpec() {
    init {
        Secrets.RELAY_ADDRESS = LOCALHOST
        Secrets.RELAY_ENDPOINT_URL = "http://$LOCALHOST"

        // Start the relay server and endpoint
        RelayServer.main(arrayOf("test"))

        GAME = TerminalControl2(StubExternalFileHandler, StubTextToSpeech, StubDiscordHandler, StubAchievementHandler)

        test("No games running") {
            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 0
            }
        }

        test("Create room, disconnect client then host") {
            val host = connectAsHost()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 1
                it[0].roomId shouldBe host.getRoomId()
            }

            val client = connectAsClient(host.getRoomId())
            client.disconnect()
            host.disconnect()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 0
            }
        }

        test("Create room, disconnect host") {
            val host = connectAsHost()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 1
                it[0].roomId shouldBe host.getRoomId()
            }

            val client = connectAsClient(host.getRoomId())
            host.disconnect()
            client.isConnected.shouldBeFalse()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 0
            }
        }

        test("Too many players") {
            val host = connectAsHost()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 1
                it[0].roomId shouldBe host.getRoomId()
            }

            connectAsClient(host.getRoomId())
            connectAsClient(host.getRoomId())
            connectAsClient(host.getRoomId())
            connectAsClient(host.getRoomId())

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 0
            }

            shouldThrow<NullPointerException> {
                connectAsClient(host.getRoomId())
            }

            host.disconnect()
        }

        test("Incorrect room ID") {
            val host = connectAsHost()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 1
                it[0].roomId shouldBe host.getRoomId()
            }

            shouldThrow<NullPointerException> {
                connectAsClient(0)
            }

            shouldThrow<NullPointerException> {
                connectAsClient(350)
            }

            shouldThrow<NullPointerException> {
                connectAsClient(777)
            }

            shouldThrow<NullPointerException> {
                connectAsClient(Short.MAX_VALUE)
            }

            host.disconnect()
        }

        test("Multiple rooms") {
            val host1 = connectAsHost()
            val host2 = connectAsHost()
            val host3 = connectAsHost()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 3
                val idList = it.map { room -> room.roomId }
                host1.getRoomId() shouldBeIn idList
                host2.getRoomId() shouldBeIn idList
                host3.getRoomId() shouldBeIn idList
            }

            connectAsClient(host1.getRoomId())
            connectAsClient(host2.getRoomId())
            connectAsClient(host3.getRoomId())
            connectAsClient(host1.getRoomId())
            connectAsClient(host2.getRoomId())
            connectAsClient(host3.getRoomId())

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 3
                val idList = it.map { room -> room.roomId }
                host1.getRoomId() shouldBeIn idList
                host2.getRoomId() shouldBeIn idList
                host3.getRoomId() shouldBeIn idList
            }

            host1.disconnect()
            host2.disconnect()
            host3.disconnect()
        }

        test("Same UUID for multiple games") {
            val host = connectAsHost()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 1
                it[0].roomId shouldBe host.getRoomId()
            }

            val client = connectAsClient(host.getRoomId())

            shouldThrow<NullPointerException> {
                connectAsClient(host.getRoomId(), false)
            }

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 1
            }

            client.disconnect()
            host.disconnect()

            HttpRequest.sendPublicGamesRequest {
                it.size shouldBe 0
            }
        }

        afterSpec {
            RelayServer.stop()
        }
    }

    /** Connects to the relay server as host, creating a new game in the process, and returns the [PublicServer] object */
    private fun connectAsHost(): PublicServer {
        myUuid = UUID.randomUUID()
        val host = PublicServer(GameServer.testGameServer(), {_, _ -> }, {}, {}, "TEST")
        host.beforeStart()
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
     * @param roomId The room ID to connect to
     * @param generateNewUUID Whether to generate a new UUID for the new client
     */
    private fun connectAsClient(roomId: Short, generateNewUUID: Boolean = true): PublicClient {
        if (generateNewUUID)
            myUuid = UUID.randomUUID()
        val client = PublicClient()
        client.beforeConnect(roomId)
        client.start()
        client.connect(5000, LOCALHOST, RELAY_TCP_PORT, RELAY_UDP_PORT)
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