# Source archive and release checklist

This document is a checklist for preparing a source release. It does not imply that a release is ready or that checks have been run.

## Confirm the source checkpoint

- [ ] Start from a user-confirmed stable checkpoint.
- [ ] Verify `versionCode` and `versionName` in `app/build.gradle.kts`.
- [ ] Confirm that the archive contains the expected project root and app sources.
- [ ] Confirm that no files from another project/version were mixed in.

## Keep in the source archive

- Gradle build scripts and `gradle.properties`
- Android manifest
- App Kotlin sources
- Unit tests
- Documentation (`README.md`, `docs/`, contributor instructions)
- `.gitignore`

## Exclude local and generated content

- [ ] `.gradle/`
- [ ] `.idea/` user-specific state (retain shared IDE config only if intentionally curated)
- [ ] `local.properties`
- [ ] `build/` and `app/build/`
- [ ] APK/AAB outputs
- [ ] Logs, captures, temporary exports, and device-specific diagnostics unless intentionally included as sanitized research material

## Build reproducibility

The current project archive does not include Gradle Wrapper scripts or wrapper distribution files. For now, open the project with Android Studio and use its compatible Gradle setup. Before adding a wrapper, verify the required Gradle version against Android Gradle Plugin 8.5.2 and test it from a clean checkout.

## Pre-publication checks

- [ ] README accurately reflects the current tested state.
- [ ] Protocol notes distinguish confirmed observations from unknowns.
- [x] MIT License selected and included. [ ] Confirm and finalize copyright attribution before public publication.
- [ ] No personal data, local paths, credentials, or unsanitized logs are included.
- [ ] ZIP integrity passes (`unzip -t` or equivalent).
- [ ] File inventory reviewed.
- [ ] Android Studio build performed from a clean extraction.
- [ ] Hardware/BLE checklist performed for changes that affect BLE behavior.
- [ ] Release notes list only actual changes and tests.

## Release notes template

### Version

### Changes

### Compatibility

### Validation

### Known limitations
