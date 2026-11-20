# Step 19 — Battery state restoration

Version: `0.6.0.19-battery-state-restoration` (versionCode 131)

## Problem

The BLE foreground service can keep the GATT session alive while the Activity is destroyed. A newly created `DashboardScreenController` previously started with `batteryPercent = null`. The A2 refresh policy correctly skipped another request when a valid response was received less than 30 minutes earlier, leaving the battery card as a dash.

## Change

- Persist the last valid proprietary A2 percentage in `SettingsRepository`.
- Save the percentage whenever `Ft10cNotificationCoordinator` receives a valid decoded A2 notification.
- Initialize each new dashboard controller with the persisted percentage.
- Keep the existing 30-minute A2 request throttle unchanged.
- Keep step history in the existing local database; no extra B2 request is added on Activity recreation.

## Expected behavior

Reopening the Activity while the BLE service remains connected immediately displays the last known battery percentage. The next A2 request still follows the existing refresh policy (or can be forced manually). A genuinely new GATT connection continues to run the established A9/A3/B2/A2 connection workflow.

## Validation

Static source/ZIP checks performed. Full Android Gradle build and device test remain to be completed by the project owner.
