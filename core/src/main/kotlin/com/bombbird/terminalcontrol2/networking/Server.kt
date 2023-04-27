package com.bombbird.terminalcontrol2.networking

import java.util.UUID

abstract class Server(
    val gameServer: GameServer,
    val onReceive: (ConnectionMeta, Any?) -> Unit,
    val onConnect: (ConnectionMeta) -> Unit,
    val onDisconnect: (ConnectionMeta) -> Unit
) {
    abstract fun start(tcpPort: Int, udpPort: Int)

    abstract fun stop()

    abstract fun sendToAllTCP(data: Any)

    abstract fun sendToAllUDP(data: Any)

    abstract fun sendTCPToConnection(uuid: UUID, data: Any)

    abstract val connections: Collection<ConnectionMeta>
}