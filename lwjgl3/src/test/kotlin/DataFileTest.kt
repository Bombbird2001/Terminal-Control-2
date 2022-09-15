import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.bombbird.terminalcontrol2.global.AVAIL_AIRPORTS
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe

/** Kotest FunSpec class for checking data file validity */
object DataFileTest: FunSpec() {
    init {
        Gdx.files = Lwjgl3Files()

        context("Aircraft data file check") {
            val handle = Gdx.files.internal("Data/aircraft.perf")
            handle.exists().shouldBeTrue()
            val allAircraft = handle.readString().split("\\r?\\n".toRegex()).map { it.trim() }.filter {
                it.length shouldBeGreaterThan 0
                it[0] != '#'
            }.toList()
            withData(allAircraft) {
                val acData = it.split(" ")
                acData.size shouldBe 14
                acData[0].length shouldBe 4
                acData[1] shouldBeIn arrayOf("L", "M", "H", "J")
                acData[2] shouldBeIn arrayOf("A", "B", "C", "D", "E", "F")
                acData[3].toInt()
                acData[4].toInt()
                acData[5].toFloat()
                acData[6].toFloat()
                acData[7].toFloat()
                acData[8].toShort() shouldBeLessThan 390
                acData[9].toFloat() shouldBeLessThan 1f
                acData[10].toShort() shouldBeLessThan 200
                acData[11].toShort() shouldBeLessThan 200
                acData[12].toInt()
                acData[13].toInt()
            }
        }

        context("Airport data file check") {
            withData(AVAIL_AIRPORTS.toList()) {
                val handle = Gdx.files.internal("Airports/${it}.arpt")
                handle.exists().shouldBeTrue()

            }
        }
    }
}