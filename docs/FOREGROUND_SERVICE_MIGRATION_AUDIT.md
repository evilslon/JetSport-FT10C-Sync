# Foreground Service migration audit

Baseline: `0.6.0.17-ble-foreground-service`

## Goal

The Android connected-device Foreground Service must own the active FT-10C GATT runtime independently of `MainActivity`, while preserving the tested GATT protocol, command serialization, subscription confirmation, timeouts, and history-session rules.

## Current ownership

`AppCoreRegistry` provides one process-wide `AppCore` identity. `AppCoreViewModel` obtains that instance and transfers cleanup responsibility once `Ft10cBleForegroundService` adopts it. The service closes/releases the `AppCore` when its own lifetime ends. A later Activity obtains the same instance from the registry and binds to the still-running service.

`Ft10cBleForegroundService` owns the lifetime of `Ft10cBleSessionRuntime` by adopting the `AppCore`, observing BLE lifecycle events, managing the connected-device foreground notification, and releasing the runtime on service destruction. `MainActivity` requests foreground mode before calling `connect`; it does not create or close the runtime.

The scan coordinator and Bluetooth adapter-state monitor remain Activity-owned. The service does not perform background scanning or periodic polling.

## Android service configuration

- Manifest service type: `connectedDevice`.
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, plus the existing Bluetooth runtime permissions.
- Notification channel: low importance, ongoing notification while GATT work is active.
- `stopWithTask=false` allows the service to continue when the task is removed.
- `START_NOT_STICKY`: process death does not trigger a misleading claim of restored GATT state.

## Remaining validation

1. Build with the configured Android Gradle Plugin/SDK and run all JVM unit tests.
2. Verify foreground-service start on Android 13 / EMUI 13 and notification behavior with notification permission allowed and denied.
3. Connect, background the app, remove Activity from recents, and verify the bracelet connection remains active.
4. Reopen the app while the service is active and verify it binds to the same `AppCore` and reflects the current GATT state.
5. With the app closed, turn the bracelet off and verify the service exits after receiving disconnect.
6. Reopen after service exit and verify a fresh connection can be established.
7. Repeat manual disconnect/reconnect and the A9 profile, A3 time, B2 history, A2 battery, HR/BP/B1 live notification workflow.
8. Verify service cleanup cancels pending GATT work and closes the runtime exactly once.

## Non-goals

- No packet, protocol, queue, retry, or timeout changes.
- No background BLE scanning or periodic polling.
- No automatic reconnect after process death, force-stop, Bluetooth disablement, or permission revocation.
- No replacement of FT-10C battery A2 with standard Battery Service 0x2A19.
- No SpO2 support claim.
