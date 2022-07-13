import com.bombbird.terminalcontrol2.components.CommandTarget
import com.bombbird.terminalcontrol2.utilities.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for testing math tools functions */
object MathToolsTest: FunSpec() {
    init {
        test("Unit conversions") {
            mToNm(1f) shouldBe 0.0005399568f.plusOrMinus(0.00000001f)
            mToFt(1f) shouldBe 3.280840f.plusOrMinus(0.0001f)
            ftToM(1f) shouldBe 0.3048f.plusOrMinus(0.00001f)
            mpsToKt(1f) shouldBe 1.943844f.plusOrMinus(0.0001f)
            ktToMps(1f) shouldBe 0.514444f.plusOrMinus(0.00001f)
            ktToFpm(1f) shouldBe 101.269f.plusOrMinus(0.01f)
            mpsToFpm(1f) shouldBe 196.8504f.plusOrMinus(0.01f)
            fpmToMps(2000f) shouldBe 10.16f.plusOrMinus(0.001f)
        }

        test("Unit conversions involving pixels") {
            nmToPx(1) shouldBe NM_TO_PX
            nmToPx(1.0f) shouldBe NM_TO_PX
            pxToNm(1) shouldBe 1 / NM_TO_PX
            pxToNm(1.0f) shouldBe 1 / NM_TO_PX
            ftToPx(1) shouldBe NM_TO_PX / 6076.12f
            ftToPx(1.0f) shouldBe NM_TO_PX / 6076.12f
            pxToFt(1) shouldBe 6076.12f / NM_TO_PX
            pxToFt(1.0f) shouldBe 6076.12f / NM_TO_PX
            mToPx(1) shouldBe NM_TO_PX / 1852
            mToPx(1.0f) shouldBe NM_TO_PX / 1852
            pxToM(1) shouldBe 1852 / NM_TO_PX
            pxToM(1.0f) shouldBe 1852 / NM_TO_PX
            ktToPxps(1) shouldBe NM_TO_PX / 3600
            ktToPxps(1.0f) shouldBe NM_TO_PX / 3600
            pxpsToKt(1.0f) shouldBe 3600 / NM_TO_PX
        }

        test("Heading calculations") {
            findDeltaHeading(360f, 50f, CommandTarget.TURN_DEFAULT) shouldBe 50f
            findDeltaHeading(360f, 50f, CommandTarget.TURN_RIGHT) shouldBe 50f
            findDeltaHeading(360f, 50f, CommandTarget.TURN_LEFT) shouldBe -310f
            findDeltaHeading(-30f, 450f, CommandTarget.TURN_DEFAULT) shouldBe 120f
            findDeltaHeading(-30f, 450f, CommandTarget.TURN_RIGHT) shouldBe 120f
            findDeltaHeading(-30f, 450f, CommandTarget.TURN_LEFT) shouldBe -240f
        }

        test("Arc calculations") {
            checkInArc(0f, 0f, 45f, 10f, 35f, 2f, 2f) shouldBe true
            checkInArc(100f, -100f, 45f, 300f, 35f, 300f, 100f) shouldBe true
            checkInArc(0f, 0f, 45f, 10f, 35f, 1f, 2f) shouldBe true
            checkInArc(100f, -100f, 45f, 300f, 35f, 200f, 100f) shouldBe true
            checkInArc(0f, 0f, 45f, 10f, 35f, 0f, 2f) shouldBe false
            checkInArc(100f, -100f, 45f, 300f, 35f, 100f, 100f) shouldBe false
            checkInArc(0f, 0f, 45f, 1f, 35f, 2f, 2f) shouldBe false
            checkInArc(100f, -100f, 45f, 100f, 35f, 300f, 100f) shouldBe false
        }

        test("Distance calculations") {
            // Delta set at 5th significant figure
            calculateDistanceBetweenPoints(0f, 0f, 10f, 20f) shouldBe 22.3607f.plusOrMinus(0.001f)
            calculateDistanceBetweenPoints(0f, 0f, -20f, -10f) shouldBe 22.3607f.plusOrMinus(0.001f)
            calculateDistanceBetweenPoints(0f, 0f, 50f, 50f) shouldBe 70.7107f.plusOrMinus(0.001f)
        }
    }
}