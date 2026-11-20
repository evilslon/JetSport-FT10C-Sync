# Changelog

## 1.0.0 — 2026-11-20

Prepared v1.0.0 release candidate of JetSport FT-10C Sync, promoted from the user-tested `0.6.0.26` checkpoint. GitHub publication is pending owner review.

### Features included
- Local FT-10C BLE scanning, explicit connection, GATT session management, and serialized command handling.
- Manual and periodic time synchronization with 30-minute, 3-hour (default), 6-hour, and 12-hour intervals.
- Live step totals and validated step-history synchronization with local persistent storage.
- FT-10C-specific battery percentage via the proprietary A2 response.
- Heart-rate and blood-pressure notifications where supported by the bracelet.
- Locally calculated distance and calorie estimates based on the user profile.
- Phone Finder triggered by the observed incoming `D1 0A` notification, with selectable Android ringtone, silent mode, vibration, foreground dialog stop control, and notification stop action.
- Russian and English interface, configurable date format, and bounded diagnostic journal with export.

### Release preparation
- Updated release metadata, architecture/protocol documentation, and English/Russian user guides.
- No BLE feature logic changes were intended as part of the 1.0.0 promotion.

### Known limitations
- Tested with one FT-10C bracelet and Huawei Nova 10 SE (Android 13); compatibility with other hardware/Android combinations is not established.
- SpO2 synchronization is not implemented.
- Distance and calories are estimates; health measurements are not medical-grade.
- This repository release contains source code, not a signed production APK.
