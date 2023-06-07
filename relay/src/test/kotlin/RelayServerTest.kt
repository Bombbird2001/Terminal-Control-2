import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.files.ExternalFileHandler
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.HttpRequest
import com.bombbird.terminalcontrol2.networking.hostserver.PublicServer
import com.bombbird.terminalcontrol2.networking.playerclient.PublicClient
import com.bombbird.terminalcontrol2.relay.RelayServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.util.UUID

/** Kotest FunSpec class for testing relay server functionality */
object RelayServerTest: FunSpec() {
    init {
        Secrets.RELAY_ADDRESS = LOCALHOST
        Secrets.RELAY_ENDPOINT_URL = "http://$LOCALHOST"

        // Start the relay server and endpoint
        RelayServer.main(arrayOf("test"))

        GAME = TerminalControl2(object : ExternalFileHandler {
            override fun selectAndReadFromFile(onComplete: (String?) -> Unit, onFailure: (String) -> Unit) {}

            override fun selectAndSaveToFile(data: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {}
        })

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

        afterSpec {
            RelayServer.stop()
        }
    }

    /** Connects to the relay server as host, creating a new game in the process, and returns the [PublicServer] object */
    private fun connectAsHost(): PublicServer {
        myUuid = UUID.randomUUID()
        val host = PublicServer(GameServer.testGameServer(), {_, _ -> }, {}, {}, "TEST")
        host.beforeConnect()
        host.start(TCP_PORT, UDP_PORT)
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
     */
    private fun connectAsClient(roomId: Short): PublicClient {
        myUuid = UUID.randomUUID()
        val client = PublicClient()
        client.beforeConnect(roomId)
        client.start()
        client.connect(5000, LOCALHOST, TCP_PORT, UDP_PORT)
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