package com.bombbird.terminalcontrol2

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.bombbird.terminalcontrol2.global.BG_INDEX
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.screens.*
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
            }
            // Assets are loaded
            GAME = this@TerminalControl2

            batch = SpriteBatch()
            engine = Engine()

            addScreen(MainMenu())
            addScreen(NewGame())
            addScreen(GameLoading())
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
    }
}
