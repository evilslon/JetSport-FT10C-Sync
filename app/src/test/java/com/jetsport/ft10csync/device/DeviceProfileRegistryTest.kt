package com.jetsport.ft10csync.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileRegistryTest {
    private val registry = DeviceProfileRegistry()

    @Test
    fun recognizesFt10cByAdvertisedName() {
        val profile = registry.findForAdvertisement("FT-10C-93DC", emptySet())
        assertEquals(Ft10cDeviceProfile.modelId, profile?.modelId)
    }

    @Test
    fun recognizesFt10cNameWithPrefixAndCaseDifferences() {
        assertEquals(
            Ft10cDeviceProfile.modelId,
            registry.findForAdvertisement("JetSport ft-10c", emptySet())?.modelId
        )
    }

    @Test
    fun rejectsNameWhereModelTokenIsOnlyPartOfAnotherWord() {
        assertNull(registry.findForAdvertisement("NOTFT-10C-CLONE", emptySet()))
    }

    @Test
    fun recognizesFt10cByAdvertisedServiceUuid() {
        assertEquals(
            Ft10cDeviceProfile.modelId,
            registry.findForAdvertisement(null, setOf(Ft10cDeviceProfile.serviceUuid))?.modelId
        )
    }

    @Test
    fun recognizesFt10cByGattLayoutWithoutBluetoothNameOrMac() {
        val profile = registry.findForGatt(
            mapOf(
                Ft10cDeviceProfile.serviceUuid to setOf(
                    Ft10cDeviceProfile.writeCharacteristicUuid,
                    Ft10cDeviceProfile.notifyCharacteristicUuid
                )
            )
        )
        assertEquals(Ft10cDeviceProfile.modelId, profile?.modelId)
    }

    @Test
    fun rejectsCharacteristicsSplitAcrossDifferentServices() {
        val otherService = java.util.UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val profile = registry.findForGatt(
            mapOf(
                Ft10cDeviceProfile.serviceUuid to setOf(Ft10cDeviceProfile.writeCharacteristicUuid),
                otherService to setOf(Ft10cDeviceProfile.notifyCharacteristicUuid)
            )
        )
        assertNull(profile)
    }

    @Test
    fun rejectsIncompleteGattLayout() {
        assertNull(
            registry.findForGatt(
                mapOf(
                    Ft10cDeviceProfile.serviceUuid to setOf(Ft10cDeviceProfile.writeCharacteristicUuid)
                )
            )
        )
    }

    @Test
    fun doesNotAssignProfileToUnknownDevice() {
        assertNull(registry.findForAdvertisement("Unknown bracelet", emptySet()))
        assertNull(registry.findForGatt(emptyMap()))
    }

    @Test
    fun ft10cCapabilitiesReflectImplementedFeatures() {
        assertTrue(Ft10cDeviceProfile.capabilities.contains(DeviceCapability.TIME_SYNC))
        assertTrue(Ft10cDeviceProfile.capabilities.contains(DeviceCapability.STEP_HISTORY))
        assertTrue(Ft10cDeviceProfile.capabilities.contains(DeviceCapability.BATTERY))
    }
}
