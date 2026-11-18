# Contributing

Thanks for your interest in JetSport FT-10C Sync.

## Scope

This project is device-specific and intended for local BLE communication with the JetSport FT-10C bracelet. Changes should preserve the known-working connection and synchronization flows unless a protocol or reliability issue is demonstrated.

## Before changing code

1. Start from the latest user-confirmed stable source archive.
2. Confirm the version name and version code in `app/build.gradle.kts`.
3. Do not modify BLE command bytes or packet interpretation without captured evidence and a reproducible test.
4. Keep unknown protocol fields explicitly unknown.
5. Avoid unnecessary polling and work that could wake the phone or drain bracelet battery.
6. Keep technical logging bounded and avoid repetitive success messages.

## Code changes

- Prefer small, focused changes.
- Keep BLE, protocol, sync, data, domain, and presentation responsibilities separated.
- Do not remove code solely because it appears unused in a text search; verify references, reflection/Android usage, and tests first.
- Add or update unit tests for parsers, decoders, state transitions, coordinators, and pure logic where applicable.
- Do not introduce unrelated UI changes into protocol/reliability fixes.

## Validation

For each change, report clearly which checks were performed:

- Static/source inspection
- Unit tests (if runnable)
- ZIP/source integrity checks
- Android Studio build (must be performed by a developer if not available in the current environment)
- Real-device BLE tests

Never describe an Android Studio build or hardware test as successful unless it was actually executed.

## Suggested device test checklist

- Scan and identify the FT-10C
- Connect and verify GATT service discovery
- Confirm notification subscription to characteristic `0x33F2`
- Test time synchronization
- Verify HR and BP notifications when available
- Request and complete history transfer
- Verify battery request/response
- Turn Bluetooth OFF while connected
- Turn Bluetooth OFF during history transfer
- Turn Bluetooth ON and reconnect manually
- Review technical log for errors and excessive repetition

## Pull request / change report

Include:
- Summary and rationale
- Files changed
- Risk to BLE behavior
- Tests performed and their results
- Any remaining uncertainty
