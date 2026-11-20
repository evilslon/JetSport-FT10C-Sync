# Phone Finder — Step 23 (0.6.0.23)

## Evidence

The FT-10C Phone Finder activation was repeatedly observed in nRF Connect as an incoming two-byte notification `D1 0A` on characteristic `000033F2-0000-1000-8000-00805F9B34FB` (service `000055FF-0000-1000-8000-00805F9B34FB`).

## Implementation

- Decode only the exact two-byte `D1 0A` payload on `0x33F2` as `PhoneFinderActivated`.
- Forward the event through the application BLE event stream; the BLE foreground service owns alert playback so it can continue while the Activity is not visible.
- Sound and vibration are independently configurable and persisted in settings. Both default to enabled.
- Stop is available in the Settings screen and as an action on the ongoing BLE foreground-service notification.
- Stop the alert when the BLE connection is lost or the BLE runtime is closed.
- No `0x33F1` command is sent for Phone Finder. The incoming `D1 0A` is never echoed back.

## Explicit limitations

- `D1 0A` is an observed activation marker. The command that causes the bracelet to emit it, and any distinct deactivation packet, remain unknown.
- The first implementation uses a repeating system alarm-tone beep rather than a user-selected ringtone. A ringtone picker can be added after this basic event path is validated on the target phone.
- Android Do Not Disturb, alarm volume, OEM battery/background policies, and notification permission can affect alert audibility or the notification stop action.
- Full Android Gradle build and unit-test execution were not possible in this environment because the configured Gradle 9.6.0 distribution is not cached and network access is unavailable.
