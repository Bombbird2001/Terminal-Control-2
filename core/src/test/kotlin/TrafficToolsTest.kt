import com.bombbird.terminalcontrol2.traffic.calculateTimeToNextDeparture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

object TrafficToolsTest {
    @Test
    @DisplayName("Time calculations")
    fun checkTimeCalculations() {
        assertEquals(90, calculateTimeToNextDeparture(24))
        assertEquals(90, calculateTimeToNextDeparture(15))
        assertEquals(90, calculateTimeToNextDeparture(10))
        assertEquals(118, calculateTimeToNextDeparture(3))
        assertEquals(186, calculateTimeToNextDeparture(-14))
        assertEquals(210, calculateTimeToNextDeparture(-20))
        assertEquals(300, calculateTimeToNextDeparture(-29))
        assertEquals(410, calculateTimeToNextDeparture(-40))
        assertEquals(410, calculateTimeToNextDeparture(-47))
    }
}