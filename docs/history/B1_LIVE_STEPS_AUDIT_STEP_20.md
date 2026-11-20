# B1 live-step packet validation — 0.6.0.20

Version: `0.6.0.20-b1-packet-validation` (versionCode 132)

## Audit findings

- B1 is decoded from notifications on `000033F2-0000-1000-8000-00805F9B34FB`.
- The established fields are year (big-endian), month, day, hour, and a big-endian 16-bit absolute step total.
- The app stores one row per date/hour and replaces the previous total when another B1 update for that hour arrives; it does not sum successive B1 notifications.
- The uploaded 2026-11-20 capture showed B1 progressing from 10 to 32 steps for 11:00. The completed B2 history transfer contained 32 steps for the same hour. This confirms agreement for that observed hour, not a universal guarantee.
- The trailing bytes in the 18-byte B1 packet remain undocumented and are intentionally not decoded.

## Change

The B1 decoder now rejects invalid calendar dates and hours before creating a `LiveSteps` event. This prevents malformed date fields from being normalized by lenient `Calendar` conversion, persisted under an inconsistent date key, or rejected later by `ActiveDayState` on the UI event path.

The existing 8-byte minimum, B1 field interpretation, database upsert behavior, B2 history parser, GATT callback path, command queue, and refresh cadence are unchanged.

## Tests added

- Captured full-length 18-byte B1 packet is decoded; trailing bytes remain uninterpreted.
- Impossible non-leap-year date is rejected.
- Valid leap day is accepted.
- Out-of-range month and hour are rejected.
- Year zero and day zero are rejected.

## Verification status

Static source checks are complete. Android Gradle unit tests/build and FT-10C device verification remain to be performed in Android Studio.
