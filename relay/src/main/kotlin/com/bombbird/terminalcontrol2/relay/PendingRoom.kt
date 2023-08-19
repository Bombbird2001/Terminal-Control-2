package com.bombbird.terminalcontrol2.relay

import com.bombbird.terminalcontrol2.networking.encryption.AESGCMDecrypter
import com.bombbird.terminalcontrol2.networking.encryption.AESGCMEncryptor
import com.bombbird.terminalcontrol2.networking.encryption.EncryptedData
import com.bombbird.terminalcontrol2.networking.encryption.NeedsEncryption
import java.util.*
import javax.crypto.SecretKey

/**
 * Class for encapsulating a pending room connection, initiated after a request is sent to the create room endpoint
 * @param serverKey the secret key generated for the room used by this room to encrypt traffic
 * @param hostKey the secret key generated for the host used by the connecting host to encrypt traffic
 * @param timerTask the timer task to remove the pending room if no room is created in time
 */
class PendingRoom(val serverKey: SecretKey, val hostKey: SecretKey, private val timerTask: TimerTask) {
    val roomEncryptor = AESGCMEncryptor(RelayServer::getSerialisedBytes).apply { setKey(serverKey) }
    val hostDecrypter = AESGCMDecrypter(RelayServer::fromSerializedBytes).apply { setKey(hostKey) }

    /** Function to be called when the associated room is actually created */
    fun roomCreated() {
        // Perform the removal now
        timerTask.cancel()
        timerTask.run()
    }

    /**
     * Performs encryption on the input data if needed using the server's encryptor, and returns the encrypted result
     *
     * If encryption not needed, returns the object itself
     */
    fun encryptIfNeeded(data: Any): Any? {
        (data as? NeedsEncryption)?.let {
            return roomEncryptor.encrypt(it)
        } ?: return data
    }

    /**
     * Performs decryption on the received data using the room's key - this method will only return valid data if
     * decrypting data sent from a host who opened this pending room
     * @param data ciphertext to be decrypted
     * @return the decrypted object
     */
    fun decryptFromHost(data: EncryptedData): Any? {
        return hostDecrypter.decrypt(data)
    }
}