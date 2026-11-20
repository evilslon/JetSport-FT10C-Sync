# JetSport FT-10C Sync v1.0.0

First stable public release candidate, promoted from the user-tested `0.6.0.26` feature checkpoint.

## Highlights
- Local BLE operation without JetSport cloud authentication.
- Manual and periodic time synchronization (30 minutes, 3 hours by default, 6 hours, or 12 hours).
- Live steps, synchronized step history, local history database, distance/calorie estimates, battery, heart-rate and blood-pressure notifications.
- Phone Finder with Android ringtone picker, silent option, independent sound/vibration controls, foreground stop dialog and background notification stop action.
- English/Russian interface, selectable date format, persistent settings, bounded technical journal and export.

## Validation
- `:app:assembleDebug` completed successfully on the project owner's Windows environment.
- `:app:testDebugUnitTest`: 128 tests passed, 0 failed.
- The resulting APK was installed on Huawei Nova 10 SE running Android 13; the user confirmed the application works with the FT-10C bracelet.
- The build emitted a Kotlin daemon compatibility error under JDK 25, then successfully used the fallback compiler. JDK 17 is the recommended Gradle JVM; this environment warning is documented and has not been represented as fixed.

## Limitations
- Compatibility confirmed only with the tested FT-10C and Huawei Nova 10 SE / Android 13; other hardware and Android versions are unverified.
- SpO2 synchronization is not supported.
- Distance and calorie values are estimates; health readings are not medical-grade.
- This source archive does not include a signed production APK.
- Independent community project, not affiliated with JetSport or the bracelet manufacturer.
