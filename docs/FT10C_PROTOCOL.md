# JetSport FT-10C protocol notes

Protocol details below are based on captured traffic and observed device behavior. This is not an official specification. Unknown bytes/fields are not assigned meanings.


## Observed GATT identifiers

| Item | UUID / value | Notes |
|---|---|---|
| FT-10C service | `000055ff-0000-1000-8000-00805f9b34fb` | Profile service used by the app |
| Write characteristic | `000033f1-0000-1000-8000-00805f9b34fb` | App sends protocol commands here |
| Notification characteristic | `000033f2-0000-1000-8000-00805f9b34fb` | Device notifications |
| CCCD | `0x2902` | Notification subscription descriptor |
| Auxiliary characteristic | `0000ff02-0000-1000-8000-00805f9b34fb` | Auxiliary payload channel; payload meaning not fully established |

## Notification packet families

### `A2` — battery

- App requests battery data using the FT-10C-specific request flow.
- A notification beginning with `A2` and at least two bytes long is decoded as battery percent from byte 1.
- Values are constrained to 0–100 by the decoder.
- The standard Battery Service `0x180F / 0x2A19` reading has been considered unreliable during device testing and is not the preferred source.

### `E5` — heart rate

Observed packet pattern: `E5 00 00 XX`, where `XX` is interpreted as beats per minute in the confirmed notification form.

### `C7` — blood pressure

Observed packet pattern: `C7 00 00 SS DD`, with `SS` and `DD` interpreted as systolic and diastolic values for the confirmed packet form.

### `B1` — live steps

The decoder recognizes an 8-byte-or-longer `B1` packet with year, month, day, hour, and a 16-bit step count. Exact field order is implemented in the decoder.

### `B2` — history

- History data records are 18 bytes and begin with `B2`.
- The parser validates date/time fields and activity step totals.
- A repeated date/hour record replaces the prior record within the transfer session.
- Completion marker is `B2 FD`; an optional trailing byte is preserved but not interpreted.
- The transfer is committed only when the session reaches the completion marker.

## Time synchronization (`A3`)

The app encodes the time packet as:


`A3 | year-high | year-low | month | day | hour | minute | second`

- Year is big-endian.
- Month is 1-based (`1..12`), not Android `Calendar.MONTH`'s zero-based value.
- The app uses local device time for automatic synchronization.
- The observed device response echoes the `A3` packet.

## Profile synchronization (`A9`)

The project uses the `A9` profile synchronization command and has observed an `A9 00` response. The exact command payload fields are not documented here because this note does not establish a complete byte-level specification.

## History request (`B2 FA`)

The history request uses the `B2 FA` command family. The request's full payload and parameter semantics are not asserted here. History notifications are expected to end with the `B2 FD` marker.

## Protocol research status

| Area | Status |
|---|---|
| Time sync packet (`A3`) | Byte layout implemented and confirmed during testing |
| HR (`E5`) | Confirmed notification form |
| BP (`C7`) | Confirmed notification form |
| FT-10C battery (`A2`) | Confirmed request/notification path used by app |
| Step history (`B2`) | Record structure and end marker parsed/validated |
| Profile sync (`A9`) | Command/response family observed; payload not fully documented |
| SpO2 | Not implemented; investigation paused |
| Auxiliary channel (`FF02`) | Raw payload forwarded; meaning not fully established |
