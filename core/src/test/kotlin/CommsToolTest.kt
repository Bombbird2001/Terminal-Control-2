import com.bombbird.terminalcontrol2.global.TRANS_LVL
import com.bombbird.terminalcontrol2.utilities.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

object CommsToolTest: FunSpec() {
    init {
        test("Literal token") {
            val sentence = TokenSentence()
            sentence.addToken(LiteralToken("Hello world"))
            sentence.addComma()
            sentence.addToken(LiteralToken("How are you doing today?"))
            sentence.toTextSentence() shouldBe "Hello world, How are you doing today?"
            sentence.toTTSSentence() shouldBe "hello world, how are you doing today?"
        }

        test("Callsign token - Not present in map") {
            val sentence = TokenSentence()
            sentence.addToken(CallsignToken("SIA123"))
            sentence.addComma()
            sentence.addToken(CallsignToken("EVA456"))
            sentence.addComma()
            sentence.addToken(CallsignToken("JAL789"))
            sentence.toTextSentence() shouldBe "SIA123, EVA456, JAL789"
            sentence.toTTSSentence() shouldBe "sierra india alpha one two tree, " +
                    "echo victor alpha four five six, juliet alpha lima seven eight niner"
        }

        test("Pronounceable token") {
            val sentence = TokenSentence()
            sentence.addToken(PronounceableToken("CHALI1C", object : Pronounceable {
                override val pronunciation: String
                    get() = "CHALI 1 CHARLIE"
            }))
            sentence.addComma()
            sentence.addToken(PronounceableToken("NTN1A", object : Pronounceable {
                override val pronunciation: String
                    get() = "KUNG SEE 1 ALPHA"
            }))
            sentence.addComma()
            sentence.addToken(PronounceableToken("MVR8F", object : Pronounceable {
                override val pronunciation: String
                    get() = "SERMING 8 FOXTROT"
            }))
            sentence.toTextSentence() shouldBe "CHALI1C, NTN1A, MVR8F"
            sentence.toTTSSentence() shouldBe "CHALI 1 CHARLIE, KUNG SEE 1 ALPHA, SERMING 8 FOXTROT".lowercase()
        }

        test("Waypoint token") {
            val sentence = TokenSentence()
            sentence.addToken(WaypointToken("CHALI"))
            sentence.addComma()
            sentence.addToken(WaypointToken("NTN"))
            sentence.addComma()
            sentence.addToken(WaypointToken("MVR"))
            sentence.toTextSentence() shouldBe "CHALI, NTN, MVR"
            sentence.toTTSSentence() shouldBe "CHALI, November Tango November, Mike Victor Romeo".lowercase()
        }

        test("Heading token") {
            val sentence = TokenSentence()
            sentence.addToken(HeadingToken(120))
            sentence.addComma()
            sentence.addToken(HeadingToken(45))
            sentence.addComma()
            sentence.addToken(HeadingToken(5))
            sentence.toTextSentence() shouldBe "120, 045, 005"
            sentence.toTTSSentence() shouldBe "One Two Zero, Zero Four Five, Zero Zero Five".lowercase()
        }

        test("Frequency token") {
            val sentence = TokenSentence()
            sentence.addToken(FrequencyToken("118.6"))
            sentence.addComma()
            sentence.addToken(FrequencyToken("124.3"))
            sentence.addComma()
            sentence.addToken(FrequencyToken("121.72"))
            sentence.toTextSentence() shouldBe "118.6, 124.3, 121.72"
            sentence.toTTSSentence() shouldBe "One One Eight Decimal Six, One Two Four Decimal Tree, One Two One Decimal Seven Two".lowercase()
        }

        test("Altitude token - below transition level") {
            TRANS_LVL = 130
            val sentence = TokenSentence()
            sentence.addToken(AltitudeToken(2000f))
            sentence.addComma()
            sentence.addToken(AltitudeToken(6534.21f))
            sentence.addComma()
            sentence.addToken(AltitudeToken(10456.56f))
            sentence.toTextSentence() shouldBe "2000 feet, 6500 feet, 10500 feet"
            sentence.toTTSSentence() shouldBe "2000 feet, 6500 feet, 10500 feet".lowercase()
        }

        test("Altitude token - above transition level") {
            TRANS_LVL = 130
            val sentence = TokenSentence()
            sentence.addToken(AltitudeToken(15000f))
            sentence.addComma()
            sentence.addToken(AltitudeToken(16534.21f))
            sentence.addComma()
            sentence.addToken(AltitudeToken(20456.56f))
            sentence.toTextSentence() shouldBe "FL150, FL165, FL205"
            sentence.toTTSSentence() shouldBe "Flight level One Five Zero, Flight level One Six Five, Flight level Two Zero Five".lowercase()
        }

        test("ATIS token") {
            val sentence = TokenSentence()
            sentence.addTokens(AtisToken('A'), AtisToken('B'),
                AtisToken('C'))
            sentence.toTextSentence() shouldBe "A B C"
            sentence.toTTSSentence() shouldBe "Alpha Bravo Charlie".lowercase()
        }

        test("Number token") {
            val sentence = TokenSentence()
            sentence.addToken(NumberToken(123))
            sentence.addComma()
            sentence.addToken(NumberToken(456))
            sentence.addComma()
            sentence.addToken(NumberToken(789))
            sentence.toTextSentence() shouldBe "123, 456, 789"
            sentence.toTTSSentence() shouldBe "One Two Tree, Four Five Six, Seven Eight Niner".lowercase()
        }

        test("Split character to NATO phonetic") {
            splitCharacterToNatoPhonetic("Shiba") shouldBe "Sierra Hotel India Bravo Alpha"
            splitCharacterToNatoPhonetic("FLE119") shouldBe "Foxtrot Lima Echo One One Niner"
            splitCharacterToNatoPhonetic("A1") shouldBe "Alpha One"
            splitCharacterToNatoPhonetic("R7") shouldBe "Romeo Seven"
            splitCharacterToNatoPhonetic("E3") shouldBe "Echo Tree"
        }

        test("Replace digits only to NATO phonetic") {
            replaceDigitsOnlyToNatoPhonetic("123") shouldBe " One  Two  Tree "
            replaceDigitsOnlyToNatoPhonetic("EVA226") shouldBe "EVA Two  Two  Six "
            replaceDigitsOnlyToNatoPhonetic("SIA123") shouldBe "SIA One  Two  Tree "
        }
    }
}