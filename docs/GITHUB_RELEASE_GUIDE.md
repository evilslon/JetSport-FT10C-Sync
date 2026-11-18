# GitHub repository setup and release guide

## Suggested repository metadata

- **Repository name:** `JetSport_FT10C_Sync`
- **Description:** Android app for local BLE synchronization, time setting, activity history and diagnostics for the JetSport FT-10C bracelet.
- **Visibility:** Public (only after the owner is comfortable publishing the source)
- **Default branch:** `main`
- **License:** MIT
- **Topics:** `android`, `kotlin`, `bluetooth-le`, `ble`, `fitness-tracker`, `smartband`, `jetsport`, `ft-10c`

## Repository features

Recommended initially:
- Issues: enabled
- Releases: enabled
- Discussions: disabled until a community exists
- Wiki: disabled; documentation lives in `README.md` and `docs/`

## Suggested first release

Use the stable, user-tested baseline **v0.5.3.27**. Treat the first public tag as a source publication, not as a promise of universal device compatibility.

Suggested tag:
`v0.5.3.27`

Suggested release title:
`JetSport FT-10C Sync v0.5.3.27 — Stable BLE baseline`

Release description should state:
- The baseline was user-tested on Huawei Nova 10 SE / Android 13.
- Confirmed features and test checklist are listed in the README.
- Protocol notes are reverse-engineered observations, not an official device specification.
- BLE behavior may vary by device/firmware/Android implementation.
- SpO2 is not implemented.
- Android Studio build and device testing must be described only if performed for the release being published.

## Source vs APK publication

For the initial GitHub release, publish:
1. **Source ZIP** — clean source snapshot, no local IDE state or build artifacts.
2. **APK** — only after a fresh build is completed and the APK is installed and tested on a device. Do not upload an APK from an unverified or mismatched source folder.

## Before making the repository public

- [ ] Confirm all source files are authored by the project owner or appropriately licensed.
- [ ] Review dependency licenses and attribution requirements.
- [ ] Review protocol captures and logs for personal/device-specific information.
- [ ] Check that no secrets, signing keys, local paths, or `local.properties` are present.
- [ ] Confirm LICENSE attribution line is appropriate for the actual copyright holder.
- [ ] Verify archive inventory and ZIP integrity.
- [ ] Perform a clean Android Studio build from extracted source.
- [ ] Run hardware tests for the release.
- [ ] Create GitHub repository and upload the source.
- [ ] Create tag and release notes only after checks are complete.

## Important copyright/ownership note

The MIT license in this package uses the attribution placeholder `JetSport FT-10C Sync contributors`. Before public release, replace this with the actual copyright holder name (or a suitable project/entity name confirmed by the owner). Do not claim rights over third-party code or dependencies.
