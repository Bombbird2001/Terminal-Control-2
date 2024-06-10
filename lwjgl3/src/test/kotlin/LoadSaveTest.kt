import com.badlogic.gdx.Gdx
import com.bombbird.terminalcontrol2.TerminalControl2
import com.bombbird.terminalcontrol2.components.Altitude
import com.bombbird.terminalcontrol2.files.*
import com.bombbird.terminalcontrol2.global.APP_TYPE
import com.bombbird.terminalcontrol2.global.GAME
import com.bombbird.terminalcontrol2.global.GAME_SERVER_THREAD_NAME
import com.bombbird.terminalcontrol2.integrations.StubDiscordHandler
import com.bombbird.terminalcontrol2.integrations.StubPlayServicesHandler
import com.bombbird.terminalcontrol2.networking.GameServer
import com.bombbird.terminalcontrol2.sounds.StubTextToSpeech
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import ktx.ashley.get
import java.io.File

/** Kotest FunSpec class for testing saving & loading functions */
object LoadSaveTest: FunSpec() {
    private const val TEST_SAVE_ID = -12321

    init {
        Gdx.app = Lwjgl3StubApplication

        GAME = TerminalControl2(StubExternalFileHandler, StubTextToSpeech, StubDiscordHandler, StubPlayServicesHandler)
        APP_TYPE = Gdx.app.type

        val gs = GameServer.testGameServer().apply {
            saveID = TEST_SAVE_ID
            mainName = "TCTP"
        }
        GAME.gameServer = gs

        val saveBufferedReader = File("..\\lwjgl3\\src\\test\\kotlin\\loadSaveTestSampleSave.txt").bufferedReader()
        val testSaveString = saveBufferedReader.use { it.readText() }
        testSaveString.length shouldBe 404787
        val testSaveCorruptString = "$testSaveString}"

        val metaBufferedReader = File("..\\lwjgl3\\src\\test\\kotlin\\loadSaveTestSampleMeta.txt").bufferedReader()
        val testMetaString = metaBufferedReader.use { it.readText() }
        testMetaString.length shouldBe 111

        val saveModifiedBufferedReader = File("..\\lwjgl3\\src\\test\\kotlin\\loadSaveTestSampleSaveModified.txt").bufferedReader()
        val testSaveModifiedString = saveModifiedBufferedReader.use { it.readText() }
        testSaveModifiedString.length shouldBe 404784
        val testSaveModifiedCorruptString = "$testSaveModifiedString}"

        val metaModifiedBufferedReader = File("..\\lwjgl3\\src\\test\\kotlin\\loadSaveTestSampleMetaModified.txt").bufferedReader()
        val testMetaModifiedString = metaModifiedBufferedReader.use { it.readText() }
        testMetaModifiedString.length shouldBe 112

        @OptIn(ExperimentalStdlibApi::class)
        val gameSaveMetaMoshi = Moshi.Builder().build().adapter<GameSaveMeta>()
        val testMeta = gameSaveMetaMoshi.fromJson(testMetaString).shouldNotBeNull()
        val testMetaModified = gameSaveMetaMoshi.fromJson(testMetaModifiedString).shouldNotBeNull()

        val saveFolder = getExtDir("saves").shouldNotBeNull()
        val saveHandle = saveFolder.child("${TEST_SAVE_ID}.json")
        val saveMetaHandle = saveFolder.child("${TEST_SAVE_ID}.meta")
        val backupHandle = saveFolder.child("${TEST_SAVE_ID}-backup.json")
        val backupMetaHandle = saveFolder.child("${TEST_SAVE_ID}-backup.meta")

        beforeTest {
            // Clear all test save files before each test
            saveHandle.delete()
            saveMetaHandle.delete()
            backupHandle.delete()
            backupMetaHandle.delete()

            saveHandle.exists().shouldBeFalse()
            saveMetaHandle.exists().shouldBeFalse()
            backupHandle.exists().shouldBeFalse()
            backupMetaHandle.exists().shouldBeFalse()

            // Suppress entity created on wrong thread log message
            Thread.currentThread().name = GAME_SERVER_THREAD_NAME
        }

        /** Writes the main save data to the relevant files */
        fun loadMainSave(corrupted: Boolean) {
            saveHandle.writeString(if (corrupted) testSaveCorruptString else testSaveString, false)
            saveMetaHandle.writeString(testMetaString, false)
            saveHandle.readString() shouldBe (if (corrupted) testSaveCorruptString else testSaveString)
            saveMetaHandle.readString() shouldBe testMetaString
        }

        /** Writes the backup save data to the relevant files */
        fun loadBackupSave(corrupted: Boolean) {
            backupHandle.writeString(if (corrupted) testSaveModifiedCorruptString else testSaveModifiedString, false)
            backupMetaHandle.writeString(testMetaModifiedString, false)
            backupHandle.readString() shouldBe (if (corrupted) testSaveModifiedCorruptString else testSaveModifiedString)
            backupMetaHandle.readString() shouldBe testMetaModifiedString
        }

        test("Load & save with only main save") {
            loadMainSave(false)

            loadSave(gs, TEST_SAVE_ID)

            gs.aircraft.size shouldBe 39
            gs.score shouldBe 9

            gs.aircraft["XAX995"].entity[Altitude.mapper]?.altitudeFt = 25000f
            gs.score = 11

            saveGame(gs)

            saveHandle.exists().shouldBeTrue()
            saveHandle.readString().length shouldBeGreaterThan 0

            saveMetaHandle.exists().shouldBeTrue()
            saveMetaHandle.readString().removeLastPlayedDatetime() shouldBe testMetaModifiedString

            backupHandle.exists().shouldBeTrue()
            backupHandle.readString() shouldBe testSaveString

            backupMetaHandle.exists().shouldBeTrue()
            backupMetaHandle.readString() shouldBe testMetaString
        }

        test("Load & save with main + backup save") {
            loadMainSave(false)
            loadBackupSave(false)

            loadSave(gs, TEST_SAVE_ID)

            gs.aircraft.size shouldBe 39
            gs.score shouldBe 9

            gs.aircraft["XAX995"].entity[Altitude.mapper]?.altitudeFt = 25000f
            gs.score = 11

            saveGame(gs)

            saveHandle.exists().shouldBeTrue()
            saveHandle.readString().length shouldBeGreaterThan 0

            saveMetaHandle.exists().shouldBeTrue()
            saveMetaHandle.readString().removeLastPlayedDatetime() shouldBe testMetaModifiedString

            backupHandle.exists().shouldBeTrue()
            backupHandle.readString() shouldBe testSaveString

            backupMetaHandle.exists().shouldBeTrue()
            backupMetaHandle.readString() shouldBe testMetaString
        }

        test("Load & save with only backup save") {
            loadBackupSave(false)

            loadSave(gs, TEST_SAVE_ID)

            gs.aircraft.size shouldBe 39
            gs.score shouldBe 11

            gs.aircraft["XAX995"].entity[Altitude.mapper]?.altitudeFt = 24000f
            gs.score = 9

            saveGame(gs)

            saveHandle.exists().shouldBeTrue()
            saveHandle.readString().length shouldBeGreaterThan 0

            saveMetaHandle.exists().shouldBeTrue()
            saveMetaHandle.readString().removeLastPlayedDatetime() shouldBe testMetaString

            backupHandle.exists().shouldBeTrue()
            backupHandle.readString() shouldBe testSaveModifiedString

            backupMetaHandle.exists().shouldBeTrue()
            backupMetaHandle.readString() shouldBe testMetaModifiedString
        }

        test("Load & save with corrupted main save + normal backup save") {
            loadMainSave(true)
            loadBackupSave(false)

            loadSave(gs, TEST_SAVE_ID)

            gs.aircraft.size shouldBe 39
            gs.score shouldBe 11

            gs.aircraft["XAX995"].entity[Altitude.mapper]?.altitudeFt = 24000f
            gs.score = 9

            saveGame(gs)

            saveHandle.exists().shouldBeTrue()
            saveHandle.readString().length shouldBeGreaterThan 0

            saveMetaHandle.exists().shouldBeTrue()
            saveMetaHandle.readString().removeLastPlayedDatetime() shouldBe testMetaString

            backupHandle.exists().shouldBeTrue()
            backupHandle.readString() shouldBe testSaveModifiedString

            backupMetaHandle.exists().shouldBeTrue()
            backupMetaHandle.readString() shouldBe testMetaModifiedString
        }

        test("Load & save with normal main save + corrupted backup save") {
            loadMainSave(false)
            loadBackupSave(true)

            loadSave(gs, TEST_SAVE_ID)

            gs.aircraft.size shouldBe 39
            gs.score shouldBe 9

            gs.aircraft["XAX995"].entity[Altitude.mapper]?.altitudeFt = 25000f
            gs.score = 11

            saveGame(gs)

            saveHandle.exists().shouldBeTrue()
            saveHandle.readString().length shouldBeGreaterThan 0

            saveMetaHandle.exists().shouldBeTrue()
            saveMetaHandle.readString().removeLastPlayedDatetime() shouldBe testMetaModifiedString

            backupHandle.exists().shouldBeTrue()
            backupHandle.readString() shouldBe testSaveString

            backupMetaHandle.exists().shouldBeTrue()
            backupMetaHandle.readString() shouldBe testMetaString
        }

        test("Get available saves with main save + backup save") {
            loadMainSave(false)
            loadBackupSave(false)

            val gamesFound = getAvailableSaveGames()
            gamesFound[TEST_SAVE_ID].shouldNotBeNull() shouldBe testMeta
        }

        test("Get available saves with main save only") {
            loadMainSave(false)

            val gamesFound = getAvailableSaveGames()
            gamesFound[TEST_SAVE_ID].shouldNotBeNull() shouldBe testMeta
        }

        test("Get available saves with backup save only") {
            loadBackupSave(false)

            val gamesFound = getAvailableSaveGames()
            gamesFound[TEST_SAVE_ID].shouldNotBeNull() shouldBe testMetaModified
        }
    }

    private fun String.removeLastPlayedDatetime(): String {
        return replace("\"lastPlayedDatetime\":\".*?\"".toRegex(), "\"lastPlayedDatetime\":\"\"")
    }
}