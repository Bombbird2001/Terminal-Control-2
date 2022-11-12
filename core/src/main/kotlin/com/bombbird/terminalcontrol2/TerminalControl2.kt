package com.bombbird.terminalcontrol2

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.files.loadPlayerSettings
import com.bombbird.terminalcontrol2.files.loadPlayerUUID
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.screens.*
import com.bombbird.terminalcontrol2.screens.settings.CustomWeatherSettings
import com.bombbird.terminalcontrol2.screens.settings.GameSettings
import com.bombbird.terminalcontrol2.screens.settings.MainSettings
import com.bombbird.terminalcontrol2.screens.settings.TrafficSettings
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import kotlinx.coroutines.launch
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.assets.async.AssetStorage
import ktx.assets.disposeSafely
import ktx.async.KtxAsync
import ktx.scene2d.*

/**
 * Main game class, extending the [KtxGame] class
 *
 * [clearScreen] is set to false as it will be handled by the individual screens
 * */
class TerminalControl2 : KtxGame<KtxScreen>(clearScreen = false) {
    lateinit var batch: SpriteBatch
    lateinit var engine: Engine
    val assetStorage = AssetStorage()
    var gameServer: GameServer? = null
    var gameClientScreen: RadarScreen? = null
    val gameClientDiscoveryHandler = GameClientDiscoveryHandler()
    var gameClient = Client(CLIENT_WRITE_BUFFER_SIZE, CLIENT_READ_BUFFER_SIZE).apply {
        setDiscoveryHandler(gameClientDiscoveryHandler)
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                if (obj != null && obj is IndividualSectorData) println("Handling ${obj::class.simpleName}")
                (obj as? RequestClientUUID)?.apply {
                    connection.sendTCP(ClientUUIDData(uuid.toString()))
                } ?: handleIncomingRequestClient(gameClientScreen ?: return, obj)
            }
        })
    }

    /** Quits the current game running */
    fun quitCurrentGame() {
        // Quit the client, and if this client is also hosting the server it will be automatically closed
        // as part of the radarScreen's disposal process
        GAME.setScreen<MainMenu>()
        // Send the resume signal before quitting game, so the server doesn't remain paused and unable to quit
        gameClientScreen?.resumeGame()
        gameClientScreen?.disposeSafely()
        GAME.removeScreen<RadarScreen>()
        gameClientScreen = null
        GAME.removeScreen<MainSettings>()
        GAME.removeScreen<GameSettings>()
        GAME.removeScreen<CustomWeatherSettings>()
        GAME.removeScreen<TrafficSettings>()
    }

    /**
     * Overrides [KtxGame.create] to also initiate [KtxAsync], and load assets using [AssetStorage]
     *
     * Sets the screen to [MainMenu] upon completion
     * */
    override fun create() {
        KtxAsync.initiate()
        KtxAsync.launch {
            assetStorage.apply {
                // Loading assets, the coroutine will suspend until each asset is loaded
                Scene2DSkin.defaultSkin = load("Skin/skin.json")
                for (i in 1..8) load<Texture>("Images/$i.png")
                load<Texture>("Images/MainMenuIcon.png")

                // Loading settings and player UUID
                loadPlayerSettings()
                loadPlayerUUID()
            }
            // Assets are loaded
            GAME = this@TerminalControl2

            batch = SpriteBatch()
            engine = Engine()

            addScreen(MainMenu())
            addScreen(NewGame())
            addScreen(JoinGame())
            addScreen(LoadGame())
            addScreen(PauseScreen())
            setScreen<MainMenu>()
        }

        BG_INDEX = MathUtils.random(1, 8)
    }

    /** Overrides [KtxGame.dispose] to also dispose of [batch] and [assetStorage] */
    override fun dispose() {
        super.dispose()
        batch.disposeSafely()
        assetStorage.disposeSafely()
        gameClient.dispose()
    }
}
