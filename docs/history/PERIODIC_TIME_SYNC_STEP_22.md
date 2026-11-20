# Step 22 — Periodic automatic time synchronization

Base: `0.6.0.21-b1-b2-merge-guard`
Version: `0.6.0.22-periodic-time-sync`

## Behavior

- Automatic time synchronization still runs after the verified connection workflow and profile upload.
- After each completed A3 BLE write callback (including an error/timeout callback), exactly one next periodic task is scheduled.
- Supported intervals: 30 minutes, 3 hours, 6 hours, and 12 hours. The default is 3 hours.
- The selected interval is persisted in `SharedPreferences`. Invalid stored or requested values fall back to 3 hours.
- The periodic task is owned by the application-scoped BLE runtime/coordinator, not by an Activity or Compose screen.
- Disconnect and disabling automatic mode cancel the periodic task. Disabling automatic mode also cancels a pending time-command retry.
- If the GATT command path is busy when a periodic task fires, the existing time-command retry mechanism retries after 2 seconds; no additional periodic timer is created until the write callback completes.
- A manual time write in automatic mode also restarts the periodic interval after its BLE callback.
- The existing A3 packet format and GATT write queue are unchanged. A BLE write callback confirms the Android BLE operation only; the bracelet's displayed clock is not independently acknowledged by this protocol.

## Files changed

- `SettingsRepository.kt`, `Ft10cTimeSyncIntervals.kt` — persisted interval, one source of truth for supported values, validation, and delay conversion.
- `Ft10cSyncCoordinator.kt` — one-shot periodic scheduler and cancellation.
- `Ft10cTimeCommandCoordinator.kt` — permits subsequent automatic A3 writes during the same connection.
- `Ft10cBleSessionRuntime.kt` — schedules the next interval after time-write completion and responds to setting changes.
- `MainActivity.kt`, `JetSportComposeHost.kt`, `DeviceUiState.kt` — settings UI and runtime wiring.
- `app/build.gradle.kts` — version `0.6.0.22`.

## Validation status

Source-level inspection completed. Android build and unit tests could not be run in this environment because the Gradle wrapper distribution `gradle-9.6.0` is not cached and network access to `services.gradle.org` is unavailable. Build and device validation remain required in Android Studio.
