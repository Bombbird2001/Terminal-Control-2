import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.files.StubExternalFileHandler
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.ConnectionMeta
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.networking.NetworkServer
import com.bombbird.terminalcontrol2.screens.RadarScreen
import com.bombbird.terminalcontrol2.sounds.StubTextToSpeech
import com.esotericsoftware.kryo.Kryo
import java.util.*
import kotlin.collections.AbstractList

/** Initialises the game and game server for test purposes, if not already initialised */
internal fun testInitialiseGameAndServer() {
    // Suppress entity created on wrong thread log message
    Thread.currentThread().name = GAME_SERVER_THREAD_NAME
    if (!isGameInitialised) GAME = TerminalControl2(StubExternalFileHandler, StubTextToSpeech)
    if (GAME.gameServer == null) {
        val newGameServer = GameServer.testGameServer()
        newGameServer.networkServer = object : NetworkServer(newGameServer, { _, _ -> }, { _ -> }, { _ -> }) {
            override val serverKryo = Kryo()

            override fun start(): Boolean { return true }

            override fun stop() {}

            override fun sendToAllTCP(data: Any) {}

            override fun sendToAllUDP(data: Any) {}

            override fun sendTCPToConnection(uuid: UUID, data: Any) {}

            override fun beforeStart(): Boolean { return true }

            override fun getRoomId(): Short? {
                return null
            }

            override fun getConnectionStatus(): String {
                return ""
            }

            override val connections = object : AbstractList<ConnectionMeta>() {
                override val size = 0

                override fun get(index: Int): ConnectionMeta {
                    return ConnectionMeta(UUID.randomUUID())
                }
            }

        }
        GAME.gameServer = newGameServer
        val engine = getEngine(false)
        engine.removeAllSystems()
        engine.removeAllEntities()
    }
}

/** Initialises the game client screen for test purposes, if not already initialised */
internal fun testInitialiseGameClient() {
    if (CLIENT_SCREEN == null) GAME.gameClientScreen = RadarScreen.newSinglePlayerRadarScreen()
}
