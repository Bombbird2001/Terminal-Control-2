import com.bombbird.terminalcontrol2.traffic.calculateAdditionalTimeToNextDeparture
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for testing traffic tools functions */
object TrafficToolsTest: FunSpec() {
    init {
        test("Time calculations") {
            calculateAdditionalTimeToNextDeparture(24) shouldBe 0
            calculateAdditionalTimeToNextDeparture(15) shouldBe 0
            calculateAdditionalTimeToNextDeparture(10) shouldBe 0
            calculateAdditionalTimeToNextDeparture(3) shouldBe 28
            calculateAdditionalTimeToNextDeparture(-14) shouldBe 96
            calculateAdditionalTimeToNextDeparture(-20) shouldBe 120
            calculateAdditionalTimeToNextDeparture(-29) shouldBe 210
            calculateAdditionalTimeToNextDeparture(-40) shouldBe 320
            calculateAdditionalTimeToNextDeparture(-47) shouldBe 320
        }
    }
}
