# Architecture overview

This document describes the `v1.0.0` source tree. It is a high-level guide, not a complete API reference.

## Main layers

- **BLE (`ble/`)**: scanning, GATT connection lifecycle, notification subscription, serialized command queue, foreground-session runtime, and command coordination.
- **Device (`device/`)**: FT-10C device profile, capability definitions, and profile matching.
- **Protocol (`protocol/`)**: stateless decoding of notifications, battery packets, and step-history records.
- **Sync (`sync/`)**: time synchronization scheduling, battery refresh policy, and history-transfer state.
- **Data (`data/`)**: persisted settings, user profile, active-day state, measurement database/repository, and bounded technical log.
- **Domain (`domain/`)**: daily activity calculations.
- **Presentation (`presentation/`)**: Compose UI, screen state, view models, and action routing.
- **App core**: `AppCore` composes the runtime services; `AppCoreRegistry` preserves a process-wide core identity.

## Runtime ownership

`Ft10cBleForegroundService` owns the active BLE runtime while a connection is maintained. `MainActivity` binds to the service and drives user-facing screens. The service keeps the BLE session and Phone Finder handling alive when the Activity is backgrounded. A notification exposes a stop action while the Phone Finder alert is active. Disconnecting the bracelet stops the active alert and tears down connection-scoped work.

Scanning is initiated from the app UI. The selected device is connected explicitly by the user; the UI filters the intended FT-10C device profile rather than automatically connecting to arbitrary nearby BLE devices.

## Connection and notification flow

1. Scan for nearby BLE devices and select the FT-10C.
2. Connect over BLE and discover GATT services and characteristics.
3. Enable notifications on the FT-10C notification characteristic and write its CCCD.
4. Treat notification subscription as a confirmed stateful operation; do not start the normal synchronization workflow before the CCCD write callback succeeds.
5. Decode incoming notification bytes and dispatch events to the appropriate measurement, history, battery, time-sync, or Phone Finder handler.
6. Serialize GATT writes through the command queue and track Android write callbacks.
7. Cancel connection-scoped scheduled work and discard incomplete transfers when the connection is lost or replaced.

## Synchronization behavior

### Time

Time can be synchronized manually or automatically. Automatic synchronization runs after the connection/profile workflow and then follows the selected interval: 30 minutes, 3 hours (default), 6 hours, or 12 hours. A manual synchronization restarts the interval countdown. If the GATT queue is busy, scheduled work is retried rather than silently discarded. The app uses the phone's local date and time. An `A3` echo confirms receipt of the packet, but does not independently prove the bracelet's displayed clock changed.

### Battery

The app uses the FT-10C-specific `A2` request/response flow. The last valid percentage is persisted for UI restoration. A refresh interval is advanced only after a valid `A2` response. The standard Battery Service `0x180F/0x2A19` value was observed to be unreliable on the tested bracelet and is not treated as authoritative.

### Step history and live totals

History is requested over the FT-10C protocol and accumulated in a transfer session. Records are committed only after a valid completion marker. A live `B1` update can overlap a `B2` history transfer; merge metadata preserves a newer live total for the same hour without discarding the imported activity breakdown. The app keeps its own local history database, so it can retain records after the bracelet's limited storage rolls over.

### Phone Finder

The app reacts to the observed incoming `D1 0A` notification from the bracelet. This event starts the phone-side alert; the app does not send `D1 0A` back to the bracelet. Sound and vibration can be toggled independently. The ringtone is selected using the Android system ringtone picker, including a silent choice. While the app is foregrounded, a non-dismissible dialog provides a prominent stop control; in the background, the foreground-service notification provides a stop action. A BLE disconnect stops the alert.

## Persistence and diagnostics

User settings and measurements are stored locally. The technical journal is bounded to 500 records and 256 KiB, with older records removed as needed. It is intended for diagnostics and export, not as a user-facing activity feed.

## Reliability boundaries

- The GATT command queue serializes writes and tracks completion callbacks.
- History is committed only after a valid end marker; incomplete transfers are not treated as complete history.
- Invalid live-step packet dates/times are rejected before persistence or UI dispatch.
- Bluetooth OFF handling stops scanning, closes the GATT connection, cancels active synchronization work, and clears incomplete history state.
- Distance and calorie values are estimates calculated locally from the saved user profile and activity data.

## Testing

JVM unit tests cover packet decoders, history parsing, queue/state coordinators, settings, activity calculations, and presentation state. Hardware/Bluetooth integration behavior still requires testing on a real phone and bracelet. The v1.0.0 feature set was confirmed by the project tester on a Huawei Nova 10 SE running Android 13 with an FT-10C bracelet. Other devices and Android versions are not yet established as compatible.
