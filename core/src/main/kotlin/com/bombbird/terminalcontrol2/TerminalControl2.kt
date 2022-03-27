package com.bombbird.terminalcontrol2

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.bombbird.terminalcontrol2.screens.MainMenu
import kotlinx.coroutines.launch
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.assets.async.AssetStorage
import ktx.assets.disposeSafely
import ktx.async.KtxAsync
import ktx.scene2d.*

/** Main game class, extending the [KtxGame] class */
class TerminalControl2 : KtxGame<KtxScreen>() {
    lateinit var batch: SpriteBatch
    val assetStorage = AssetStorage()

    /** Overrides [KtxGame.create] to also initiate [KtxAsync], and load assets using [AssetStorage]
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

            batch = SpriteBatch()

            addScreen(MainMenu(this@TerminalControl2))
            setScreen<MainMenu>()
        }
    }

    /** Overrides [KtxGame.dispose] to also dispose of [batch] and [assetStorage] */
    override fun dispose() {
        super.dispose()
        batch.disposeSafely()
        assetStorage.disposeSafely()
    }
}
