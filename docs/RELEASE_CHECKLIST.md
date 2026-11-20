# v1.0.0 release checklist

## Source and documentation
- [x] Based on the user-tested `0.6.0.26` feature checkpoint.
- [x] Includes the corrected `BleUiEventDispatcherTest` synchronization and accurate dispatcher comment.
- [x] Version name is `1.0.0`; versionCode is `138`.
- [x] README, changelog, release notes, architecture/protocol notes and English/Russian user guides are included.
- [x] GitHub instructions refer to the existing `evilslon/JetSport-FT10C-Sync` repository and preserve its history.
- [ ] Owner confirms the copyright-holder wording in `LICENSE` and third-party attribution.

## Local validation (reported by project tester)
- [x] `:app:assembleDebug` — BUILD SUCCESSFUL.
- [x] `:app:testDebugUnitTest` — 128 tests passed, 0 failed.
- [x] Resulting APK installed on Huawei Nova 10 SE / Android 13 and user confirmed that the app works with FT-10C.
- [ ] Optional: eliminate Kotlin 1.9.24 / JDK 25 daemon incompatibility by ensuring Gradle actually runs on JDK 17. Current build falls back to compilation without the Kotlin daemon and succeeds.
- [ ] Review existing deprecation and compileSdk/AGP warnings; they do not currently prevent the build.

## Existing repository update
- [ ] Confirm the existing `main` checkout is clean and up to date; preserve `.git` and all history.
- [ ] Apply the source update archive at the repository root, not into a nested directory.
- [ ] Review `git status`, `git diff`, and `git diff --check`.
- [ ] Ensure no `local.properties`, signing keys, private logs, build outputs, APKs or IDE caches are staged.

## GitHub publication (owner action)
- [ ] Owner approves public release and license/copyright wording.
- [ ] Commit reviewed changes on the existing `main` branch.
- [ ] Create annotated tag `v1.0.0` on the release commit.
- [ ] Push `main` and `v1.0.0`; preserve existing tags, including `v0.5.3.27`.
- [ ] Create GitHub Release from `v1.0.0` using `RELEASE_NOTES_1.0.0.md`.
- [ ] Attach an APK only if the exact release APK is intentionally distributed and its provenance/signing status is clearly stated.
