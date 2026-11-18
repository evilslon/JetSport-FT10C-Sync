package com.jetsport.ft10csync.device

/**
 * Central registry of device profiles supported by this build.
 * Unknown devices remain discoverable by the generic BLE scanner but are not
 * assigned a protocol profile and must not receive model-specific commands.
 */
class DeviceProfileRegistry(
    profiles: List<BraceletDeviceProfile> = listOf(Ft10cDeviceProfile)
) {
    private val registeredProfiles = profiles.toList()

    fun findForAdvertisement(
        name: String?,
        advertisedServiceUuids: Set<java.util.UUID>
    ): BraceletDeviceProfile? =
        registeredProfiles.firstOrNull {
            it.matchesAdvertisement(name, advertisedServiceUuids)
        }

    fun findForGatt(
        discoveredServices: Map<java.util.UUID, Set<java.util.UUID>>
    ): BraceletDeviceProfile? =
        registeredProfiles.firstOrNull { it.matchesGatt(discoveredServices) }

    fun all(): List<BraceletDeviceProfile> = registeredProfiles
}
