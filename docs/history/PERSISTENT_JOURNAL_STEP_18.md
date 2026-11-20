# Persistent diagnostic journal — Step 18

## Version

`0.6.0.18-persistent-diagnostic-journal` (versionCode 130)

## Scope

Persist the bounded technical journal across Activity recreation, BLE service shutdown, and ordinary app process restarts without changing BLE runtime ownership or protocol behavior.

## Changes

- Added `TechnicalLogPersistence` as a small persistence boundary for `TechnicalLogStore`.
- Added `SharedPreferencesTechnicalLogPersistence`, using app-private preferences and asynchronous `apply()` writes.
- `TechnicalLogStore` restores its snapshot at construction and reapplies current record/byte limits to restored data.
- Kept the limits at 500 records and 256 KiB of UTF-8 text; oldest entries are evicted first.
- Routed Activity logging through `AppCore.appendTechnicalLog`, unifying Activity and background BLE journal entries and timestamps.
- Added unit tests for restoration after store recreation, trimming restored content, and UTF-8 byte limits after persistence.

## Preserved behavior

- No BLE runtime, Foreground Service, GATT queue, command coordinator, protocol packet, retry, or timeout changes.
- Existing TXT export continues to export the current bounded snapshot.
- No new permissions or third-party dependencies.

## Persistence characteristics and limitations

- The journal is stored in app-private SharedPreferences. It is not uploaded or shared.
- `apply()` updates the in-process preference state immediately and writes asynchronously. This avoids synchronous disk writes on the UI/BLE logging path.
- A sudden power loss or process termination before the pending asynchronous disk write completes can theoretically lose the most recent entries; this is a diagnostic journal, not a crash-proof audit log.
- Clearing app data or uninstalling the app removes the journal.

## Validation status

- Unit tests added; run `:app:testDebugUnitTest` in Android Studio.
- Confirm Android build from a clean extraction.
- Device test: generate entries, close Activity, reopen and confirm earlier entries remain; disconnect/stop the service and reopen; generate more than 500 records or exceed 256 KiB and confirm oldest entries are removed; export and inspect TXT.
