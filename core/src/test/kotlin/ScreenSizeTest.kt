import com.bombbird.terminalcontrol2.global.UI_HEIGHT
import com.bombbird.terminalcontrol2.global.UI_WIDTH
import com.bombbird.terminalcontrol2.graphics.ScreenSize
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for testing screen size functions */
object ScreenSizeTest: FunSpec() {
    init {
        test("Screen size calculation") {
            ScreenSize.updateScreenSizeParameters(2000, 1000)
            UI_WIDTH shouldBe 1920
            UI_HEIGHT shouldBe 960

            ScreenSize.updateScreenSizeParameters(1600, 1200)
            UI_WIDTH shouldBe 1440
            UI_HEIGHT shouldBe 1080

            ScreenSize.updateScreenSizeParameters(1920, 1080)
            UI_WIDTH shouldBe 1920
            UI_HEIGHT shouldBe 1080

            ScreenSize.updateScreenSizeParameters(1600, 900)
            UI_WIDTH shouldBe 1920
            UI_HEIGHT shouldBe 1080
        }
    }
}