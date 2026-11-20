# Foreground Service migration — Step 17

## Version

`0.6.0.17-ble-foreground-service` (versionCode 129)

## Scope

Make the Android connected-device Foreground Service the lifecycle owner of the existing `AppCore` and its `Ft10cBleSessionRuntime`. The Activity binds to the service and continues to present the same application state; the service is promoted to foreground immediately before a user-requested GATT connection.

## Changes

- Added `Ft10cBleForegroundService` with a local Binder, persistent low-importance notification channel, and `connectedDevice` foreground-service type.
- Added `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_CONNECTED_DEVICE` manifest permissions. Android 13+ is asked for notification permission on the first connection attempt; a denial does not block BLE work Android may show the FGS notification only in its task manager when permission is denied. The non-exported service is registered with `stopWithTask=false`.
- `MainActivity` starts and binds the service while visible. The service adopts the process-wide `AppCore` instance; `AppCoreViewModel` no longer closes it after ownership has transferred.
- Added `AppCoreRegistry` so a newly created Activity can obtain the same `AppCore` while the service continues running after the previous Activity has been removed.
- Before `Ft10cBleSessionRuntime.connect`, the Activity asks the bound service to enter foreground mode. The service then displays connecting/connected status and removes the notification after disconnect/failure.
- The service keeps running after the Activity/task is removed while a GATT session is active. If the session later disconnects with no Activity bound, the service stops and releases `AppCore`.
- Heart-rate and blood-pressure persistence now occurs in `Ft10cNotificationCoordinator`, not in the Activity event collector. This prevents missed HR/BP history entries while the Activity is absent and avoids duplicate inserts while it is visible.
- Scanning and Bluetooth adapter monitoring remain Activity-owned; no scan/background polling behavior was added.

## Preserved behavior

- Existing GATT runtime, callback routing, FT-10C profile validation, CCCD sequencing, A9/A3/B2/A2 command coordinators, serialized GATT queue, decoders, retries, and timeouts were not intentionally changed.
- No packet bytes or command order were changed.
- No standard 0x2A19 battery fallback or SpO2 support was introduced.

## Lifecycle behavior

- Activity recreation: the started service and process-wide `AppCore` survive; the new Activity binds to the same runtime.
- User closes Activity while connected: the Activity unbinds; the connected-device service and notification remain active.
- Bracelet disconnects while no Activity is bound: the service removes its notification, stops itself, and closes the runtime.
- User disconnects while Activity is visible: foreground notification is removed, but the service remains available for another connection until the Activity finishes.
- Process death: no guarantee of automatic GATT session restoration is made. The service is `START_NOT_STICKY`; a new app process requires a new user-initiated connection.

## Validation status

- Source-level checks and ZIP integrity are required for this archive.
- Android Gradle build, JVM tests, and real-device lifecycle tests remain to be run by the project owner in Android Studio.
- Device testing must specifically cover: connect, background app, remove Activity from recents, reopen app while connected, disconnect from bracelet while app is closed, reopen after service exit, manual disconnect/reconnect, and Android notification permission denied/granted behavior.

## Important limitation

The Foreground Service improves survival while the Android process remains alive; it cannot guarantee survival after process termination, force-stop, Bluetooth being disabled, permission revocation, or OEM power-management intervention.
