package com.bombbird.terminalcontrol2.networking.transport

import com.bombbird.terminalcontrol2.utilities.FileLog
import tc2relay.Relay.TcpMessage
import tc2relay.Relay.UdpMessage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A transport connection that speaks our length-prefixed protobuf protocol
 * over TCP with optional UDP, replacing KryoNet.
 *
 * Usable both as a client connecting to a remote host and (for LAN) wrapped
 * inside a server accept loop.
 */
class TransportConnection {
    @Volatile
    var isConnected = false
        private set

    private var tcpSocket: Socket? = null
    private var tcpOut: DataOutputStream? = null
    private var tcpIn: DataInputStream? = null
    private var udpSocket: DatagramSocket? = null
    private var udpRemoteAddress: InetSocketAddress? = null

    private val running = AtomicBoolean(false)
    private val sendQueue = LinkedBlockingQueue<ByteArray>()

    private var readThread: Thread? = null
    private var writeThread: Thread? = null
    private var udpReadThread: Thread? = null

    var onTcpReceived: ((TcpMessage) -> Unit)? = null
    var onUdpReceived: ((UdpMessage, InetSocketAddress?) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    val localTcpPort: Int? get() = tcpSocket?.localPort
    val localUdpPort: Int? get() = udpSocket?.localPort

    /** Connect to a remote host as a client. */
    fun connect(timeout: Int, host: String, tcpPort: Int, udpPort: Int) {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, tcpPort), timeout)
        socket.tcpNoDelay = true
        tcpSocket = socket
        tcpOut = DataOutputStream(socket.getOutputStream())
        tcpIn = DataInputStream(socket.getInputStream())

        if (udpPort > 0) {
            udpSocket = DatagramSocket()
            udpRemoteAddress = InetSocketAddress(host, udpPort)
        }

        isConnected = true
        running.set(true)
        startThreads()
    }

    /**
     * Initialize from an already-accepted socket (used by server-side accept loop).
     */
    fun initFromAccepted(socket: Socket) {
        socket.tcpNoDelay = true
        tcpSocket = socket
        tcpOut = DataOutputStream(socket.getOutputStream())
        tcpIn = DataInputStream(socket.getInputStream())
        isConnected = true
        running.set(true)
        startThreads()
    }

    private fun startThreads() {
        readThread = Thread({
            try {
                while (running.get()) {
                    val input = tcpIn ?: break
                    val len = input.readInt()
                    if (len <= 0 || len > 1024 * 1024) {
                        FileLog.info("TransportConnection", "Invalid frame length: $len")
                        break
                    }
                    val payload = ByteArray(len)
                    input.readFully(payload)
                    try {
                        val msg = TcpMessage.parseFrom(payload)
                        onTcpReceived?.invoke(msg)
                    } catch (e: Exception) {
                        FileLog.info("TransportConnection", "Failed to parse TCP message: ${e.message}")
                    }
                }
            } catch (_: IOException) {
                // Connection closed
            } catch (_: Exception) {
                // Unexpected error
            } finally {
                disconnect()
            }
        }, "TC2-TCP-Read")
        readThread?.isDaemon = true
        readThread?.start()

        writeThread = Thread({
            try {
                while (running.get()) {
                    val data = sendQueue.take()
                    val output = tcpOut ?: break
                    synchronized(output) {
                        output.writeInt(data.size)
                        output.write(data)
                        output.flush()
                    }
                }
            } catch (_: IOException) {
                // Connection closed
            } catch (_: InterruptedException) {
                // Shutdown
            } finally {
                disconnect()
            }
        }, "TC2-TCP-Write")
        writeThread?.isDaemon = true
        writeThread?.start()

        if (udpSocket != null) {
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
                            FileLog.info("TransportConnection", "Failed to parse UDP message: ${e.message}")
                        }
                    }
                } catch (_: IOException) {
                    // Socket closed
                }
            }, "TC2-UDP-Read")
            udpReadThread?.isDaemon = true
            udpReadThread?.start()
        }
    }

    /** Send a TcpMessage (queued, non-blocking). */
    fun sendTCP(msg: TcpMessage) {
        if (!isConnected) return
        sendQueue.offer(msg.toByteArray())
    }

    /** Send a UdpMessage to the remote UDP address. */
    fun sendUDP(msg: UdpMessage) {
        val socket = udpSocket ?: return
        val addr = udpRemoteAddress ?: return
        val data = msg.toByteArray()
        try {
            socket.send(DatagramPacket(data, data.size, addr))
        } catch (e: IOException) {
            FileLog.info("TransportConnection", "UDP send failed: ${e.message}")
        }
    }

    fun disconnect() {
        if (!running.compareAndSet(true, false)) return
        isConnected = false
        try { tcpSocket?.close() } catch (_: Exception) {}
        try { udpSocket?.close() } catch (_: Exception) {}
        tcpSocket = null
        udpSocket = null
        sendQueue.clear()
        readThread?.interrupt()
        writeThread?.interrupt()
        onDisconnected?.invoke()
    }

    val returnTripTime: Int
        get() = 0 // TODO: implement ping measurement
}
