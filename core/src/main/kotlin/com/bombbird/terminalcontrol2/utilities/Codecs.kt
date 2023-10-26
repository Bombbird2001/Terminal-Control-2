package com.bombbird.terminalcontrol2.utilities

import org.apache.commons.codec.binary.Base64

/**
 * Decodes a base64 string into a byte array - required to bypass an issue with NoSuchMethodError when running
 * on Android devices which use older versions (< 1.4) of Apache Commons Codec
 */
fun decodeBase64(str: String): ByteArray {
    return Base64.decodeBase64(str.toByteArray())
}
