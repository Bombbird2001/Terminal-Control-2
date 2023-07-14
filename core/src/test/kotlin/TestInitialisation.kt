import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.files.ExternalFileHandler
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.isGameInitialised
import com.bombbird.terminalcontrol2.networking.ConnectionMeta
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.NetworkServer
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.esotericsoftware.kryo.Kryo
import java.util.*
import kotlin.collections.AbstractList

/** Initialises the game and game server for test purposes, if not already initialised */
internal fun testInitialiseGameAndServer() {
    if (!isGameInitialised) GAME = TerminalControl2(object : ExternalFileHandler {
        override fun selectAndReadFromFile(onComplete: (String?) -> Unit, onFailure: (String) -> Unit) {}

        override fun selectAndSaveToFile(data: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {}
    })
    if (GAME.gameServer == null) {
        val newGameServer = GameServer.testGameServer()
        newGameServer.networkServer = object : NetworkServer(newGameServer, { _, _ -> }, { _ -> }, { _ -> }) {
            override val kryo = Kryo()

            override fun start() {}

            override fun stop() {}

            override fun sendToAllTCP(data: Any) {}

            override fun sendToAllUDP(data: Any) {}

            override fun sendTCPToConnection(uuid: UUID, data: Any) {}

            override fun beforeStart(): Boolean { return true }

            override fun getRoomId(): Short? {
                return null
            }

            override val connections = object : AbstractList<ConnectionMeta>() {
                override val size = 0

                override fun get(index: Int): ConnectionMeta {
                    return ConnectionMeta(UUID.randomUUID())
                }
            }

        }
        GAME.gameServer = newGameServer
    }
}

/** Initialises the game client screen for test purposes, if not already initialised */
internal fun testInitialiseGameClient() {
    if (CLIENT_SCREEN == null) GAME.gameClientScreen = RadarScreen.newSinglePlayerRadarScreen()
}
