package com.bombbird.terminalcontrol2.networking

import java.util.UUID

/** Meta information to represent a connection from a player */
class ConnectionMeta(val uuid: UUID, var returnTripTime: Int = 0)