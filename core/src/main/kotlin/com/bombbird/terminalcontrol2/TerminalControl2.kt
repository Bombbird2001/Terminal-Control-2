package com.bombbird.terminalcontrol2

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.components.loadAllComponents
import com.bombbird.terminalcontrol2.files.*
import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.integrations.DiscordHandler
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
import com.bombbird.terminalcontrol2.sounds.TextToSpeechHandler
import com.bombbird.terminalcontrol2.sounds.TextToSpeechManager
import com.bombbird.terminalcontrol2.systems.loadAllFamilies
import com.bombbird.terminalcontrol2.ui.CustomDialog
import com.bombbird.terminalcontrol2.utilities.AndroidLifeCycleHandler
import com.bombbird.terminalcontrol2.utilities.FileLog
import com.bombbird.terminalcontrol2.utilities.loadCallsigns
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
 */
class TerminalControl2(val externalFileHandler: ExternalFileHandler, ttsHandler: TextToSpeechHandler,
                       val discordHandler: DiscordHandler) : KtxGame<KtxScreen>(clearScreen = false) {
    lateinit var batch: SpriteBatch
    lateinit var engine: Engine
    lateinit var soundManager: SoundManager
    val assetStorage = AssetStorage()
    var gameServer: GameServer? = null
    var gameClientScreen: RadarScreen? = null
    val lanClientDiscoveryHandler = LANClientDiscoveryHandler()
    val lanClient = LANClient(lanClientDiscoveryHandler)
    val publicClient = PublicClient()
    val ttsManager = TextToSpeechManager(ttsHandler)
    val androidLifeCycleHandler = AndroidLifeCycleHandler()

    init {
        GAME = this
    }

    /** Quits the current game running */
    fun quitCurrentGame() {
        FileLog.info("TerminalControl2", "Quitting current game")
        // Quit the client, and if this client is also hosting the server it will be automatically closed
        // as part of the radarScreen's disposal process
        GAME.setScreen<MainMenu>()
        // Send the resume signal before quitting game, so the server doesn't remain paused and unable to quit
        gameClientScreen?.quitGame()
        gameClientScreen?.disposeSafely()
        GAME.removeScreen<RadarScreen>()
        GAME.removeScreen<GameLoading>()
        gameClientScreen = null
        GAME.removeScreen<GameSettings>()
        GAME.removeScreen<CustomWeatherSettings>()
        GAME.removeScreen<TrafficSettings>()
        GAME.gameServer = null
        ttsManager.quitGame()
    }

    /**
     * Quits the current game, and show an additional dialog in the main menu upon exit
     * @param dialogCreator a function to create the dialog to show -  this is to prevent threading issues when
     * calling this function on a thread other than the main rendering thread
     */
    fun quitCurrentGameWithDialog(dialogCreator: () -> CustomDialog) {
        Gdx.app.postRunnable {
            quitCurrentGame()
            val dialog = dialogCreator()
            GAME.getScreen<MainMenu>().showDialog(dialogCreator())
            FileLog.warn("TerminalControl2", "Quitting current game with dialog ${dialog.text}")
        }
    }

    /**
     * Shows a dialog on the current screen
     * @param dialogCreator a function to create the dialog to show -  this is to prevent threading issues when
     * calling this function on a thread other than the main rendering thread
     */
    fun showDialogAtCurrentScreen(dialogCreator: () -> CustomDialog) {
        Gdx.app.postRunnable {
            val dialog = dialogCreator()
            val currentScreen = GAME.currentScreen
            (currentScreen as? ShowsDialog)?.showDialog(dialog)
        }
    }

    /**
     * Overrides [KtxGame.create] to also initiate [KtxAsync], and load assets using [AssetStorage]
     *
     * Sets the screen to [MainMenu] upon completion
     */
    override fun create() {
        APP_TYPE = Gdx.app.type

        KtxAsync.initiate()
        KtxAsync.launch {
            assetStorage.apply {
                // Loading assets, the coroutine will suspend until each asset is loaded
                Scene2DSkin.defaultSkin = load("Skin/skin.json")
                for (i in 1..9) load<Texture>("Images/$i.png")
                load<Texture>("Images/RadarBg.png")
                load<Texture>("Images/TextFg.png")
                load<Texture>("Images/Blip.png")

                // Loading audio files
                load<Sound>("Audio/alert.wav")
                load<Sound>("Audio/conflict.wav")
                load<Sound>("Audio/initial_contact.wav")
                load<Sound>("Audio/rwy_change.wav")

                // Loading settings and player UUID
                loadBuildVersion()
                loadPlayerSettings()
                loadPlayerUUID()
                loadAvailableAirports()
                loadAllDatatagLayouts()
                loadCallsigns()
            }

            // Initialize logging system
            FileLog.initializeFile()
            println("------------------------------------------------------")
            FileLog.info("TerminalControl2", "Game initialized")

            // Assets are loaded
            batch = SpriteBatch()
            engine = Engine()
            soundManager = SoundManager()

            addScreen(MainMenu())
            addScreen(NewGame())
            addScreen(JoinGame())
            addScreen(LoadGame())
            addScreen(ChooseMaxPlayers())
            addScreen(PauseScreen())
            addScreen(MainSettings())
            addScreen(AboutGame())
            addScreen(PrivacyPolicy())
            addScreen(SoftwareLicenses())
            addScreen(IndividualSoftwareLicense())
            addScreen(ReportBug())
            setScreen<MainMenu>()

            // Initialise Ashley component mapper indices, family bits to prevent threading issues when hosting
            loadAllComponents()
            loadAllFamilies()

            ttsManager.init()
            discordHandler.initialize()
        }

        BG_INDEX = MathUtils.random(1, 9)
    }

    /** Overrides [KtxGame.dispose] to also dispose of [batch], [assetStorage] and [soundManager] */
    override fun dispose() {
        super.dispose()
        batch.disposeSafely()
        assetStorage.disposeSafely()
        soundManager.disposeSafely()
        lanClient.dispose()
        publicClient.dispose()
        FileLog.close()
        ttsManager.disposeSafely()
        discordHandler.quit()
    }
}
