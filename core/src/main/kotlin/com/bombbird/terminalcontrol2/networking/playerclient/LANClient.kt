package com.bombbird.terminalcontrol2.networking.playerclient

import com.bombbird.terminalcontrol2.global.*
import com.bombbird.terminalcontrol2.networking.NetworkClient
import com.bombbird.terminalcontrol2.networking.dataclasses.ClientUUIDData
import com.bombbird.terminalcontrol2.networking.dataclasses.RequestClientUUID
import com.bombbird.terminalcontrol2.networking.encryption.*
import com.bombbird.terminalcontrol2.networking.handleIncomingRequestClient
import com.bombbird.terminalcontrol2.networking.registerClassesToKryo
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryonet.Client
import com.esotericsoftware.kryonet.Connection
import com.esotericsoftware.kryonet.Listener
import com.esotericsoftware.minlog.Log

/**
 * Client for handling LAN multiplayer games
 *
 * Cannot be used for public multiplayer games, use [PublicClient] for that
 */
class LANClient(lanClientDiscoveryHandler: LANClientDiscoveryHandler): NetworkClient() {
    override val isConnected: Boolean
        get() = client.isConnected

    override val encryptor: Encryptor = AESGCMEncryptor(::getSerialisedBytes)
    override val decrypter: Decrypter = AESGCMDecrypter(::fromSerializedBytes)
    override val kryo: Kryo
        get() = client.kryo

    private var secretKeyCalculated = false

    private val client = Client(CLIENT_WRITE_BUFFER_SIZE, CLIENT_READ_BUFFER_SIZE).apply {
        setDiscoveryHandler(lanClientDiscoveryHandler)
        addListener(object: Listener {
            override fun received(connection: Connection, obj: Any?) {
                if (!secretKeyCalculated && obj is DiffieHellmanValue) {
                    // Calculate DH values
                    val dh = DiffieHellman(DIFFIE_HELLMAN_GENERATOR, DIFFIE_HELLMAN_PRIME)
                    val toSend = dh.getExchangeValue()
                    val secretKey = dh.getAES128Key(obj.xy)
                    encryptor.setKey(secretKey)
                    decrypter.setKey(secretKey)

                    // Key established
                    secretKeyCalculated = true
                    connection.sendTCP(DiffieHellmanValue(toSend))
                    return
                }

                if (!secretKeyCalculated) return

                if (obj is NeedsEncryption) {
                    Log.info("RelayServer", "Received unencrypted data of class ${obj.javaClass.name}")
                    return
                }

                val decrypted = if (obj is EncryptedData) {
                    decrypter.decrypt(obj)
                } else obj

                (decrypted as? RequestClientUUID)?.apply {
                    this@LANClient.sendTCP(ClientUUIDData(myUuid.toString()))
                } ?: handleIncomingRequestClient(GAME.gameClientScreen ?: return, decrypted)
            }
        })
    }

    override fun connect(timeout: Int, connectionHost: String, tcpPort: Int, udpPort: Int) {
        client.connect(timeout, connectionHost, tcpPort, udpPort)
    }

    override fun reconnect() {
        client.reconnect()
    }

    override fun sendTCP(data: Any) {
        val encrypted = encryptIfNeeded(data) ?: return
        client.sendTCP(encrypted)
    }

    override fun beforeConnect(roomId: Short?) {
        registerClassesToKryo(kryo)
        secretKeyCalculated = false
    }

    override fun start() {
        client.start()
    }

    override fun stop() {
        client.stop()
    }

    override fun dispose() {
        client.dispose()
    }

    fun discoverHosts(udpPort: Int) {
        client.discoverHosts(udpPort, 2000)
    }

    /**
     * Serialises the input object with Kryo and returns the byte array
     * @param data the object to serialise; it should have been registered with Kryo first
     * @return a byte array containing the serialised object
     */
    @Synchronized
    private fun getSerialisedBytes(data: Any): ByteArray {
        val serialisationOutput = Output(SERVER_WRITE_BUFFER_SIZE)
        client.kryo.writeClassAndObject(serialisationOutput, data)
        return serialisationOutput.toBytes()
    }

    /**
     * De-serialises the input byte array with Kryo and returns the object
     * @param data the byte array to de-serialise
     * @return the de-serialised object
     */
    @Synchronized
    private fun fromSerializedBytes(data: ByteArray): Any? {
        return client.kryo.readClassAndObject(Input(data))
    }
}