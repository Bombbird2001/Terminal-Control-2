import com.bombbird.terminalcontrol2.global.DIFFIE_HELLMAN_GENERATOR
import com.bombbird.terminalcontrol2.global.DIFFIE_HELLMAN_PRIME
import com.bombbird.terminalcontrol2.global.SERVER_WRITE_BUFFER_SIZE
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientData
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMDecrypter
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMEncryptor
import com.bombbird.terminalcontrol2.networking.encryption.DiffieHellman
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import com.bombbird.terminalcontrol2.networking.registerClassesToKryo
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigInteger
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

        test("Encrypt then decrypt - ClientData") {
            val uuid = UUID.randomUUID().toString()
            val cipherText = encryptor.encrypt(ClientData(uuid)).shouldNotBeNull()
            val decrypted = (decrypter.decrypt(cipherText) as? ClientData).shouldNotBeNull()
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

        test("Diffie-Hellman") {
            val dh1 = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
            val exchange1 = dh1.getExchangeValue()
            val dh2 = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
            val exchange2 = dh2.getExchangeValue()
            dh1.getAES128Key(exchange2).encoded shouldBe dh2.getAES128Key(exchange1).encoded
        }

        test("Diffie-Hellman wrong value") {
            val dh1 = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
            val exchange1 = dh1.getExchangeValue() + BigInteger.valueOf(4)
            val dh2 = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
            val exchange2 = dh2.getExchangeValue()
            dh1.getAES128Key(exchange2).encoded shouldNotBe dh2.getAES128Key(exchange1).encoded
        }

        test("Diffie-Hellman small prime") {
            shouldThrow<IllegalArgumentException> {
                DiffieHellman(DIFFIE_HELLMAN_GENERATOR, BigInteger.valueOf(Int.MAX_VALUE.toLong()))
            }
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