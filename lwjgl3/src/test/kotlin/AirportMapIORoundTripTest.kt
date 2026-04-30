import com.bombbird.terminalcontrol2.editor.io.AirportMapIO
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object AirportMapIORoundTripTest : FunSpec({
    context("AirportMapIO arpt round-trip") {
        withData(listOf("TCTP", "TCSF", "TCTT")) { icao: String ->
            val path = findAirportFile(icao)
            Files.exists(path) shouldBe true
            val original = String(Files.readAllBytes(path))

            val canonical1 = AirportMapIO.serializeArpt(AirportMapIO.parseArpt(original))
            val canonical2 = AirportMapIO.serializeArpt(AirportMapIO.parseArpt(canonical1))

            // Canonicalization should stabilize after one pass.
            canonical2 shouldBe canonical1
        }
    }
})

private fun findAirportFile(icao: String): Path {
    val rel = Paths.get("assetsDesktop - Copy", "Airports", "$icao.arpt")
    var curr: Path = Paths.get("").toAbsolutePath()
    repeat(8) {
        val candidate = curr.resolve(rel)
        if (Files.exists(candidate)) return candidate
        curr = curr.parent ?: return@repeat
    }
    // Fall back to relative path (will fail assertion with a nicer clue)
    return rel
}

