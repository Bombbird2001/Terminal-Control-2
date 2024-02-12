import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.math.Polygon
import com.bombbird.terminalcontrol2.components.FlightType
import com.bombbird.terminalcontrol2.files.*
import com.bombbird.terminalcontrol2.global.AVAIL_AIRPORTS
import com.bombbird.terminalcontrol2.navigation.Route
import com.bombbird.terminalcontrol2.navigation.findMissedApproachAlt
import com.bombbird.terminalcontrol2.utilities.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.scopes.ContainerScope
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.bytes.shouldBeBetween
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeIn
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.ints.*
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.short.shouldBeBetween
import io.kotest.matchers.shouldBe
import ktx.assets.toInternalFile
import ktx.collections.GdxArray
import ktx.collections.contains
import java.lang.NumberFormatException
import java.lang.RuntimeException

/** Kotest FunSpec class for checking data file validity */
object DataFileTest: FunSpec() {
    private val WAKE_CATS = arrayOf("L", "M", "H", "J")
    private val RECAT_CATS = arrayOf("A", "B", "C", "D", "E", "F")
    private val MVA_SECTOR_SHAPE = arrayOf("POLYGON", "CIRCLE")
    private val MVA_SECTOR_TYPE = arrayOf("MVA", "RESTR")
    private val DIRECTION = arrayOf("RIGHT", "LEFT")
    private val RWY_POS = arrayOf('L', 'C', 'R')
    private val LABEL_POS = arrayOf("LABEL_LEFT", "LABEL_RIGHT", "LABEL_BEFORE")
    private val TIME_SLOTS = arrayOf("DAY_ONLY", "NIGHT_ONLY", "DAY_NIGHT")
    private val WARNING_SHOULD_BE_EMPTY: (String, String) -> Unit = { type: String, warning: String -> "[$type] $warning" shouldBe "" }

    private val aircraftSet = HashSet<String>()

    init {
        Gdx.files = Lwjgl3Files()

        context("Aircraft data file check") {
            val handle = "Data/aircraft.perf".toInternalFile()
            handle.exists().shouldBeTrue()
            val allAircraft = handle.readString().toLines().map { it.trim() }.filter {
                it.length shouldBeGreaterThan 0
                it[0] != '#'
            }.toList()
            withData(allAircraft) {
                val acData = it.split(" ")
                acData.size shouldBe 15
                acData[0].length shouldBeIn arrayOf(3, 4)
                acData[1] shouldBeIn WAKE_CATS
                acData[2] shouldBeIn RECAT_CATS
                try {
                    val thrustN = acData[3].toInt()
                    val propPowerKw = acData[4].toInt()
                    acData[5].toFloat()
                    val dragCd0 = acData[6].toFloat()
                    val dragMaxCd = acData[7].toFloat()
                    val vmo = acData[8].toShort()
                    vmo shouldBeLessThan 390
                    val mmo = acData[9].toFloat()
                    mmo shouldBeLessThan 1f
                    val ceiling = acData[10].toInt()
                    ceiling shouldBeInRange 10000..60000
                    val vapp = acData[11].toShort()
                    vapp shouldBeLessThan 200
                    val v2 = acData[12].toShort()
                    v2 shouldBeLessThan 200
                    val oew = acData[13].toInt()
                    val mtow = acData[14].toInt()

                    // Test aircraft initial climb performance
                    val aircraftPerf = AircraftTypeData.AircraftPerfData(acData[1][0], acData[2][0], thrustN,
                        propPowerKw, 1f, dragCd0, dragMaxCd, vmo, mmo, ceiling, vapp, v2, oew, mtow,
                        FlightType.DEPARTURE).apply {
                            massKg = mtow
                    }
                    val alt = 2500f
                    val tasKt = calculateTASFromIAS(alt, v2.toFloat())
                    // We exclude the A340-200 and A340-300 because they're not the best at climbing at MTOW
                    if (!GdxArray<String>(arrayOf("A342", "A343")).contains(acData[0]))
                        calculateMaxVerticalSpd(aircraftPerf, alt, tasKt, 0f,  onApproach = false,
                            takingOff = false, takeoffGoAround = false) shouldBeGreaterThan 1000f
                } catch (e: NumberFormatException) {
                    e.stackTraceToString().shouldBeNull()
                }
                aircraftSet.add(acData[0])
            }
        }

        context("Airport data file check") {
            val airports = ArrayList<String>(AVAIL_AIRPORTS.size)
            for (entry in AVAIL_AIRPORTS) {
                airports.add(entry.key)
            }
            withData(airports) {
                val handle = "Airports/${it}.arpt".toInternalFile()
                handle.exists().shouldBeTrue()
                val descHandle = "Airports/${it}.desc".toInternalFile()
                descHandle.exists().shouldBeTrue()
                val data = handle.readString()
                try {
                    // 1. Check all global values are present and within acceptable range
                    val minAlt = getAllTextAfterHeader("MIN_ALT", data).toInt()
                    withData(arrayListOf("World Minimum Altitude")) {
                        minAlt.shouldBeBetween(500, 16500)
                        minAlt % 100 shouldBe 0
                    }
                    val maxAlt = getAllTextAfterHeader("MAX_ALT", data).toInt()
                    withData(arrayListOf("World Maximum Altitude")) {
                        maxAlt.shouldBeBetween(15000, 26000)
                        maxAlt shouldBeGreaterThan minAlt
                        maxAlt % 100 shouldBe 0
                    }
                    withData(arrayListOf("Intermediate Altitudes")) interAlt@ {
                        for ((index, value) in (" (.+)".toRegex().find(data)?.groupValues?.get(1)?.split(" ") ?: return@interAlt).withIndex()) {
                            if (index == 0) continue
                            val interAlt = value.trim().toInt()
                            interAlt.shouldBeBetween(minAlt, maxAlt)
                            interAlt % 100 shouldBe 0
                        }
                    }
                    val transAlt = "TRANS_ALT (.+)".toRegex().find(data).shouldNotBeNull().groupValues[1].toInt()
                    withData(arrayListOf("Transition Altitude")) {
                        transAlt.shouldBeBetween(minAlt, maxAlt)
                        transAlt % 100 shouldBe 0
                    }
                    val transLvl = "TRANS_LVL (.+)".toRegex().find(data).shouldNotBeNull().groupValues[1].toInt() * 100
                    withData(arrayListOf("Transition Level")) {
                        transLvl.shouldBeBetween(minAlt, maxAlt)
                        transLvl shouldBeGreaterThanOrEqual transAlt
                    }
                    withData(arrayListOf("Minimum Separation")) {
                        "MIN_SEP (.+)".toRegex().find(data).shouldNotBeNull().groupValues[1].toFloat()
                            .shouldBeBetween(1f, 10f, 0.0001f)
                    }
                    withData(arrayListOf("Magnetic Heading Deviation")) {
                        "MAG_HDG_DEV (.+)".toRegex().find(data).shouldNotBeNull().groupValues[1].toFloat()
                            .shouldBeBetween(-180f, 180f, 0.0001f)
                    }
                    withData(arrayListOf("Max Arrivals")) {
                        "MAX_ARRIVALS (.+)".toRegex().find(data).shouldNotBeNull().groupValues[1].toInt().shouldBeBetween(10, 40)
                    }

                    // 2. Check waypoints
                    val wpts = testWaypoints(data)

                    // 3. Check sectors, including that all sub-sectors are within the primary sector
                    testSectors(data)

                    // 4. Check min alt sectors
                    testMinAltSectors(data)

                    // 5. Check shoreline
                    testShoreline(data)

                    // 6. Check hold waypoints
                    testHolds(data, wpts)

                    // 7. Check airports
                    testAirports(data, wpts, minAlt)
                } catch (e: RuntimeException) {
                    e.stackTraceToString().shouldBeNull()
                }
            }
        }
    }

    /**
     * Parses the data to return the string that exists between the opening and closing of a tag;
     * e.g. using tag WAYPOINTS will search for text between WAYPOINTS and /WAYPOINTS, and return it with leading and
     * trailing whitespace trimmed
     * @param tag the tag to check for
     * @param data the text to check for text between the tag
     * @return the string between the tag, with leading and trailing whitespace trimmed; if no tag found in text, empty
     * string is returned
     */
    private fun getStringBetweenTags(tag: String, data: String): String {
        val split1 = data.split("$tag/\\s".toRegex(), limit = 2)
        if (split1.size < 2) return ""
        return split1[1].split("\\s/$tag".toRegex(), limit = 2)[0].trim()
    }

    /**
     * Parses the data to return the lines of text that exist between the opening and closing of a tag;
     * e.g. using tag WAYPOINTS will search for text between WAYPOINTS and /WAYPOINTS, and return non-blank lines
     * between them
     * @param tag the tag to check for
     * @param data the text to check for text between the tag
     * @return the lines of text between the tag, with blank lines removed
     */
    private fun getLinesBetweenTags(tag: String, data: String): List<String> {
        return getStringBetweenTags(tag, data).toLines().filter { it.isNotBlank() }
    }

    /**
     * Gets blocks between multiple tags with the same name
     * @param tag the tag to check for
     * @param data the text to check for text between the tag
     * @return a List of strings between each instance of the tag, with leading and trailing whitespace removed;
     * if no tag found in text, empty string is returned
     */
    private fun getBlocksBetweenTags(tag: String, data: String): List<String> {
        fun recursiveGetBlock(data: String, accumulator: ArrayList<String> = arrayListOf()): ArrayList<String> {
            val split1 = data.split("$tag/\\s".toRegex(), limit = 2)
            if (split1.size < 2) return accumulator
            val split2 = split1[1].split("\\s/$tag".toRegex(), limit = 2)
            accumulator.add(split2[0].trim())
            if (split2.size > 1) recursiveGetBlock(split2[1], accumulator)
            return accumulator
        }

        return recursiveGetBlock(data)
    }

    /**
     * Gets all text following a header with leading and trailing whitespace trimmed; e.g. if the header is SHIBA, then
     * for a text "SHIBA 123 43 1234" will return "123 43 1234"
     * @param header the header to search for
     * @param data the text to check for text after the header
     * @return the text following the header with leading and trailing whitespace trimmed
     */
    private fun getAllTextAfterHeader(header: String, data: String): String {
        return "$header (.+)".toRegex().find(data).shouldNotBeNull().groupValues[1].trim()
    }

    /**
     * Gets all text following a header with leading and trailing whitespace trimmed; this will return multiple strings
     * that match the header
     * @param header the header to search for
     * @param data the text to check for text after the header
     * @return the List of text following the header with leading and trailing whitespace trimmed
     */
    private fun getAllTextAfterHeaderMultiple(header: String, data: String): List<String> {
        val arrayList = ArrayList<String>()
        "^$header (.+)".toRegex(RegexOption.MULTILINE).findAll(data).forEach { arrayList.add(it.groupValues[1]) }
        return arrayList.map { it.trim() }
    }

    /**
     * Tests that the input string follows the format [Float],[Float]
     * @param coords the string to test for coordinate format
     */
    private fun testCoordsString(coords: String) {
        coords.split(",").let {
            it.size shouldBe 2
            it[0].toFloat()
            it[1].toFloat()
        }
    }

    /**
     * Tests the waypoint data validity for the input data, and returns the set of all waypoint IDs encountered, with
     * the calling ContainerScope as the scope for the tests
     * @param data the string text to parse
     * @return a HashSet of all the waypoint IDs
     */
    private suspend fun ContainerScope.testWaypoints(data: String): HashMap<String, Short> {
        val wptNames = HashMap<String, Short>()
        val wptIds = HashSet<Short>()
        withData(arrayListOf("Waypoints")) { withData(getLinesBetweenTags("WAYPOINTS", data)) { line ->
            val wptData = line.split(" ")
            val id = wptData[0].toShort()
            withClue("Duplicate waypoint ID $id") { if (wptIds.isNotEmpty()) wptIds shouldNotBeIn wptIds }
            wptIds.add(id)
            val wptName = wptData[1]
            withClue("Duplicate waypoint name $wptName") {
                if (wptNames.isNotEmpty()) wptName shouldNotBeIn wptNames.keys
            }
            wptNames[wptName] = id
            testCoordsString(wptData[2])
        }}

        return wptNames
    }

    /**
     * Tests the sector data validity for the input data, with the calling ContainerScope as the scope for the tests
     * @param data the string text to parse
     */
    private suspend fun ContainerScope.testSectors(data: String) {
        withData(arrayListOf("Sectors")) {
            val sectorData = getStringBetweenTags("SECTORS", data)
            sectorData.isBlank().shouldBeFalse()
            val primarySector = Polygon()
            var sectorIndex = 1
            while (true) {
                val sectorArrangements = getLinesBetweenTags(sectorIndex.toString(), sectorData)
                if (sectorArrangements.isEmpty()) break
                withData(sectorArrangements) { sector ->
                    val polygonVertices = ArrayList<Float>()
                    val indivSectorData = sector.split(" ")
                    indivSectorData.size shouldBeGreaterThanOrEqual 6
                    indivSectorData[0].toFloat()
                    for (i in 3 until indivSectorData.size) {
                        testCoordsString(indivSectorData[i])
                        val coordArray = indivSectorData[i].split(",")
                        coordArray.forEach { coord -> polygonVertices.add(coord.toFloat()) }
                    }
                    if (sectorIndex == 1) primarySector.vertices = polygonVertices.toFloatArray()
                    else {
                        var x: Float? = null
                        var y: Float? = null
                        for (point in polygonVertices) {
                            if (x == null) x = point
                            else if (y == null) y = point
                            if (y != null) {
                                var found = false
                                // Test +- 1px for both x, y axis due to imprecise Polygon.contains calculation
                                for (i in -1..1) {
                                    for (j in -1..1) {
                                        if (primarySector.contains(x + i, y + j)) found = true
                                    }
                                }
                                found.shouldBeTrue()
                            }
                        }
                    }
                }
                sectorIndex++
            }
        }
    }

    /**
     * Tests the minimum altitude sector data validity for the input data, with the calling ContainerScope as the scope
     * for the tests
     * @param data the string text to parse
     */
    private suspend fun ContainerScope.testMinAltSectors(data: String) {
        withData(arrayListOf("Minimum Altitude Sectors")) {
            withData(getLinesBetweenTags("MIN_ALT_SECTORS", data)) {
                val minAltSectorData = it.split(" ")
                minAltSectorData[0] shouldBeIn MVA_SECTOR_SHAPE
                minAltSectorData[1] shouldBeIn MVA_SECTOR_TYPE
                if (minAltSectorData[2] != "UNL") minAltSectorData[2].toInt()
                if (minAltSectorData[0] == "POLYGON") {
                    // For polygon sectors
                    minAltSectorData.size shouldBeGreaterThan 5
                    for (i in 3 until minAltSectorData.size) {
                        val coordData = minAltSectorData[i].split(",")
                        if (coordData[0] == "LABEL") {
                            coordData.size shouldBe 3
                            coordData[1].toFloat()
                            coordData[2].toFloat()
                            minAltSectorData.size shouldBeGreaterThan 6
                        } else {
                            coordData.size shouldBe 2
                            coordData[0].toFloat()
                            coordData[1].toFloat()
                        }
                    }
                } else if (minAltSectorData[1] == "CIRCLE") {
                    // For circle sectors
                    minAltSectorData.size shouldBe 5
                    testCoordsString(minAltSectorData[3])
                    minAltSectorData[4].toFloat()
                }
            }
        }
    }

    /**
     * Tests the shoreline data validity for the input data, with the calling ContainerScope as the scope for the tests
     * @param data the string text to parse
     */
    private suspend fun ContainerScope.testShoreline(data: String) {
        withData(arrayListOf("Shoreline")) {
            withData(getLinesBetweenTags("SHORELINE", data)) {
                val coords = it.split(" ")
                coords.size shouldBeGreaterThan 2
                coords.forEach { coord -> testCoordsString(coord) }
            }
        }
    }

    /**
     * Tests the hold waypoint data validity for the input data, with the calling ContainerScope as the scope for the
     * tests
     * @param data the string text to parse
     * @param wpts the map of waypoint names available to their IDs, and the hold waypoint name should be in this set
     */
    private suspend fun ContainerScope.testHolds(data: String, wpts: HashMap<String, Short>) {
        withData(arrayListOf("Hold Waypoints")) {
            withData(getLinesBetweenTags("HOLDS", data)) {
                val holdArray = it.split(" ")
                holdArray.size shouldBeGreaterThan 5
                holdArray[0] shouldBeIn wpts.keys
                holdArray[1].toInt().shouldBeBetween(1, 360)
                holdArray[2].toByte().shouldBeBetween(3, 12)
                holdArray[3].toShort().shouldBeBetween(150, 300)
                holdArray[4].toShort().shouldBeBetween(180, 320)
                holdArray[5] shouldBeIn DIRECTION
                if (holdArray.size == 7) (ABOVE_ALT_REGEX.find(holdArray[6]) ?: BELOW_ALT_REGEX.find(holdArray[6])).shouldNotBeNull()
            }
        }
    }

    /**
     * Tests the airport data validity for all airports, with the calling ContainerScope as the scope for the tests
     * @param data the string text to parse
     * @param wpts the map of waypoint names available to their IDs, and the hold waypoint name should be in this set
     * @param minAlt the minimum altitude of the game world
     */
    private suspend fun ContainerScope.testAirports(data: String, wpts: HashMap<String, Short>, minAlt: Int) {
        val arptIds = HashSet<Byte>()
        getBlocksBetweenTags("AIRPORT", data).forEach { airport ->
            val arptLines = airport.toLines(2)
            val header = arptLines[0].split(" ") // Get only the first 2 identifiers (ID, ICAO)
            header.size shouldBe 8
            withData(arrayListOf("Airport ${header[0]} ${header[1]}")) {
                val id = header[0].toByte()
                val icao = header[1]
                arptIds shouldNotContain id
                arptIds.add(id)
                withClue("ICAO code format invalid: $icao") { "^[A-Z]{4}\$".toRegex().find(icao).shouldNotBeNull() }
                header[3].toByte()
                header[4].toInt()
                testCoordsString(header[5])
                header[6].toShort()
                withClue("Real life weather ICAO code format invalid: ${header[7]}") { "^[A-Z]{4}\$".toRegex().find(header[7]).shouldNotBeNull() }
                testAirport(arptLines[1], wpts, minAlt)
            }
        }
    }

    /**
     * Tests the airport data validity for each airport data block, with the calling ContainerScope as the scope for the
     * tests
     * @param arptData the airport text to parse
     * @param wpts the map of waypoint names available to their IDs, and the hold waypoint name should be in this set
     * @param minAlt the minimum altitude of the game world
     */
    private suspend fun ContainerScope.testAirport(arptData: String, wpts: HashMap<String, Short>, minAlt: Int) {
        withData(arrayListOf("Wind Direction")) {
            getAllTextAfterHeader("WINDDIR", arptData).split(" ").map { it.toFloat() }.size shouldBe 37
        }
        withData(arrayListOf("Wind Speed")) {
            getAllTextAfterHeader("WINDSPD", arptData).split(" ").map { it.toFloat() }.size shouldBeGreaterThan 30
        }
        withData(arrayListOf("Visibility")) {
            getAllTextAfterHeader("VISIBILITY", arptData).split(" ").map { it.toFloat() }.size shouldBe 20
        }
        withData(arrayListOf("Ceiling")) {
            getAllTextAfterHeader("CEILING", arptData).split(" ").map { it.toFloat() }.size shouldBe 15
        }
        withData(arrayListOf("Windshear")) {
            getAllTextAfterHeader("WINDSHEAR", arptData).split(" ").map { it.toFloat() }.size shouldBe 2
        }
        val allRwys = testRunways(arptData)
        testDependentRunways("Dependent Parallel Runways", "DEPENDENT_PARALLEL", arptData, allRwys)
        testDependentRunways("Dependent Opposite Runways", "DEPENDENT_OPPOSITE", arptData, allRwys)
        testDependentRunways("Crossing Runways", "CROSSING", arptData, allRwys)
        testDepNOZ(arptData, allRwys)
        val configRwys = testRunwayConfigs(arptData, allRwys)
        testSid(arptData, allRwys, wpts, configRwys.first, minAlt)
        testStar(arptData, allRwys, wpts, configRwys.second)
        val allApproaches = testApp(arptData, allRwys, wpts, configRwys.second)
        testAppNOZ(arptData, allApproaches)
        testTraffic(arptData)
    }

    /**
     * Tests the runways for the input airport data block, with the calling ContainerScope as the scope for the
     * tests
     * @param data the string text to parse
     * @return a HashSet of all runway names for this airport
     */
    private suspend fun ContainerScope.testRunways(data: String): HashMap<String, Short> {
        val rwyAlts = HashMap<String, Short>()
        withData(arrayListOf("Runways")) {
            val rwyIds = HashSet<Byte>()
            withData(getLinesBetweenTags("RWYS", data)) {
                val rwyData = it.split(" ")
                rwyData.size shouldBe 11
                rwyIds shouldNotContain rwyData[0].toByte()
                val rwyName = rwyData[1]
                if (rwyName.last() !in RWY_POS) rwyName.toByte().shouldBeBetween(1, 36)
                else rwyName.substring(0, rwyName.length - 1).toByte().shouldBeBetween(1, 36)
                testCoordsString(rwyData[2])
                rwyData[3].toFloat().shouldBeBetween(0f, 360f, 0.0001f)
                val rwyLength = rwyData[4].toShort()
                rwyLength.shouldBeBetween(400, 7000)
                rwyData[5].toShort().shouldBeBetween(0, rwyLength)
                rwyData[6].toShort().shouldBeBetween(0, rwyLength)
                val thrElevation = rwyData[7].toShort()
                rwyAlts[rwyName] = thrElevation
                rwyData[8] shouldBeIn LABEL_POS
                rwyData[10].toFloat()
            }
        }
        return rwyAlts
    }

    /**
     * Tests the dependent runways for the input airport data block, with the calling ContainerScope as the scope for
     * the tests
     * @param data the string text to parse
     * @return a HashMap of all runway names for this airport mapped to their elevation
     */
    private suspend fun ContainerScope.testDependentRunways(testName: String, tag: String, data: String, allRwys: HashMap<String, Short>) {
        withData(arrayListOf(testName)) {
            withData(getLinesBetweenTags(tag, data)) {
                val rwys = it.split(" ")
                rwys.size shouldBe 2
                allRwys.keys shouldContain rwys[0]
                allRwys.keys shouldContain rwys[1]
            }
        }
    }

    /**
     * Tests the approach NOZs for the input airport [data] block, with the calling ContainerScope as the scope for the
     * tests
     * @param allApproaches name of all approaches in this airport
     */
    private suspend fun ContainerScope.testAppNOZ(data: String, allApproaches: HashSet<String>) {
        withData(arrayListOf("Approach NOZ")) {
            for (block in getBlocksBetweenTags("APP_NOZ", data)) {
                for (zone in getAllTextAfterHeaderMultiple("ZONE", block)) {
                    val zoneData = zone.split(" ")
                    zoneData.size shouldBeGreaterThanOrEqual 5
                    testCoordsString(zoneData[0])
                    zoneData[1].toShort().shouldBeBetween(1, 360)
                    zoneData[2].toFloat()
                    zoneData[3].toFloat()
                    for (i in 4 until zoneData.size) {
                        allApproaches shouldContain zoneData[i]
                    }
                }
            }
        }
    }

    /**
     * Tests the departure NOZs for the input airport [data] block, with the calling ContainerScope as the scope for the
     * tests
     * @param allRwys all runways in this airport mapped to their elevation
     */
    private suspend fun ContainerScope.testDepNOZ(data: String, allRwys: HashMap<String, Short>) {
        withData(arrayListOf("Departure NOZ")) {
            withData(getLinesBetweenTags("DEP_NOZ", data)) {
                val nozData = it.split(" ")
                nozData.size shouldBe 5
                allRwys.keys shouldContain nozData[0]
                testCoordsString(nozData[1])
                nozData[2].toShort().shouldBeBetween(1, 360)
                nozData[3].toFloat()
                nozData[4].toFloat()
            }
        }
    }

    /**
     * Tests the runway configs for the input airport data block, with the calling ContainerScope as the scope for the
     * tests, returning a Pair containing a HashMap each mapping the runway config ID to their respective departure,
     * arrival runways
     * @param data the string text to parse
     * @param allRwys all runways in this airport mapped to their elevation
     */
    private suspend fun ContainerScope.testRunwayConfigs(data: String, allRwys: HashMap<String, Short>): Pair<HashMap<Byte, HashSet<String>>, HashMap<Byte, HashSet<String>>> {
        val depRwys = HashMap<Byte, HashSet<String>>()
        val arrRwys = HashMap<Byte, HashSet<String>>()
        withData(arrayListOf("Runway Configurations")) {
            val configIds = HashSet<Byte>()
            getBlocksBetweenTags("CONFIG", data).forEach { config ->
                val header = config.toLines()[0].split(" ")
                header.size shouldBe 2
                withData(arrayListOf("Config ${header[0]} ${header[1]}")) {
                    val configId = header[0].toByte()
                    configIds shouldNotContain configId
                    configIds.add(configId)
                    val configDepRwys = HashSet<String>()
                    val configArrRwys = HashSet<String>()
                    header[1] shouldBeIn TIME_SLOTS
                    val name = getAllTextAfterHeader("NAME", config)
                    name.trim().length shouldBeGreaterThan 0
                    val dep = getAllTextAfterHeader("DEP", config)
                    withData(arrayListOf("DEP $dep")) {
                        dep.split(" ").forEach { rwy ->
                            allRwys.keys shouldContain rwy
                            configDepRwys.add(rwy)
                        }
                    }
                    val arr = getAllTextAfterHeader("ARR", config)
                    withData(arrayListOf("ARR $arr")) {
                        arr.split(" ").forEach { rwy ->
                            allRwys.keys shouldContain rwy
                            configArrRwys.add(rwy)
                        }
                    }
                    depRwys[configId] = configDepRwys
                    arrRwys[configId] = configArrRwys
                    withData(getAllTextAfterHeaderMultiple("NTZ", config).map { "NTZ $it" }) {ntz ->
                        val ntzData = ntz.split(" ")
                        ntzData.size shouldBe 5
                        testCoordsString(ntzData[1])
                        ntzData[2].toShort().shouldBeBetween(1, 360)
                        ntzData[3].toFloat()
                        ntzData[4].toFloat()
                    }
                }
            }
        }

        return Pair(depRwys, arrRwys)
    }

    /**
     * Tests the SIDs for the input airport data block, with the calling ContainerScope as the scope for the
     * tests
     * @param data the string text to parse
     * @param allRwys all runways in this airport mapped to their elevation
     * @param allWpts all waypoints in this game world
     * @param allConfigDepRwys a HashMap mapping the runway config ID to their respective departure runways
     * @param minAlt the game world's minimum altitude
     * @return a HashSet of all runway names for this airport
     */
    private suspend fun ContainerScope.testSid(data: String, allRwys: HashMap<String, Short>,
                                               allWpts: HashMap<String, Short>,
                                               allConfigDepRwys: HashMap<Byte, HashSet<String>>, minAlt: Int) {
        withData(arrayListOf("SIDs")) {
            getBlocksBetweenTags("SID", data).forEach {
                val sidLines = it.toLines(2)
                val infoLine = sidLines[0].split(" ")
                infoLine.size shouldBe 3
                withData(arrayListOf(infoLine[0])) {
                    infoLine[1] shouldBeIn TIME_SLOTS

                    val sidRwys = ArrayList<String>()
                    val rwyLines = getAllTextAfterHeaderMultiple("RWY", sidLines[1])
                    rwyLines.size shouldBeGreaterThanOrEqual 1
                    for (rwyStr in rwyLines) {
                        val rwyLine = rwyStr.split(" ")
                        rwyLine.size shouldBeGreaterThanOrEqual 2
                        rwyLine[0] shouldBeIn allRwys.keys
                        sidRwys.add(rwyLine[0])
                        rwyLine[1].toInt() shouldBeGreaterThanOrEqual minAlt
                        testParseLegs(rwyLine.subList(2, rwyLine.size), allWpts, Route.Leg.NORMAL, WARNING_SHOULD_BE_EMPTY)
                    }

                    val routeLines = getAllTextAfterHeaderMultiple("ROUTE", sidLines[1])
                    routeLines.size shouldBe 1
                    val routeLine = routeLines[0].split(" ")
                    testParseLegs(routeLine, allWpts, Route.Leg.NORMAL, WARNING_SHOULD_BE_EMPTY)

                    val outbounds = getAllTextAfterHeaderMultiple("OUTBOUND", sidLines[1])
                    for (outbound in outbounds) {
                        val outboundLine = outbound.split(" ")
                        testParseLegs(outboundLine, allWpts, Route.Leg.NORMAL, WARNING_SHOULD_BE_EMPTY)
                        testWaypointLegStartEnd(outboundLine, false)
                    }
                    if (outbounds.isEmpty()) testWaypointLegStartEnd(routeLine, false)

                    val allowedConfigs = getAllTextAfterHeader("ALLOWED_CONFIGS", sidLines[1])
                    val configArray = allowedConfigs.split(" ")
                    configArray.size shouldBeGreaterThan 0
                    withData(configArray) {configId ->
                        val id = configId.toByte()
                        val depRwys = allConfigDepRwys[id].shouldNotBeNull()
                        (sidRwys intersect depRwys).size shouldBeGreaterThan 0
                    }
                }
            }
        }
    }

    /**
     * Tests the STARs for the input airport data block, with the calling ContainerScope as the scope for the
     * tests
     * @param data the string text to parse
     * @param allRwys all runways in this airport mapped to their elevation
     * @param allWpts all waypoints in this game world
     * @param allConfigArrRwys a HashMap mapping the runway config ID to their respective arrival runways
     * @return a HashSet of all runway names for this airport
     */
    private suspend fun ContainerScope.testStar(data: String, allRwys: HashMap<String, Short>,
                                                allWpts: HashMap<String, Short>,
                                                allConfigArrRwys: HashMap<Byte, HashSet<String>>) {
        withData(arrayListOf("STARs")) {
            getBlocksBetweenTags("STAR", data).forEach {
                val starLines = it.toLines(2)
                val infoLine = starLines[0].split(" ")
                infoLine.size shouldBe 3
                withData(arrayListOf(infoLine[0])) {
                    infoLine[1] shouldBeIn TIME_SLOTS

                    val inbounds = getAllTextAfterHeaderMultiple("INBOUND", starLines[1])
                    for (inbound in inbounds) {
                        val inboundLine = inbound.split(" ")
                        testParseLegs(inboundLine, allWpts, Route.Leg.NORMAL, WARNING_SHOULD_BE_EMPTY)
                        testWaypointLegStartEnd(inboundLine, true)
                    }

                    val routeLines = getAllTextAfterHeaderMultiple("ROUTE", starLines[1])
                    routeLines.size shouldBe 1
                    val routeLine = routeLines[0].split(" ")
                    testParseLegs(routeLine, allWpts, Route.Leg.NORMAL, WARNING_SHOULD_BE_EMPTY)
                    if (inbounds.isEmpty()) testWaypointLegStartEnd(routeLine, true)

                    val starRwys = ArrayList<String>()
                    val rwyLines = getAllTextAfterHeaderMultiple("RWY", starLines[1])
                    rwyLines.size shouldBeGreaterThanOrEqual 1
                    for (rwy in rwyLines) {
                        val rwyLine = rwy.split(" ")
                        rwyLine.size shouldBe 1
                        rwyLine[0] shouldBeIn allRwys.keys
                        starRwys.add(rwyLine[0])
                    }

                    val allowedConfigs = getAllTextAfterHeader("ALLOWED_CONFIGS", starLines[1])
                    val configArray = allowedConfigs.split(" ")
                    configArray.size shouldBeGreaterThan 0
                    withData(configArray) {configId ->
                        val id = configId.toByte()
                        val arrRwys = allConfigArrRwys[id].shouldNotBeNull()
                        (starRwys intersect arrRwys).size shouldBeGreaterThan 0
                    }
                }
            }
        }
    }

    /**
     * Checks if the input route string ends/starts with a waypoint leg
     * @param routeLine The route string list to check
     * @param start Whether to check the start (true) or the end (false)
     */
    private fun testWaypointLegStartEnd(routeLine: List<String>, start: Boolean) {
        if (start) {
            val firstWypt = routeLine.indexOfFirst { it == WYPT_LEG }
            firstWypt shouldBe 0
        } else {
            val lastWypt = routeLine.lastIndexOf(WYPT_LEG)
            lastWypt shouldBeGreaterThan -1
            val lastHdng = routeLine.lastIndexOf(HDNG_LEG)
            lastHdng shouldBeLessThan lastWypt
            val lastHold = routeLine.lastIndexOf(HOLD_LEG)
            lastHold shouldBeLessThan lastWypt
            val lastInitClimb = routeLine.lastIndexOf(INIT_CLIMB_LEG)
            lastInitClimb shouldBeLessThan lastWypt
        }
    }

    /**
     * Tests the STARs for the input airport data block, with the calling ContainerScope as the scope for the
     * tests
     * @param data the string text to parse
     * @param allRwys all runways in this airport mapped to their elevation
     * @param allWpts all waypoints in this game world
     * @param allConfigArrRwys a HashMap mapping the runway config ID to their respective arrival runways
     * @return a HashSet of all approach names for this airport
     */
    private suspend fun ContainerScope.testApp(data: String, allRwys: HashMap<String, Short>,
                                               allWpts: HashMap<String, Short>, allConfigArrRwys: HashMap<Byte, HashSet<String>>): HashSet<String> {
        val appNames = HashSet<String>()
        withData(arrayListOf("Approaches")) {
            getBlocksBetweenTags("APCH", data).forEach {
                val apchLines = it.toLines(2)
                val infoLine = apchLines[0].split(" ")
                infoLine.size shouldBe 6
                appNames.add(infoLine[0])
                withData(arrayListOf(infoLine[0])) {
                    infoLine[1] shouldBeIn TIME_SLOTS
                    val rwyName = infoLine[2]
                    val alt = allRwys[rwyName]
                    val rwyAlt = alt.shouldNotBeNull()
                    testCoordsString(infoLine[3])
                    infoLine[4].toShort() shouldBeGreaterThanOrEqualTo rwyAlt
                    infoLine[5].toShort() shouldBeGreaterThanOrEqualTo 0

                    val locLines = getAllTextAfterHeaderMultiple("LOC", apchLines[1])
                    locLines.size shouldBeLessThanOrEqual 1
                    if (locLines.size == 1) {
                        val locData = locLines[0].split(" ")
                        locData.size shouldBe 2
                        locData[0].toShort()
                        locData[1].toByte()
                    }

                    val gsLines = getAllTextAfterHeaderMultiple("GS", apchLines[1])
                    gsLines.size shouldBeLessThanOrEqual 1
                    if (gsLines.isNotEmpty()) {
                        val gsData = gsLines[0].split(" ")
                        gsData.size shouldBe 3
                        gsData[0].toFloat()
                        gsData[1].toFloat()
                        gsData[2].toShort()
                    }

                    val stepDownLines = getAllTextAfterHeaderMultiple("STEPDOWN", apchLines[1])
                    stepDownLines.size shouldBeLessThanOrEqual 1
                    if (stepDownLines.isNotEmpty()) {
                        val stepDownData = stepDownLines[0].split(" ")
                        stepDownData.size shouldBeGreaterThanOrEqual 1
                        for (step in stepDownData) {
                            val stepData = step.split("@")
                            stepData.size shouldBe 2
                            stepData[0].toShort()
                            stepData[1].toFloat()
                        }
                    }

                    gsLines.size + stepDownLines.size shouldBeLessThanOrEqual 1

                    val appLineUpLines = getAllTextAfterHeaderMultiple("LINEUP", apchLines[1])
                    appLineUpLines.size shouldBeLessThanOrEqual 1
                    if (appLineUpLines.isNotEmpty()) {
                        val lineUpData = appLineUpLines[0].split(" ")
                        lineUpData.size shouldBe 1
                        lineUpData[0].toFloat()
                    }

                    val circleLines = getAllTextAfterHeaderMultiple("CIRCLING", apchLines[1])
                    circleLines.size shouldBeLessThanOrEqual 1
                    if (circleLines.isNotEmpty()) {
                        val circleData = circleLines[0].split(" ")
                        circleData.size shouldBe 3
                        circleData[0].toInt() shouldBeGreaterThanOrEqual rwyAlt + 500
                        circleData[1].toInt() shouldBeGreaterThanOrEqual circleData[0].toInt() + 250
                        circleData[2] shouldBeIn DIRECTION
                    }

                    val transitions = getAllTextAfterHeaderMultiple("TRANSITION", apchLines[1])
                    transitions.size shouldBeGreaterThanOrEqual 1
                    var vectorTransPresent = false
                    for (trans in transitions) {
                        val inboundLine = trans.split(" ")
                        inboundLine.size shouldBeGreaterThanOrEqual 1
                        if (inboundLine[0] != "vectors") {
                            // First leg should be waypoint with the same name as transition
                            inboundLine[0] shouldBeIn allWpts.keys
                            inboundLine[1] shouldBe WYPT_LEG
                            inboundLine[2] shouldBe inboundLine[0]
                        }
                        else vectorTransPresent = true
                        testParseLegs(inboundLine.subList(1, inboundLine.size), allWpts, Route.Leg.NORMAL, WARNING_SHOULD_BE_EMPTY)
                    }
                    vectorTransPresent.shouldBeTrue()

                    val routeLines = getAllTextAfterHeaderMultiple("ROUTE", apchLines[1])
                    routeLines.size shouldBeLessThanOrEqual 1
                    if (routeLines.size == 1) {
                        val routeLine = routeLines[0].split(" ")
                        testParseLegs(routeLine, allWpts, Route.Leg.NORMAL, WARNING_SHOULD_BE_EMPTY)
                    }

                    val missedLines = getAllTextAfterHeaderMultiple("MISSED", apchLines[1])
                    missedLines.size shouldBe 1
                    val missedLine = missedLines[0].split(" ")
                    val route = testParseLegs(missedLine, allWpts, Route.Leg.MISSED_APP, WARNING_SHOULD_BE_EMPTY)
                    route.size shouldBeGreaterThanOrEqual 1
                    if (route.size > 1 || route[0] !is Route.VectorLeg) {
                        findMissedApproachAlt(route).shouldNotBeNull()
                    }

                    val allowedConfigs = getAllTextAfterHeader("ALLOWED_CONFIGS", apchLines[1])
                    val configArray = allowedConfigs.split(" ")
                    configArray.size shouldBeGreaterThan 0
                    withData(configArray) {configId ->
                        val id = configId.toByte()
                        val arrRwys = allConfigArrRwys[id].shouldNotBeNull()
                        arrRwys shouldContain rwyName
                    }
                }
            }
        }

        return appNames
    }

    /**
     * Tests traffic data for the input airport data block, with the calling ContainerScope as the scope for the tests
     * @param data the string text to parse
     */
    private suspend fun ContainerScope.testTraffic(data: String) {
        withData(arrayListOf("Traffic")) {
            getBlocksBetweenTags("TRAFFIC", data).forEach {
                val tfcLines = it.toLines()
                for (line in tfcLines) {
                    val tfcData = line.split(" ")
                    if (tfcData[0] == "PRIVATE") {
                        tfcData.size shouldBe 4
                        withData(arrayListOf(tfcData[1])) {
                            tfcData[2].toFloat()
                            tfcData[3] shouldBeIn aircraftSet
                        }
                    } else {
                        tfcData.size shouldBeGreaterThanOrEqual 3
                        withData(arrayListOf(tfcData[0])) {
                            tfcData[0].length shouldBe 3
                            tfcData[1].toFloat()
                            for (acType in tfcData.subList(2, tfcData.size)) {
                                acType shouldBeIn aircraftSet
                            }
                        }
                    }
                }
            }
        }
    }
}