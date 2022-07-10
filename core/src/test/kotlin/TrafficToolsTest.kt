import com.bombbird.terminalcontrol2.traffic.calculateAdditionalTimeToNextDeparture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

object TrafficToolsTest {
    @Test
    @DisplayName("Time calculations")
    fun checkTimeCalculations() {
        assertEquals(0, calculateAdditionalTimeToNextDeparture(24))
        assertEquals(0, calculateAdditionalTimeToNextDeparture(15))
        assertEquals(0, calculateAdditionalTimeToNextDeparture(10))
        assertEquals(28, calculateAdditionalTimeToNextDeparture(3))
        assertEquals(96, calculateAdditionalTimeToNextDeparture(-14))
        assertEquals(120, calculateAdditionalTimeToNextDeparture(-20))
        assertEquals(210, calculateAdditionalTimeToNextDeparture(-29))
        assertEquals(320, calculateAdditionalTimeToNextDeparture(-40))
        assertEquals(320, calculateAdditionalTimeToNextDeparture(-47))
    }
}