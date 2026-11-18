package com.jetsport.ft10csync.device

import java.util.UUID

/**
 * Reference implementation for JetSport FT-10C.
 *
 * UUIDs and advertised-name matching are based on observed FT-10C hardware.
 * Capabilities list only functions currently implemented by this application.
 */
object Ft10cDeviceProfile : BraceletDeviceProfile {
    override val modelId: String = "jetsport-ft-10c"
    override val displayName: String = "JetSport FT-10C"

    override val serviceUuid: UUID =
        UUID.fromString("000055ff-0000-1000-8000-00805f9b34fb")
    override val writeCharacteristicUuid: UUID =
        UUID.fromString("000033f1-0000-1000-8000-00805f9b34fb")
    override val notifyCharacteristicUuid: UUID =
        UUID.fromString("000033f2-0000-1000-8000-00805f9b34fb")

    override val capabilities: Set<DeviceCapability> = setOf(
        DeviceCapability.TIME_SYNC,
        DeviceCapability.USER_PROFILE,
        DeviceCapability.STEP_HISTORY,
        DeviceCapability.BATTERY,
        DeviceCapability.HEART_RATE,
        DeviceCapability.BLOOD_PRESSURE
    )

    override fun matchesAdvertisement(
        name: String?,
        advertisedServiceUuids: Set<UUID>
    ): Boolean {
        val normalizedName = name.orEmpty()
        val hasModelToken = Regex("(^|[^A-Z0-9])FT-10C($|[^A-Z0-9])")
            .containsMatchIn(normalizedName.uppercase())
        return hasModelToken || serviceUuid in advertisedServiceUuids
    }
}
