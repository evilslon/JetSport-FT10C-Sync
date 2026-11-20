# User guide

## First connection

1. Install the app on an Android phone and grant the Bluetooth permissions requested by Android.
2. Turn on the FT-10C bracelet and Bluetooth on the phone.
3. Open **Device**, scan for the bracelet, select the FT-10C entry, and press **Connect**.
4. Keep the app's foreground-service notification available while the bracelet is connected.

The app communicates locally over BLE. A JetSport account or manufacturer cloud login is not required.

## Today and measurements

The Today screen shows the current day's steps, bracelet battery, estimated distance, and estimated calories. Heart-rate and blood-pressure measurements are shown when the bracelet sends supported notifications. New measurements and synchronized step history are stored locally on the phone.

Distance and calories are estimates calculated from the saved profile and activity data. They are not direct measurements. Health metrics are informational and are not medical-grade readings.

## Time synchronization

Time can be synchronized manually from Settings. Automatic synchronization can be enabled and configured for 30 minutes, 3 hours (default), 6 hours, or 12 hours. The first automatic synchronization occurs as part of the connection workflow after profile transfer. A manual synchronization restarts the next automatic-sync countdown.

The app sends the phone's current local date and time. The bracelet's `A3` response is a packet echo; it is not an independent reading of the bracelet clock.

## Phone Finder

Use the bracelet's **Find Phone** function while it is connected to the app. The bracelet sends an activation notification and the phone starts the configured alert. In Settings you can:

- Enable or disable sound.
- Enable or disable vibration.
- Choose a ringtone in the Android system ringtone picker, including **Silent**.

When the alert starts while the app is open, a dialog appears over the current screen with a stop button. When the app is in the background, use **Stop alert** in the JetSport foreground-service notification. Disconnecting the bracelet also stops the alert.

## Language and date format

The interface supports Russian and English. The date display format can be selected independently:

- `DD.MM.YYYY` — day, month, year.
- `MM.DD.YYYY` — month, day, year.

The settings screen localizes the format examples for the selected interface language.

## Diagnostic journal

The technical journal is intended for troubleshooting. It is bounded to 500 records and 256 KiB; older entries are removed automatically. If support is needed, export the journal from Settings and review it before sharing, because diagnostic logs may contain device and connection details.

## Known limitations

- SpO2 synchronization is not implemented.
- The standard BLE Battery Service value is unreliable on the tested FT-10C; the app uses the proprietary A2 response.
- Bracelet history storage is limited; the app's local database is intended to retain synchronized records on the phone.
- Compatibility has been confirmed only on the tested FT-10C and Huawei Nova 10 SE / Android 13. Other combinations need testing.
