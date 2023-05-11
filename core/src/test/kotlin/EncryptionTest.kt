import com.bombbird.terminalcontrol2.global.SERVER_WRITE_BUFFER_SIZE
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDData
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMDecrypter
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMEncryptor
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.networking.registerClassesToKryo
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID
import javax.crypto.KeyGenerator

/** Kotest FunSpec class for testing encryption functions */
object EncryptionTest: FunSpec() {
    private val kryo = Kryo()
    private val encryptor = AESGCMEncryptor(::getSerialisedBytes)
    private val decrypter = AESGCMDecrypter(::fromSerializedBytes)

    class WrappedByteArray(val byteArray: ByteArray = byteArrayOf()): NeedsEncryption

    class WrappedString(val string: String = ""): NeedsEncryption

    init {
        registerClassesToKryo(kryo)
        registerTestClasses(kryo)

        val secretKey = KeyGenerator.getInstance("AES").apply { init(AESGCMEncryptor.AES_KEY_LENGTH_BYTES * 8) }.generateKey()
        encryptor.setKey(secretKey)
        decrypter.setKey(secretKey)

        test("Encrypt then decrypt - Byte Array") {
            val byteArray = byteArrayOf(3, 4, 8, 1, 89, 126, 34, 0, -56, -93)
            val cipherText = encryptor.encrypt(WrappedByteArray(byteArray)).shouldNotBeNull()
            val decrypted = (decrypter.decrypt(cipherText) as? WrappedByteArray).shouldNotBeNull()
            decrypted.byteArray shouldBe byteArray
        }

        test("Encrypt then decrypt - String") {
            val str = "kewtshibakewtshibakewtshibakewtshibakewtshibakewtshibakewtshiba"
            val cipherText = encryptor.encrypt(WrappedString(str)).shouldNotBeNull()
            val decrypted = (decrypter.decrypt(cipherText) as? WrappedString).shouldNotBeNull()
            decrypted.string shouldBe str
        }

        test("Encrypt then decrypt - ClientUUIDData") {
            val uuid = UUID.randomUUID().toString()
            val cipherText = encryptor.encrypt(ClientUUIDData(uuid)).shouldNotBeNull()
            val decrypted = (decrypter.decrypt(cipherText) as? ClientUUIDData).shouldNotBeNull()
            decrypted.uuid shouldBe uuid
        }

        test("Modified ciphertext") {
            val byteArray = byteArrayOf(3, 4, 8, 1, 89, 126, 34, 0, -56, -93)
            val cipherText = encryptor.encrypt(WrappedByteArray(byteArray)).shouldNotBeNull()
            cipherText.ciphertext[3] = (cipherText.ciphertext[3] - 42).toByte()
            decrypter.decrypt(cipherText).shouldBeNull()
        }

        test("Modified tag") {
            val str = "kewtshibakewtshibakewtshibakewtshibakewtshibakewtshibakewtshiba"
            val cipherText = encryptor.encrypt(WrappedString(str)).shouldNotBeNull()
            cipherText.iv[6] = (cipherText.iv [6] + 38).toByte()
            decrypter.decrypt(cipherText).shouldBeNull()
        }
    }

    @Synchronized
    private fun getSerialisedBytes(data: Any): ByteArray {
        val serialisationOutput = Output(SERVER_WRITE_BUFFER_SIZE)
        kryo.writeClassAndObject(serialisationOutput, data)
        return serialisationOutput.toBytes()
    }

    @Synchronized
    private fun fromSerializedBytes(data: ByteArray): Any? {
        return kryo.readClassAndObject(Input(data))
    }

    private fun registerTestClasses(kryo: Kryo) {
        kryo.register(WrappedByteArray::class.java)
        kryo.register(WrappedString::class.java)
    }
}