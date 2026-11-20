# v0.6.0.21 — B1/B2 merge guard

## Audit finding

The database repository serializes writes on one worker, but a complete B2 transfer was committed with `CONFLICT_REPLACE`. If B1 delivered a newer absolute total for an hour while B2 was still streaming, the B1 upsert could be queued before the B2 commit; the later B2 replacement could then overwrite the newer live total. The uploaded 2026-11-20 diagnostic log did not reproduce this ordering: B2 completed before the subsequent B1 notifications.

## Change

- B1 hourly upserts now store a local `live_updated_at` timestamp.
- The accepted B2 request records its local start timestamp.
- On complete B2 commit, a same-hour B1 total received after the B2 request began is retained; B2 run/walk breakdown and raw record are still imported.
- B1 totals older than the B2 request do not override the B2 snapshot.
- SQLite schema version advances from 3 to 4 with a non-destructive column migration.
- Added pure unit tests for newer, equal-time, older, and missing request timestamps.

## Preserved behavior

No BLE packet, GATT operation, command order, retry policy, request interval, history timeout, or activity formula changed. Incomplete B2 transfers still never commit. Unknown packet bytes remain uninterpreted.

## Verification

Source-level review and ZIP integrity checks are performed for this archive. Android Gradle build and full test suite still require execution in Android Studio on the user's machine.
