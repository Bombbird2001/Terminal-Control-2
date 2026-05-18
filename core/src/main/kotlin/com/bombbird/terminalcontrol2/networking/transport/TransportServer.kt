package com.bombbird.terminalcontrol2.networking.transport

import com.bombbird.terminalcontrol2.utilities.FileLog
import tc2relay.Relay.TcpMessage
import tc2relay.Relay.UdpMessage
import java.io.IOException
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A TCP + UDP server that accepts multiple [TransportConnection]s.
 * Used for LAN multiplayer where the game host acts as the server directly.
 */
class TransportServer {
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private val running = AtomicBoolean(false)
    private var acceptThread: Thread? = null
    private var udpReadThread: Thread? = null

    val connections = ConcurrentHashMap<Int, TransportConnection>()
    private var nextId = 1

    var onClientConnected: ((Int, Socket) -> Unit)? = null
    var onClientDisconnected: ((Int) -> Unit)? = null
    var onTcpReceived: ((Int, TcpMessage) -> Unit)? = null
    var onUdpReceived: ((UdpMessage, InetSocketAddress) -> Unit)? = null

    val tcpPort: Int get() = serverSocket?.localPort ?: 0
    val udpPort: Int get() = udpSocket?.localPort ?: 0

    fun bind(tcpPort: Int, udpPort: Int) {
        serverSocket = ServerSocket(tcpPort)
        udpSocket = DatagramSocket(udpPort)
    }

    fun start() {
        running.set(true)

        acceptThread = Thread({
            try {
                while (running.get()) {
                    val socket = serverSocket?.accept() ?: break
                    val connId = nextId++
                    val conn = TransportConnection()
                    conn.onTcpReceived = { msg -> onTcpReceived?.invoke(connId, msg) }
                    conn.onDisconnected = {
                        connections.remove(connId)
                        onClientDisconnected?.invoke(connId)
                    }
                    conn.initFromAccepted(socket)
                    connections[connId] = conn
                    onClientConnected?.invoke(connId, socket)
                }
            } catch (_: IOException) {
                // Server socket closed
            }
        }, "TC2-Server-Accept")
        acceptThread?.isDaemon = true
        acceptThread?.start()

        // UDP read thread
        udpReadThread = Thread({
            val buf = ByteArray(65536)
            val packet = DatagramPacket(buf, buf.size)
            try {
                while (running.get()) {
                    val socket = udpSocket ?: break
                    socket.receive(packet)
                    val data = packet.data.copyOf(packet.length)
                    try {
                        val msg = UdpMessage.parseFrom(data)
                        val sender = InetSocketAddress(packet.address, packet.port)
                        onUdpReceived?.invoke(msg, sender)
                    } catch (e: Exception) {
                        FileLog.info("TransportServer", "Failed to parse UDP: ${e.message}")
                    }
                }
            } catch (_: IOException) {
                // Socket closed
            }
        }, "TC2-Server-UDP-Read")
        udpReadThread?.isDaemon = true
        udpReadThread?.start()
    }

    fun sendTCPTo(connId: Int, msg: TcpMessage) {
        connections[connId]?.sendTCP(msg)
    }

    fun sendUDPTo(msg: UdpMessage, addr: InetSocketAddress) {
        val socket = udpSocket ?: return
        val data = msg.toByteArray()
        try {
            socket.send(DatagramPacket(data, data.size, addr))
        } catch (e: IOException) {
            FileLog.info("TransportServer", "UDP send failed: ${e.message}")
        }
    }

    fun stop() {
        running.set(false)
        for (conn in connections.values) {
            conn.disconnect()
        }
        connections.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        try { udpSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        udpReadThread?.interrupt()
    }

    fun disconnectClient(connId: Int) {
        connections.remove(connId)?.disconnect()
    }
}
