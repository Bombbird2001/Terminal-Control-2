import com.bombbird.terminalcontrol2.traffic.calculateAdditionalTimeToNextDeparture
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for testing traffic tools functions */
object TrafficToolsTest: FunSpec() {
    init {
        test("Time calculations") {
            calculateAdditionalTimeToNextDeparture(24, 40) shouldBe 0
            calculateAdditionalTimeToNextDeparture(15, 40) shouldBe 0
            calculateAdditionalTimeToNextDeparture(10, 40) shouldBe 0
            calculateAdditionalTimeToNextDeparture(3, 40) shouldBe 28
            calculateAdditionalTimeToNextDeparture(-14, 40) shouldBe 96
            calculateAdditionalTimeToNextDeparture(-6, 20) shouldBe 96
            calculateAdditionalTimeToNextDeparture(-20, 40) shouldBe 120
            calculateAdditionalTimeToNextDeparture(-29, 40) shouldBe 210
            calculateAdditionalTimeToNextDeparture(-58, 80) shouldBe 210
            calculateAdditionalTimeToNextDeparture(-40, 40) shouldBe 320
            calculateAdditionalTimeToNextDeparture(-47, 40) shouldBe 320
            calculateAdditionalTimeToNextDeparture(-30, 20) shouldBe 320
            calculateAdditionalTimeToNextDeparture(-15, 10) shouldBe 320
        }
    }
}
