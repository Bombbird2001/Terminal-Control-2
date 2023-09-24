import com.badlogic.gdx.math.Vector2
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Aircraft
import com.bombbird.terminalcontrol2.systems.updateAircraftRadarData
import com.bombbird.terminalcontrol2.utilities.ktToPxps
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import ktx.ashley.get
import ktx.ashley.plusAssign

object DataSystemTest: FunSpec() {
    init {
        beforeTest {
            testInitialiseGameAndServer()
        }

        test("Update aircraft radarData") {
            val ac = Aircraft("SHIBA4", 0f, 0f, 0f, "B77W", FlightType.ARRIVAL, false).entity
            ac.apply {
                this += Position(123.45f, 67.89f)
                this += Direction(Vector2(Vector2.Y).rotateDeg(45f))
                this += Speed(274.56f, -2000f, 1.5f)
                this += Altitude(7603.12f)
                this += GroundTrack(Vector2(Vector2.Y).rotateDeg(45.5f).scl(ktToPxps(283.41f)))
            }
            updateAircraftRadarData(ac)
            ac[RadarData.mapper]?.shouldNotBeNull()?.apply {
                position.x shouldBe 123.45f
                position.y shouldBe 67.89f
                direction.trackUnitVector.angleDeg() shouldBe 45f
                speed.speedKts shouldBe 274.56f
                speed.vertSpdFpm shouldBe -2000f
                speed.angularSpdDps shouldBe 1.5f
                altitude shouldBe 7603.12f
                groundSpeed shouldBe 283.41f
            }
        }
    }
}