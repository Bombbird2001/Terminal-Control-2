import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.math.CumulativeDistribution
import com.bombbird.terminalcontrol2.components.*
import com.bombbird.terminalcontrol2.entities.Airport
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.json.getMoshiWithAllAdapters
import com.bombbird.terminalcontrol2.json.runDelayedEntityRetrieval
import com.bombbird.terminalcontrol2.navigation.Approach
import com.bombbird.terminalcontrol2.utilities.UsabilityFilter
import com.squareup.moshi.adapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import ktx.ashley.get
import ktx.collections.set

/** Kotest FunSpec class for testing JSON serialization for components and entities */
@OptIn(ExperimentalStdlibApi::class)
object JsonTest: FunSpec() {
    private var arpt: Airport? = null

    private var rwy1: Airport.Runway? = null
    private var rwy2: Airport.Runway? = null

    private var app1: Approach? = null
    private var app2: Approach? = null
    private var app3: Approach? = null

    private val testMoshi = getMoshiWithAllAdapters()

    init {
        beforeTest {
            testInitialiseGameAndServer()
            arpt = Airport(0, "TEST", "Test Airport", 1, 1f, 1f, 20, false)
            arpt?.addRunway(0, "05L", -1f, 1f, 45f, 4000, 200, 0,
                20, "Test Tower", "118.6", RunwayLabel.LEFT)
            arpt?.setRunwayMapping("05L", 0)
            arpt?.addRunway(1, "05R", 1f, -1f, 45f, 3800, 0, 0,
                25, "Test Tower", "118.6", RunwayLabel.RIGHT)
            arpt?.setRunwayMapping("05R", 1)
            rwy1 = arpt?.getRunway("05L")
            rwy2 = arpt?.getRunway("05R")
            arpt?.assignOppositeRunways()
            GAME.gameServer?.apply {
                airports.clear()
                arpt?.let { airports[0] = it }
            }

            app1 = Approach("ILS05L", 0, 0, 9f, 11f, 220, 800, false, UsabilityFilter.DAY_AND_NIGHT).apply {
                addLocalizer(45, 20)
                addGlideslope(3f, -2f, 4000)
            }.shouldNotBeNull()
            app2 = Approach("LDA05R", 0, 1, 7f, 11f, 625, 2700, false, UsabilityFilter.DAY_AND_NIGHT).apply {
                addLocalizer(30, 20)
                addStepDown(arrayOf(StepDown.Step(4f, 1200), StepDown.Step(8f, 2000), StepDown.Step(15f, 4000)))
                addLineUpDist(1f)
            }.shouldNotBeNull()
            app3 = Approach("CIRCLE05L", 0, 0, -1f, 1f, 1220, 4000, false, UsabilityFilter.DAY_AND_NIGHT).apply {
                addLocalizer(225, 20)
                addGlideslope(3f, -2f, 4000)
                addCircling(1000, 1400, CommandTarget.TURN_RIGHT)
            }.shouldNotBeNull()
            arpt?.entity?.get(ApproachChildren.mapper)?.apply {
                app1?.let { approachMap["ILS05L"] = it }.shouldNotBeNull()
                app2?.let { approachMap["LDA05R"] = it }.shouldNotBeNull()
                app3?.let { approachMap["CIRCLE05L"] = it }.shouldNotBeNull()
            }.shouldNotBeNull()
        }

        test("TakeoffRoll serialization") {
            val takeoffRollAdapter = testMoshi.adapter<TakeoffRoll>()
            val takeoffRoll1 = TakeoffRoll(2f, rwy1?.entity ?: Entity())
            val takeoffRoll2 = TakeoffRoll(2.5f, rwy2?.entity ?: Entity())
            takeoffRollAdapter.toJson(takeoffRoll1) shouldBe """{"targetAccMps2":2.0,"rwy":{"arptId":0,"rwyId":0}}"""
            takeoffRollAdapter.toJson(takeoffRoll2) shouldBe """{"targetAccMps2":2.5,"rwy":{"arptId":0,"rwyId":1}}"""
            val takeoffRoll1FromJson = takeoffRollAdapter.fromJson(takeoffRollAdapter.toJson(takeoffRoll1))
            val takeoffRoll2FromJson = takeoffRollAdapter.fromJson(takeoffRollAdapter.toJson(takeoffRoll2))
            runDelayedEntityRetrieval()
            takeoffRoll1FromJson shouldBe takeoffRoll1
            takeoffRoll2FromJson shouldBe takeoffRoll2
        }

        test("LandingRoll serialization") {
            val landingRollAdapter = testMoshi.adapter<LandingRoll>()
            val landingRoll1 = LandingRoll(rwy1?.entity ?: Entity())
            val landingRoll2 = LandingRoll(rwy2?.entity ?: Entity())
            landingRollAdapter.toJson(landingRoll1) shouldBe """{"rwy":{"arptId":0,"rwyId":0}}"""
            landingRollAdapter.toJson(landingRoll2) shouldBe """{"rwy":{"arptId":0,"rwyId":1}}"""
            val landingRoll1FromJson = landingRollAdapter.fromJson(landingRollAdapter.toJson(landingRoll1))
            val landingRoll2FromJson = landingRollAdapter.fromJson(landingRollAdapter.toJson(landingRoll2))
            runDelayedEntityRetrieval()
            landingRoll1FromJson shouldBe landingRoll1
            landingRoll2FromJson shouldBe landingRoll2
        }

        test("CommandTarget serialization") {
            val cmdTargetAdapter = testMoshi.adapter<CommandTarget>()
            val cmdTarget1 = CommandTarget(45f, CommandTarget.TURN_DEFAULT, 3000, 240)
            val cmdTarget2 = CommandTarget(180f, CommandTarget.TURN_LEFT, 5000, 220)
            val cmdTarget3 = CommandTarget(360f, CommandTarget.TURN_RIGHT, 10000, 260)
            cmdTargetAdapter.fromJson(cmdTargetAdapter.toJson(cmdTarget1)) shouldBe cmdTarget1
            cmdTargetAdapter.fromJson(cmdTargetAdapter.toJson(cmdTarget2)) shouldBe cmdTarget2
            cmdTargetAdapter.fromJson(cmdTargetAdapter.toJson(cmdTarget3)) shouldBe cmdTarget3
        }

        test("CommandVector serialization") {
            val cmdVectorAdapter = testMoshi.adapter<CommandVector>()
            val cmdVector1 = CommandVector(45, CommandTarget.TURN_DEFAULT)
            val cmdVector2 = CommandVector(180, CommandTarget.TURN_LEFT)
            val cmdVector3 = CommandVector(360, CommandTarget.TURN_RIGHT)
            cmdVectorAdapter.fromJson(cmdVectorAdapter.toJson(cmdVector1)) shouldBe cmdVector1
            cmdVectorAdapter.fromJson(cmdVectorAdapter.toJson(cmdVector2)) shouldBe cmdVector2
            cmdVectorAdapter.fromJson(cmdVectorAdapter.toJson(cmdVector3)) shouldBe cmdVector3
        }

        test("CommandInitClimb serialization") {
            val cmdInitClimbAdapter = testMoshi.adapter<CommandInitClimb>()
            val cmdInitClimb1 = CommandInitClimb(45, 600)
            val cmdInitClimb2 = CommandInitClimb(180, 1000)
            val cmdInitClimb3 = CommandInitClimb(360, 4000)
            cmdInitClimbAdapter.fromJson(cmdInitClimbAdapter.toJson(cmdInitClimb1)) shouldBe cmdInitClimb1
            cmdInitClimbAdapter.fromJson(cmdInitClimbAdapter.toJson(cmdInitClimb2)) shouldBe cmdInitClimb2
            cmdInitClimbAdapter.fromJson(cmdInitClimbAdapter.toJson(cmdInitClimb3)) shouldBe cmdInitClimb3
        }

        test("CommandDirect serialization") {
            val cmdDirectAdapter = testMoshi.adapter<CommandDirect>()
            val cmdDirect1 = CommandDirect(0, null, 2000, null, false, CommandTarget.TURN_DEFAULT)
            val cmdDirect2 = CommandDirect(1, 5000, null, 230, false, CommandTarget.TURN_LEFT)
            val cmdDirect3 = CommandDirect(3, null, null, null, true, CommandTarget.TURN_RIGHT)
            cmdDirectAdapter.fromJson(cmdDirectAdapter.toJson(cmdDirect1)) shouldBe cmdDirect1
            cmdDirectAdapter.fromJson(cmdDirectAdapter.toJson(cmdDirect2)) shouldBe cmdDirect2
            cmdDirectAdapter.fromJson(cmdDirectAdapter.toJson(cmdDirect3)) shouldBe cmdDirect3
        }

        test("CommandHold serialization") {
            val cmdHoldAdapter = testMoshi.adapter<CommandHold>()
            val cmdHold1 = CommandHold(0, null, 5000, 230, 45, 5, CommandTarget.TURN_RIGHT,
                0, entryDone = false, oppositeTravelled = false, flyOutbound = false)
            val cmdHold2 = CommandHold(1, 16000, 4000, 240, 180, 4, CommandTarget.TURN_LEFT,
                1, entryDone = true, oppositeTravelled = false, flyOutbound = true)
            val cmdHold3 = CommandHold(3, null, null, 230, 360, 6, CommandTarget.TURN_LEFT,
                2, entryDone = true, oppositeTravelled = true, flyOutbound = false)
            cmdHoldAdapter.fromJson(cmdHoldAdapter.toJson(cmdHold1)) shouldBe cmdHold1
            cmdHoldAdapter.fromJson(cmdHoldAdapter.toJson(cmdHold2)) shouldBe cmdHold2
            cmdHoldAdapter.fromJson(cmdHoldAdapter.toJson(cmdHold3)) shouldBe cmdHold3
        }

        test("LastRestrictions serialization") {
            val lastRestrAdapter = testMoshi.adapter<LastRestrictions>()
            val lastRestr1 = LastRestrictions(null, null, null)
            val lastRestr2 = LastRestrictions(5000, null, 230)
            val lastRestr3 = LastRestrictions(null, 20000, 280)
            lastRestrAdapter.fromJson(lastRestrAdapter.toJson(lastRestr1)) shouldBe lastRestr1
            lastRestrAdapter.fromJson(lastRestrAdapter.toJson(lastRestr2)) shouldBe lastRestr2
            lastRestrAdapter.fromJson(lastRestrAdapter.toJson(lastRestr3)) shouldBe lastRestr3
        }

        test("VisualCaptured serialization") {
            val visCapAdapter = testMoshi.adapter<VisualCaptured>()
            val visCap1 = VisualCaptured(rwy1?.entity?.get(VisualApproach.mapper)?.visual ?: Entity(), app1?.entity ?: Entity())
            val visCap2 = VisualCaptured(rwy2?.entity?.get(VisualApproach.mapper)?.visual ?: Entity(), app2?.entity ?: Entity())
            val visCap1FromJson = visCapAdapter.fromJson(visCapAdapter.toJson(visCap1))
            val visCap2FromJson = visCapAdapter.fromJson(visCapAdapter.toJson(visCap2))
            runDelayedEntityRetrieval()
            visCap1FromJson shouldBe visCap1
            visCap2FromJson shouldBe visCap2
        }

        test("LocalizerArmed serialization") {
            val locArmedAdapter = testMoshi.adapter<LocalizerArmed>()
            val locArmed1 = LocalizerArmed(app1?.entity ?: Entity())
            val locArmed2 = LocalizerArmed(app2?.entity ?: Entity())
            val locArmed1FromJson = locArmedAdapter.fromJson(locArmedAdapter.toJson(locArmed1))
            val locArmed2FromJson = locArmedAdapter.fromJson(locArmedAdapter.toJson(locArmed2))
            runDelayedEntityRetrieval()
            locArmed1FromJson shouldBe locArmed1
            locArmed2FromJson shouldBe locArmed2
        }

        test("LocalizerCaptured serialization") {
            val locCapAdapter = testMoshi.adapter<LocalizerCaptured>()
            val locCap1 = LocalizerCaptured(app1?.entity ?: Entity())
            val locCap2 = LocalizerCaptured(app2?.entity ?: Entity())
            val locCap1FromJson = locCapAdapter.fromJson(locCapAdapter.toJson(locCap1))
            val locCap2FromJson = locCapAdapter.fromJson(locCapAdapter.toJson(locCap2))
            runDelayedEntityRetrieval()
            locCap1FromJson shouldBe locCap1
            locCap2FromJson shouldBe locCap2
        }

        test("GlideSlopeArmed serialization") {
            val gsArmedAdapter = testMoshi.adapter<GlideSlopeArmed>()
            val gsArmed1 = GlideSlopeArmed(app1?.entity ?: Entity())
            val gsArmed2 = GlideSlopeArmed(app2?.entity ?: Entity())
            val gsArmed1FromJson = gsArmedAdapter.fromJson(gsArmedAdapter.toJson(gsArmed1))
            val gsArmed2FromJson = gsArmedAdapter.fromJson(gsArmedAdapter.toJson(gsArmed2))
            runDelayedEntityRetrieval()
            gsArmed1FromJson shouldBe gsArmed1
            gsArmed2FromJson shouldBe gsArmed2
        }

        test("GlideSlopeCaptured serialization") {
            val gsCapAdapter = testMoshi.adapter<GlideSlopeCaptured>()
            val gsCap1 = GlideSlopeCaptured(app1?.entity ?: Entity())
            val gsCap2 = GlideSlopeCaptured(app2?.entity ?: Entity())
            val gsCap1FromJson = gsCapAdapter.fromJson(gsCapAdapter.toJson(gsCap1))
            val gsCap2FromJson = gsCapAdapter.fromJson(gsCapAdapter.toJson(gsCap2))
            runDelayedEntityRetrieval()
            gsCap1FromJson shouldBe gsCap1
            gsCap2FromJson shouldBe gsCap2
        }

        test("StepDownApproach serialization"){
            val stepDownAdapter = testMoshi.adapter<StepDownApproach>()
            val stepDown = StepDownApproach(app2?.entity ?: Entity())
            val stepDownFromJson = stepDownAdapter.fromJson(stepDownAdapter.toJson(stepDown))
            runDelayedEntityRetrieval()
            stepDownFromJson shouldBe stepDown
        }

        test("CirclingApproach serialization") {
            val circlingAdapter = testMoshi.adapter<CirclingApproach>()
            val circling1 = CirclingApproach(app3?.entity ?: Entity(), 1200, 0)
            val circling2 = CirclingApproach(app3?.entity ?: Entity(), 1100, 1, 34.5f)
            val circling3 = CirclingApproach(app3?.entity ?: Entity(), 1150, 2, 0f)
            val circling4 = CirclingApproach(app3?.entity ?: Entity(), 1250, 3, 0f, 21f)
            val circling1FromJson = circlingAdapter.fromJson(circlingAdapter.toJson(circling1))
            val circling2FromJson = circlingAdapter.fromJson(circlingAdapter.toJson(circling2))
            val circling3FromJson = circlingAdapter.fromJson(circlingAdapter.toJson(circling3))
            val circling4FromJson = circlingAdapter.fromJson(circlingAdapter.toJson(circling4))
            runDelayedEntityRetrieval()
            circling1FromJson shouldBe circling1
            circling2FromJson shouldBe circling2
            circling3FromJson shouldBe circling3
            circling4FromJson shouldBe circling4
        }

        test("ApproachInfo serialization") {
            val appInfoAdapter = testMoshi.adapter<ApproachInfo>()
            val appInfo1 = ApproachInfo("ILS05L", 0, 0)
            val appInfo2 = ApproachInfo("LDA05R", 0, 1)
            val appInfo3 = ApproachInfo("CIRCLE05L", 0, 0)
            appInfoAdapter.fromJson(appInfoAdapter.toJson(appInfo1)) shouldBe appInfo1
            appInfoAdapter.fromJson(appInfoAdapter.toJson(appInfo2)) shouldBe appInfo2
            appInfoAdapter.fromJson(appInfoAdapter.toJson(appInfo3)) shouldBe appInfo3
        }

        test("Localizer serialization") {
            val locAdapter = testMoshi.adapter<Localizer>()
            val loc1 = Localizer(20)
            val loc2 = Localizer(25)
            val loc3 = Localizer(15)
            locAdapter.fromJson(locAdapter.toJson(loc1)) shouldBe loc1
            locAdapter.fromJson(locAdapter.toJson(loc2)) shouldBe loc2
            locAdapter.fromJson(locAdapter.toJson(loc3)) shouldBe loc3
        }

        test("LineUpDist serialization") {
            val lineUpDistAdapter = testMoshi.adapter<LineUpDist>()
            val lineUpDist1 = LineUpDist(0.3f)
            val lineUpDist2 = LineUpDist(0.65f)
            val lineUpDist3 = LineUpDist(1.35f)
            lineUpDistAdapter.fromJson(lineUpDistAdapter.toJson(lineUpDist1)) shouldBe lineUpDist1
            lineUpDistAdapter.fromJson(lineUpDistAdapter.toJson(lineUpDist2)) shouldBe lineUpDist2
            lineUpDistAdapter.fromJson(lineUpDistAdapter.toJson(lineUpDist3)) shouldBe lineUpDist3
        }

        test("GlideSlope serialization") {
            val gsAdapter = testMoshi.adapter<GlideSlope>()
            val gs1 = GlideSlope(3f, -1.4f, 4000)
            val gs2 = GlideSlope(3f, -2f, 5000)
            val gs3 = GlideSlope(4.5f, -1f, 6000)
            gsAdapter.fromJson(gsAdapter.toJson(gs1)) shouldBe gs1
            gsAdapter.fromJson(gsAdapter.toJson(gs2)) shouldBe gs2
            gsAdapter.fromJson(gsAdapter.toJson(gs3)) shouldBe gs3
        }

        test("StepDown serialization") {
            val stepDownAdapter = testMoshi.adapter<StepDown>()
            val stepDown1 = StepDown(arrayOf(StepDown.Step(4f, 1200), StepDown.Step(8f, 2000), StepDown.Step(15f, 4000)))
            val stepDown2 = StepDown(arrayOf(StepDown.Step(5f, 1200), StepDown.Step(8.4f, 2000), StepDown.Step(14f, 4000)))
            val stepDown3 = StepDown(arrayOf(StepDown.Step(3.3f, 800), StepDown.Step(7f, 2100), StepDown.Step(10f, 3000)))
            stepDownAdapter.fromJson(stepDownAdapter.toJson(stepDown1)) shouldBe stepDown1
            stepDownAdapter.fromJson(stepDownAdapter.toJson(stepDown2)) shouldBe stepDown2
            stepDownAdapter.fromJson(stepDownAdapter.toJson(stepDown3)) shouldBe stepDown3
        }

        test("Circling serialization") {
            val circlingAdapter = testMoshi.adapter<Circling>()
            val circling1 = Circling(1000, 1400, CommandTarget.TURN_LEFT)
            val circling2 = Circling(1200, 1400, CommandTarget.TURN_RIGHT)
            val circling3 = Circling(900, 1300, CommandTarget.TURN_LEFT)
            circlingAdapter.fromJson(circlingAdapter.toJson(circling1)) shouldBe circling1
            circlingAdapter.fromJson(circlingAdapter.toJson(circling2)) shouldBe circling2
            circlingAdapter.fromJson(circlingAdapter.toJson(circling3)) shouldBe circling3
        }

        test("Minimums serialization") {
            val minsAdapter = testMoshi.adapter<Minimums>()
            val mins1 = Minimums(220, 800)
            val mins2 = Minimums(620, 2700)
            val mins3 = Minimums(1220, 4000)
            minsAdapter.fromJson(minsAdapter.toJson(mins1)) shouldBe mins1
            minsAdapter.fromJson(minsAdapter.toJson(mins2)) shouldBe mins2
            minsAdapter.fromJson(minsAdapter.toJson(mins3)) shouldBe mins3
        }

        test("AirportInfo serialization") {
            val arptInfoAdapter = testMoshi.adapter<AirportInfo>()
            val arptInfo1 = AirportInfo(0, "TESA", "Test 1", 1)
            val arptInfo2 = AirportInfo(1, "TESB", "Test 2", 3)
            arptInfoAdapter.fromJson(arptInfoAdapter.toJson(arptInfo1)) shouldBe arptInfo1
            arptInfoAdapter.fromJson(arptInfoAdapter.toJson(arptInfo2)) shouldBe arptInfo2
        }

        test("RunwayInfo serialization") {
            val rwyInfoAdapter = testMoshi.adapter<RunwayInfo>()
            val rwyInfo1 = rwy1?.entity?.get(RunwayInfo.mapper)?.shouldNotBeNull()
            val rwyInfo2 = rwy2?.entity?.get(RunwayInfo.mapper)?.shouldNotBeNull()
            val rwyInfo1FromJson = rwyInfoAdapter.fromJson(rwyInfoAdapter.toJson(rwyInfo1))
            val rwyInfo2FromJson = rwyInfoAdapter.fromJson(rwyInfoAdapter.toJson(rwyInfo2))
            runDelayedEntityRetrieval()
            rwyInfo1FromJson shouldBe rwyInfo1
            rwyInfo2FromJson shouldBe rwyInfo2
        }

        test("RandomMetarInfo serialization") {
            val randomMetarAdapter = testMoshi.adapter<RandomMetarInfo>()
            val cumDist1a = CumulativeDistribution<Short>().apply { for (i in 0..36) add(i.toShort(), i + 1f) }
            val cumDist1b = CumulativeDistribution<Short>().apply { for (i in 0..50) add(i.toShort(), 51f - i) }
            val cumDist1c = CumulativeDistribution<Short>().apply { for (i in 0 until 20) add(i.toShort(), i + 1f) }
            val cumDist1d = CumulativeDistribution<Short>().apply { for (i in 0 until 15) add(i.toShort(), i + 1f) }
            val randomMetar = RandomMetarInfo(cumDist1a, cumDist1b, cumDist1c, cumDist1d)
            val randomMetarFromJson = randomMetarAdapter.fromJson(randomMetarAdapter.toJson(randomMetar)).shouldNotBeNull()
            randomMetarFromJson.windDirDist should matchCumDist(cumDist1a)
            randomMetarFromJson.windSpdDist should matchCumDist(cumDist1b)
            randomMetarFromJson.visibilityDist should matchCumDist(cumDist1c)
            randomMetarFromJson.ceilingDist should matchCumDist(cumDist1d)
        }
    }

    /**
     * Matcher for checking contents of a cumulative distribution are equal
     * @param otherDist the other distribution to check for equality with; the existing distribution to check if accessed
     * from [Matcher]
     * */
    private fun <T> matchCumDist(otherDist: CumulativeDistribution<T>) = Matcher<CumulativeDistribution<T>> {
        var discrepancyFound = true
        if (otherDist.size() == it.size()) {
            discrepancyFound = false
            for (i in 0 until otherDist.size())
                if (otherDist.getValue(i) != it.getValue(i) || otherDist.getInterval(i) != it.getInterval(i)) {
                    discrepancyFound = true
                    break
                }
        }
        return@Matcher MatcherResult(!discrepancyFound, {
            if (otherDist.size() != it.size()) "Cumulative distributions did not match in size"
            else "Cumulative distribution values and/or intervals did not match exactly (including order)"
        }, { "Cumulative distributions matched, but should not have matched" })
    }
}