# Foreground Service migration — Step 16

## Version

`0.6.0.16-ble-session-runtime` (versionCode 128)

## Scope

Move the complete active BLE GATT session graph out of `MainActivity` into an application-owned runtime. This step prepares a clean ownership boundary for the later Foreground Service migration. It does not add a service or change background execution policy.

## Changes

- Added `Ft10cBleSessionRuntime`, constructed by `AppCore` with `applicationContext` and the main-thread `Handler`.
- Moved ownership of `Ft10cBleSessionController` / `Ft10cGattClient`, `Ft10cGattCallbackRouter`, `Ft10cGattSessionEventHandler`, `Ft10cConnectionCoordinator`, `Ft10cGattCommandChannel`, and `Ft10cCommandOperations` into the runtime.
- Moved active FT-10C profile state and post-subscription connection workflow scheduling into the runtime.
- Moved command completion and time-sync status reporting to immutable `BleUiEvent` values. Permission requests are also emitted as events; the Activity remains responsible for showing the Android permission prompt.
- Made runtime shutdown cancel retries, queued commands and connection deadlines before closing the GATT session and resetting shared session state.
- `MainActivity` now calls the runtime facade and renders events. It no longer stores a GATT object or constructs the connection/command graph.
- Activity recreation no longer disconnects the GATT session. The `AppCoreViewModel` retains `AppCore` through configuration changes; terminal AppCore cleanup closes the runtime.

## Preserved behavior

- FT-10C profile validation still precedes model-specific commands.
- CCCD descriptor-write confirmation still gates the post-connect workflow.
- A9 profile, A3 time, B2 FA history, and A2 battery operations still use the same command coordinators and shared serialized GATT queue.
- Packet formats, command order, busy checks, retry delays, callback timeouts, queue sequencing, GATT callback identity checks, and notification decoding were not intentionally changed.
- The scan coordinator remains separate and Activity-owned in this step.
- No SpO2 support or standard 0x2A19 battery fallback was introduced.

## Ownership boundary and limitations

The runtime is independent of `MainActivity` and receives no Activity or UI references. `AppCoreViewModel` is still the current lifetime holder. This is not a Foreground Service and does not guarantee session survival after the task is finished or the process is killed. The next stage can make a service the explicit runtime owner and move scanning/adapter monitoring if required.

## Validation

- Source audit: `MainActivity` has no `BluetoothGatt` field, `connectGatt` call, session-controller reference, callback-router reference, connection-coordinator reference, command-channel reference, or command-operations reference.
- Source audit: `Ft10cBleSessionRuntime` contains no Activity, Compose, `runOnUiThread`, or Activity callback references.
- The existing command coordinators and protocol/queue implementation files were left unchanged.
- ZIP integrity and source-structure checks passed.
- Android Gradle compilation and JVM unit tests could not run in this environment: Gradle Wrapper 9.6.0 could not download from `services.gradle.org` because network DNS access was unavailable. Build and device verification are therefore still required in Android Studio.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
