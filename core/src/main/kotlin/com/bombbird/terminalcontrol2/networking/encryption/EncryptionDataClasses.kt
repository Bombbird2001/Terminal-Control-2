package com.bombbird.terminalcontrol2.networking.encryption

import java.math.BigInteger

/** Encrypted data to be sent and decrypted */
class EncryptedData(val iv: ByteArray = byteArrayOf(), val ciphertext: ByteArray = byteArrayOf())

/** Diffie-Hellman key exchange parameters sent over the network to establish symmetric key */
class DiffieHellmanValue(val xy: BigInteger = BigInteger.ZERO)

/** Interface to mark classes that must be encrypted to [EncryptedData] before being sent in transit */
interface NeedsEncryption
