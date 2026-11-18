package com.jetsport.ft10csync.device

import java.util.UUID

/**
 * Describes a supported JetSport device without exposing model-specific protocol
 * details to the common scanner/UI. New models should provide their own profile.
 *
 * This interface is intentionally descriptive in this first migration step:
 * it does not authorize commands to an unverified device.
 */
interface BraceletDeviceProfile {
    val modelId: String
    val displayName: String
    val serviceUuid: UUID
    val writeCharacteristicUuid: UUID
    val notifyCharacteristicUuid: UUID
    val capabilities: Set<DeviceCapability>

    fun matchesAdvertisement(name: String?, advertisedServiceUuids: Set<UUID>): Boolean

    fun matchesGatt(discoveredServices: Map<UUID, Set<UUID>>): Boolean {
        val characteristics = discoveredServices[serviceUuid] ?: return false
        return writeCharacteristicUuid in characteristics &&
            notifyCharacteristicUuid in characteristics
    }
}

enum class DeviceCapability {
    TIME_SYNC,
    USER_PROFILE,
    STEP_HISTORY,
    BATTERY,
    HEART_RATE,
    BLOOD_PRESSURE,
    OXYGEN_SATURATION
}
