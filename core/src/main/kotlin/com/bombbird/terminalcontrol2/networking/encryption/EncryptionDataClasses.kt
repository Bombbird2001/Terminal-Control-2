package com.bombbird.terminalcontrol2.networking.encryption

/** Encrypted data to be sent and decrypted */
class EncryptedData(val iv: ByteArray = byteArrayOf(), val ciphertext: ByteArray = byteArrayOf())

/** Interface to mark classes that must be encrypted to [EncryptedData] before being sent in transit */
interface NeedsEncryption
