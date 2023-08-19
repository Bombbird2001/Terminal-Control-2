package com.bombbird.terminalcontrol2.networking.encryption

import java.math.BigInteger

/** Encrypted data to be sent and decrypted */
class EncryptedData(val iv: ByteArray = byteArrayOf(), val ciphertext: ByteArray = byteArrayOf())

/** Diffie-Hellman key exchange parameters sent over the network to establish symmetric key */
@Deprecated("Old version of DiffieHellmanValue class, kept for compatibility with builds < 10", ReplaceWith("DiffieHellmanValues"))
class DiffieHellmanValueOld(val xy: BigInteger = BigInteger.ZERO)

/**
 * Diffie-Hellman key exchange parameters sent over the network to establish 2 separate symmetric keys, 1 each for
 * encryption by server and client respectively, to prevent IV reuse for the same key
 */
class DiffieHellmanValues(val serverXy: BigInteger = BigInteger.ZERO, val clientXy: BigInteger = BigInteger.ZERO)

/** Interface to mark classes that must be encrypted to [EncryptedData] before being sent in transit */
interface NeedsEncryption
