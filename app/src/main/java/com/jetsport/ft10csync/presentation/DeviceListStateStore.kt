package com.jetsport.ft10csync.presentation

/**
 * Pure in-memory state for discovered BLE rows.
 *
 * This store owns presentation data only; scanning, BluetoothDevice instances,
 * selection commands and GATT connection ownership remain outside it.
 */
class DeviceListStateStore {
    private val rows = linkedMapOf<String, DeviceUiRow>()

    fun upsert(
        address: String,
        name: String,
        rssi: Int,
        selectedAddress: String?,
        connectedAddress: String?
    ) {
        rows[address] = DeviceUiRow(
            address = address,
            name = name,
            rssi = rssi,
            selected = address == selectedAddress,
            connected = address == connectedAddress
        )
    }

    fun refresh(selectedAddress: String?, connectedAddress: String?) {
        rows.replaceAll { address, row ->
            row.copy(
                selected = address == selectedAddress,
                connected = address == connectedAddress
            )
        }
    }

    /** Retain only the selected/connected rows while a new scan is prepared. */
    fun retainAddresses(
        addresses: Set<String>,
        selectedAddress: String?,
        connectedAddress: String?
    ) {
        val retained = addresses.mapNotNull { rows[it] }
        rows.clear()
        retained.forEach { row ->
            rows[row.address] = row.copy(
                selected = row.address == selectedAddress,
                connected = row.address == connectedAddress
            )
        }
    }

    fun clear() = rows.clear()

    fun snapshot(): List<DeviceUiRow> = rows.values.toList()

    fun contains(address: String): Boolean = rows.containsKey(address)

    fun nameFor(address: String): String? = rows[address]?.name
}
