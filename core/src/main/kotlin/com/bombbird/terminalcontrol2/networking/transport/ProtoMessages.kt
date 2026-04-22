package com.bombbird.terminalcontrol2.networking.transport

import com.google.protobuf.ByteString
import tc2relay.Relay.*

/** Helper functions for building protocol messages. */
object ProtoMessages {

    // --- TCP message builders ---

    fun challengeResponse(ciphertext: ByteArray): TcpMessage =
        TcpMessage.newBuilder()
            .setChallengeResponse(ChallengeResponse.newBuilder().setCiphertext(ByteString.copyFrom(ciphertext)))
            .build()

    fun requestRelayAction(encryptedPayload: ByteArray, iv: ByteArray): TcpMessage =
        TcpMessage.newBuilder()
            .setRequestRelayAction(
                RequestRelayAction.newBuilder()
                    .setEncryptedPayload(ByteString.copyFrom(encryptedPayload))
                    .setIv(ByteString.copyFrom(iv))
            )
            .build()

    fun newGameRequest(roomId: Int, maxPlayers: Int, mapName: String, uuid: String): TcpMessage =
        TcpMessage.newBuilder()
            .setNewGameRequest(
                NewGameRequest.newBuilder()
                    .setRoomId(roomId)
                    .setMaxPlayers(maxPlayers)
                    .setMapName(mapName)
                    .setUuid(uuid)
            )
            .build()

    fun joinGameRequest(roomId: Int, uuid: String): TcpMessage =
        TcpMessage.newBuilder()
            .setJoinGameRequest(JoinGameRequest.newBuilder().setRoomId(roomId).setUuid(uuid))
            .build()

    fun playerConnected(uuid: String): TcpMessage =
        TcpMessage.newBuilder()
            .setPlayerConnected(PlayerConnected.newBuilder().setUuid(uuid))
            .build()

    fun playerDisconnected(uuid: String): TcpMessage =
        TcpMessage.newBuilder()
            .setPlayerDisconnected(PlayerDisconnected.newBuilder().setUuid(uuid))
            .build()

    fun relayError(reason: String): TcpMessage =
        TcpMessage.newBuilder()
            .setRelayError(RelayError.newBuilder().setReason(reason))
            .build()

    fun forwardToHost(roomId: Int, sendingUuid: String, data: ByteArray, rtt: Int = 0): TcpMessage =
        TcpMessage.newBuilder()
            .setForwardToHost(
                ForwardToHost.newBuilder()
                    .setRoomId(roomId)
                    .setSendingUuid(sendingUuid)
                    .setData(ByteString.copyFrom(data))
                    .setClientToRelayRtt(rtt)
            )
            .build()

    fun forwardToClient(roomId: Int, targetUuid: String?, data: ByteArray): TcpMessage =
        TcpMessage.newBuilder()
            .setForwardToClient(
                ForwardToClient.newBuilder()
                    .setRoomId(roomId)
                    .setTargetUuid(targetUuid ?: "")
                    .setData(ByteString.copyFrom(data))
            )
            .build()

    fun forwardToAllClientsTcp(roomId: Int, data: ByteArray): TcpMessage =
        TcpMessage.newBuilder()
            .setForwardToAllClientsTcp(
                ForwardToAllClientsTcp.newBuilder()
                    .setRoomId(roomId)
                    .setData(ByteString.copyFrom(data))
            )
            .build()

    fun encryptedFrame(iv: ByteArray, ciphertext: ByteArray): TcpMessage =
        TcpMessage.newBuilder()
            .setEncryptedFrame(
                EncryptedFrame.newBuilder()
                    .setIv(ByteString.copyFrom(iv))
                    .setCiphertext(ByteString.copyFrom(ciphertext))
            )
            .build()

    fun lanKeyExchange(serverXy: ByteArray, clientXy: ByteArray): TcpMessage =
        TcpMessage.newBuilder()
            .setLanKeyExchange(
                LanKeyExchange.newBuilder()
                    .setServerXy(ByteString.copyFrom(serverXy))
                    .setClientXy(ByteString.copyFrom(clientXy))
            )
            .build()

    fun keepAlive(): TcpMessage =
        TcpMessage.newBuilder().setKeepAlive(KeepAlive.newBuilder()).build()

    // --- UDP message builders ---

    fun udpForwardUnencrypted(data: ByteArray): UdpMessage =
        UdpMessage.newBuilder()
            .setForwardToAllClientsUdpUnencrypted(
                ForwardToAllClientsUdpUnencrypted.newBuilder().setData(ByteString.copyFrom(data))
            )
            .build()

    fun udpKeepAlive(): UdpMessage =
        UdpMessage.newBuilder().setKeepAlive(KeepAlive.newBuilder()).build()

    fun udpRegistration(roomId: Int, uuid: String, connId: Long): UdpMessage =
        UdpMessage.newBuilder()
            .setUdpRegistration(
                UdpRegistration.newBuilder()
                    .setRoomId(roomId)
                    .setUuid(uuid)
                    .setConnId(connId)
            )
            .build()

    fun lanDiscoverRequest(): UdpMessage =
        UdpMessage.newBuilder()
            .setLanDiscoverRequest(LanDiscoverRequest.newBuilder())
            .build()

    fun lanDiscoverResponse(
        playerCount: Int, maxPlayers: Int, mapName: String, tcpPort: Int, udpPort: Int
    ): UdpMessage =
        UdpMessage.newBuilder()
            .setLanDiscoverResponse(
                LanDiscoverResponse.newBuilder()
                    .setPlayerCount(playerCount)
                    .setMaxPlayers(maxPlayers)
                    .setMapName(mapName)
                    .setTcpPort(tcpPort)
                    .setUdpPort(udpPort)
            )
            .build()
}
