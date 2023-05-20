package com.bombbird.terminalcontrol2

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.files.ExternalFileHandler
import com.bombbird.terminalcontrol2.files.loadPlayerSettings
import com.bombbird.terminalcontrol2.files.loadPlayerUUID
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.*
import com.bombbird.terminalcontrol2.networking.playerclient.LANClient
import com.bombbird.terminalcontrol2.networking.playerclient.LANClientDiscoveryHandler
import com.bombbird.terminalcontrol2.networking.playerclient.PublicClient
import com.bombbird.terminalcontrol2.screens.*
import com.bombbird.terminalcontrol2.screens.settings.CustomWeatherSettings
import com.bombbird.terminalcontrol2.screens.settings.GameSettings
import com.bombbird.terminalcontrol2.screens.settings.MainSettings
import com.bombbird.terminalcontrol2.screens.settings.TrafficSettings
import com.bombbird.terminalcontrol2.sounds.SoundManager
import com.bombbird.terminalcontrol2.ui.CustomDialog
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
class TerminalControl2(val externalFileHandler: ExternalFileHandler) : KtxGame<KtxScreen>(clearScreen = false) {
    lateinit var batch: SpriteBatch
    lateinit var engine: Engine
    lateinit var soundManager: SoundManager
    val assetStorage = AssetStorage()
    var gameServer: GameServer? = null
    var gameClientScreen: RadarScreen? = null
    val lanClientDiscoveryHandler = LANClientDiscoveryHandler()
    val lanClient = LANClient(lanClientDiscoveryHandler)
    val publicClient = PublicClient()

    /** Quits the current game running */
    fun quitCurrentGame() {
        // Quit the client, and if this client is also hosting the server it will be automatically closed
        // as part of the radarScreen's disposal process
        GAME.setScreen<MainMenu>()
        // Send the resume signal before quitting game, so the server doesn't remain paused and unable to quit
        gameClientScreen?.quitGame()
        gameClientScreen?.disposeSafely()
        GAME.removeScreen<RadarScreen>()
        GAME.removeScreen<GameLoading>()
        GAME.removeScreen<PauseScreen>()
        gameClientScreen = null
        GAME.removeScreen<GameSettings>()
        GAME.removeScreen<CustomWeatherSettings>()
        GAME.removeScreen<TrafficSettings>()
    }

    /**
     * Quits the current game, and show an additional dialog in the main menu upon exit
     * @param dialog the dialog to show
     */
    fun quitCurrentGameWithDialog(dialog: CustomDialog) {
        Gdx.app.postRunnable {
            quitCurrentGame()
            GAME.getScreen<MainMenu>().showDialog(dialog)
        }
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

                // Loading audio files
                load<Sound>("Audio/alert.wav")
                load<Sound>("Audio/conflict.wav")
                load<Sound>("Audio/initial_contact.wav")
                load<Sound>("Audio/rwy_change.wav")

                // Loading settings and player UUID
                loadPlayerSettings()
                loadPlayerUUID()
            }
            // Assets are loaded
            GAME = this@TerminalControl2

            batch = SpriteBatch()
            engine = Engine()
            soundManager = SoundManager()

            addScreen(MainMenu())
            addScreen(NewGame())
            addScreen(JoinGame())
            addScreen(LoadGame())
            addScreen(PauseScreen())
            addScreen(MainSettings())
            setScreen<MainMenu>()
        }

        BG_INDEX = MathUtils.random(1, 8)
    }

    /** Overrides [KtxGame.dispose] to also dispose of [batch], [assetStorage] and [soundManager] */
    override fun dispose() {
        super.dispose()
        batch.disposeSafely()
        assetStorage.disposeSafely()
        soundManager.disposeSafely()
        lanClient.dispose()
    }
}
