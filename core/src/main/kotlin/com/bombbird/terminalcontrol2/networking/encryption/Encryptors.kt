package com.bombbird.terminalcontrol2.networking.encryption

import com.esotericsoftware.minlog.Log
import java.lang.Exception
import java.security.SecureRandom
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
class AESGCMEncryptor(private val serializeObj: (Any) -> ByteArray): Encryptor {
    companion object {
        const val ENCRYPTION_MODE = "AES/GCM/PKCS5Padding"
        const val AES_KEY_LENGTH_BYTES = 16
        const val GCM_TAG_LENGTH_BYTES = 16
    }

    private lateinit var secretKey: SecretKey
    private val cipher = Cipher.getInstance(ENCRYPTION_MODE)

    override fun encrypt(dataToEncrypt: NeedsEncryption): EncryptedData? {
        try {
            val iv = ByteArray(GCM_TAG_LENGTH_BYTES)
            SecureRandom().nextBytes(iv)
            val gcmPara = GCMParameterSpec(8 * iv.size, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmPara)
            return EncryptedData(iv, cipher.doFinal(serializeObj(dataToEncrypt)))
        } catch (e: Exception) {
            Log.info("AESGCMEncryptor", "Failed to encrypt due to ${e.javaClass.name}")
        }

        return null
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
            Log.info("AESGCMDecrypter", "Failed to decrypt due to ${e.javaClass.name}")
        }

        return null
    }

    override fun setKey(key: SecretKey) {
        secretKey = key
    }
}
