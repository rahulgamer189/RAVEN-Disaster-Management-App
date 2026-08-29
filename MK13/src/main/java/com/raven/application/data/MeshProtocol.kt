package com.raven.application.data

import java.util.UUID

/**
 * Shared mesh wire-contract constants. Keeping these in one place prevents the
 * UI, ViewModel, and Bluetooth service from drifting apart.
 */
object MeshProtocol {
    const val CHAT = "CHAT"
    const val SOS = "SOS"
    const val BROADCAST = "BROADCAST"
    const val LOCATION = "LOCATION"
    const val CAMP_UPSERT = "CAMP_UPSERT"

    val serviceUuid: UUID = UUID.fromString("8ce255c0-200a-11ee-be56-0242ac120002")
}
