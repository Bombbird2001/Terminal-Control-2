import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.global.CLIENT_SCREEN
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.isGameInitialised
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.screens.RadarScreen

/** Initialises the game and game server for test purposes, if not already initialised */
internal fun testInitialiseGameAndServer() {
    if (!isGameInitialised) GAME = TerminalControl2()
    if (GAME.gameServer == null) GAME.gameServer = GameServer()
}

/** Initialises the game client screen for test purposes, if not already initialised */
internal fun testInitialiseGameClient() {
    if (CLIENT_SCREEN == null) GAME.gameClientScreen = RadarScreen(null, null, null)
}
