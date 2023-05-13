package com.bombbird.terminalcontrol2.networking.encryption

import org.apache.commons.codec.digest.DigestUtils
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Class for providing Diffie-Hellman calculations
 * @param g generator value
 * @param p a prime value, should be large (>= 2048 bits)
 */
class DiffieHellman(private val g: BigInteger, private val p: BigInteger) {
    companion object {
        private val random = SecureRandom()
    }

    private lateinit var privateExp: BigInteger
    private lateinit var exchangeValue: BigInteger

    init {
        val bits = p.bitLength()
        if (bits < 2048)
            throw IllegalArgumentException("DiffieHellman - Bit count of prime is too low, requires >= 2048, got $bits")
    }

    /**
     * Calculates the value to be sent over the network by generating a random value a, then calculating g^a mod p
     * @return a BigInteger representing the value to be sent over network
     */
    fun getExchangeValue(): BigInteger {
        // 264-bit private exponent
        val randomBytes = ByteArray(33)
        random.nextBytes(randomBytes)
        privateExp = BigInteger(randomBytes)
        exchangeValue = g.modPow(privateExp, p)
        return exchangeValue
    }

    /**
     * Calculates the DH key using the received exchange value
     * @param receivedValue the exchange value received from other party
     * @return a BigInteger representing the value of calculated key
     */
    private fun getKey(receivedValue: BigInteger): BigInteger {
        return receivedValue.modPow(privateExp, p)
    }

    /**
     * Generates the 128-bit AES key using the DH key value calculated with the input received exchange value as well as
     * parameters provided and calculated beforehand
     *
     * The AES key is generated from the DH key by hashing (SHA256) it and taking the first 128 bits of the hash
     * @param receivedValue the exchange value received from other party
     * @return the SecretKey object containing the AES key
     */
    fun getAES128Key(receivedValue: BigInteger): SecretKey {
        return SecretKeySpec(DigestUtils.sha256(getKey(receivedValue).toByteArray()), 0, 16, "AES")
    }
}