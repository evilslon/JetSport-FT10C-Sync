# BLE Runtime Extraction Plan

## Purpose

Prepare the verified FT-10C BLE implementation for a real Android Foreground Service. This is not a cleanup exercise. The objective is to make the BLE session, its GATT callbacks, command queue, and connection workflow independent of `MainActivity` before changing their lifecycle owner.

## Current ownership confirmed in source (0.6.0.16)

`AppCore` owns `Ft10cBleSessionRuntime`, constructed with `applicationContext` and the main-thread `Handler`. The runtime owns:

- `Ft10cBleSessionController` and its `Ft10cGattClient`;
- `Ft10cGattSessionEventHandler` and `Ft10cGattWriteEventHandler` routing;
- `Ft10cGattCallbackRouter`;
- `Ft10cGattCommandChannel` and the single serialized `Ft10cCommandQueue`;
- `Ft10cConnectionCoordinator` and its connect/service-discovery deadlines;
- `Ft10cCommandOperations` and the four existing command coordinators;
- active FT-10C profile validation and connection workflow scheduling;
- transport/session cleanup and immutable event publication.

`MainActivity` no longer constructs, stores, or closes a `BluetoothGatt`, session controller, callback router, connection coordinator, command channel, or command operations object. It calls the runtime's narrow operation methods and collects `BleUiEvent` values for rendering. Permission prompts and localized presentation remain Activity responsibilities, requested through an immutable event rather than an Activity callback retained by BLE code.

The BLE runtime contains no `Activity`, Compose, `runOnUiThread`, screen-controller, or Activity-method references. The scan coordinator remains Activity-owned for now; it is separate from the active GATT session and can be migrated independently if later service requirements call for it.

`AppCoreViewModel` retains `AppCore` across Activity configuration recreation. Terminal AppCore cleanup invokes runtime shutdown, cancels command retries/queue work and connection deadlines, closes GATT, and resets session state. This step is an ownership boundary, **not** a Foreground Service: it does not guarantee survival after the Activity/task is finished or the process is killed.

## Remaining boundary before Foreground Service

1. `Ft10cScanCoordinator` and Bluetooth adapter-state monitoring are still Activity-owned.
2. Runtime lifetime is currently retained by `AppCoreViewModel`; the next service step must make the service the explicit runtime owner and define binding/client behavior.
3. Android 13+ notification permission and the appropriate connected-device foreground-service declaration/permission still need to be implemented and verified on the Huawei device.

## Safe implementation sequence

### Stage 1 — Introduce application-level BLE event/state contracts

- Define immutable event/state models without Android View/UI types.
- Move notification decoding and repository writes behind an application-level event consumer.
- Keep the current Activity as the collector/renderer.
- Preserve exact protocol bytes, GATT callback ordering, queue timeout values, CCCD gating, and command sequencing.
- No change to who closes GATT in this stage.

### Stage 2 — Extract connection workflow operations

- Move profile transfer, time sync, history request, and A2 battery request implementations out of Activity into an application-level operation facade.
- Inject repositories/coordinators and the runtime command channel.
- Replace Activity callbacks with operation facade calls.
- Keep localized UI strings and screen state in Activity.

### Stage 3 — Introduce `Ft10cBleSessionRuntime` (completed in 0.6.0.16)

- Construct the full callback graph in one place using application context.
- Move the connection coordinator and timeout ownership into the runtime, exposing events for UI failures.
- Make the runtime the only owner of the session controller and command channel.
- Existing focused tests cover the command queue, connection/session lifecycle, and transport cleanup. Runtime-level Android integration checks remain required on a device after a successful build.

### Stage 4 — Add Foreground Service ownership

- Service obtains/creates the application runtime and calls `startForeground` with a persistent notification.
- Activity binds or communicates through a service/client interface; it never owns GATT.
- Activity destruction must not disconnect the session.
- Service shutdown must explicitly disconnect and close GATT and cancel all pending work.
- Add Android 13+ notification permission handling and the appropriate connected-device foreground-service declaration/permission for the app's target SDK. Verify Android-version-specific behavior on the Huawei device.

## Non-negotiable regression checks

- FT-10C profile validation still occurs before model-specific commands.
- CCCD write success still gates the connection workflow.
- A9 profile transfer, A3 time write, B2 FA history request, and A2 battery request retain their existing order and conditions.
- Command channel remains serialized; no parallel GATT characteristic writes are introduced.
- GATT callback identity checks remain intact.
- Disconnect cancels pending commands, sync/battery/history work and timeouts exactly once.
- Reconnect resets session state and restores subscriptions before sync workflow.
- No code path in the runtime references `Activity`, `runOnUiThread`, Compose state, screen controllers, or Activity method references.

## Explicit non-goals for this migration

- No protocol redesign or new FT-10C commands.
- No SpO2 implementation.
- No change to battery source (A2 remains authoritative; standard 0x2A19 remains untrusted).
- No broad package renaming or cosmetic cleanup.
- No APK build claims until the project is built and tested in the user's Android Studio environment.
