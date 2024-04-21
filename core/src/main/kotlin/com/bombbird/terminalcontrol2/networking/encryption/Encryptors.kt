package com.bombbird.terminalcontrol2.networking.encryption

import com.bombbird.terminalcontrol2.utilities.FileLog
import java.lang.Exception
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encryptor interface to be implemented for servers and client */
interface Encryptor {
    /**
     * Encrypts the given [NeedsEncryption] object to be encrypted into an [EncryptedData] instance
     * @param dataToEncrypt the data object to be encrypted
     */
    fun encrypt(dataToEncrypt: NeedsEncryption): EncryptedData?

    /**
     * Encrypts the given [NeedsEncryption] object to be encrypted into an [EncryptedData] instance, using the provided
     * IV
     * @param iv the IV to use for encryption; it should be generated randomly and securely
     * @param dataToEncrypt the data object to be encrypted
     */
    fun encryptWithIV(iv: ByteArray, dataToEncrypt: NeedsEncryption): EncryptedData?

    /**
     * Sets the key used for decryption
     * @param key secret key for encryption
     */
    fun setKey(key: SecretKey)
}

/** Decrypter interface to be implemented for servers and client */
interface Decrypter {
    /**
     * Decrypts the given [EncryptedData] to an object
     */
    fun decrypt(encryptedData: EncryptedData): Any?

    /**
     * Sets the key used for decryption
     * @param key secret key for decryption
     */
    fun setKey(key: SecretKey)
}

/**
 * Class to encrypt data using AES-GCM mode
 * @param serializeObj the function passed from server/client to serialize an object into byte array before encryption
 */
class AESGCMEncryptor(private val serializeObj: (Any) -> ByteArray?): Encryptor {
    companion object {
        const val ENCRYPTION_MODE = "AES/GCM/NoPadding"
        const val AES_KEY_LENGTH_BYTES = 16
        const val GCM_TAG_LENGTH_BYTES = 12
    }

    private lateinit var secretKey: SecretKey
    private val cipher = Cipher.getInstance(ENCRYPTION_MODE)
    private var ivCounter = BigInteger.ZERO

    override fun encrypt(dataToEncrypt: NeedsEncryption): EncryptedData? {
        return encryptWithIV(ivCounter.toByteArray(GCM_TAG_LENGTH_BYTES), dataToEncrypt)
    }

    override fun encryptWithIV(iv: ByteArray, dataToEncrypt: NeedsEncryption): EncryptedData? {
        return encryptWithIV(iv, dataToEncrypt, 0)
    }

    private fun encryptWithIV(iv: ByteArray, dataToEncrypt: NeedsEncryption, retry: Int): EncryptedData? {
        if (retry > 3) {
            FileLog.warn("AESGCMEncryptor", "Failed to encrypt after 3 retries")
            return null
        }

        try {
            val gcmPara = GCMParameterSpec(8 * iv.size, iv)
            ivCounter = BigInteger(iv) + BigInteger.ONE
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmPara)
            return EncryptedData(iv, cipher.doFinal(serializeObj(dataToEncrypt) ?: return null))
        } catch (e: IllegalStateException) {
            FileLog.warn("AESGCMEncryptor", "IV ${BigInteger(iv)} repeated, incrementing and trying again\n${e.stackTraceToString()}")
            return encryptWithIV(ivCounter.toByteArray(GCM_TAG_LENGTH_BYTES), dataToEncrypt, retry + 1)
        } catch(e: InvalidAlgorithmParameterException) {
            FileLog.warn("AESGCMEncryptor", "IV ${BigInteger(iv)} repeated, incrementing and trying again\n${e.stackTraceToString()}")
            return encryptWithIV(ivCounter.toByteArray(GCM_TAG_LENGTH_BYTES), dataToEncrypt, retry + 1)
        } catch (e: Exception) {
            FileLog.warn("AESGCMEncryptor", "Failed to encrypt due to\n${e.stackTraceToString()}")
        }

        return null
    }

    private fun BigInteger.toByteArray(arrayLength: Int): ByteArray {
        val byteArray = ByteArray(arrayLength)
        val bigIntBytes = toByteArray()
        bigIntBytes.copyInto(byteArray, destinationOffset = byteArray.size - bigIntBytes.size)
        return byteArray
    }

    override fun setKey(key: SecretKey) {
        secretKey = key
    }
}

/**
 * Class to decrypt data using AES-GCM mode
 * @param deserializeObj the function passed from server/client to de-serialize a byte array into an object after
 * decryption
 */
class AESGCMDecrypter(private val deserializeObj: (ByteArray) -> Any?): Decrypter {
    private lateinit var secretKey: SecretKey
    private val cipher = Cipher.getInstance(AESGCMEncryptor.ENCRYPTION_MODE)

    override fun decrypt(encryptedData: EncryptedData): Any? {
        try {
            val iv = encryptedData.iv
            val gcmPara = GCMParameterSpec(8 * iv.size, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmPara)
            val decrypted = cipher.doFinal(encryptedData.ciphertext)
            return deserializeObj(decrypted)
        } catch (e: Exception) {
            FileLog.info("AESGCMDecrypter", "Failed to decrypt due to\n${e.stackTraceToString()}")
        }

        return null
    }

    override fun setKey(key: SecretKey) {
        secretKey = key
    }
}
