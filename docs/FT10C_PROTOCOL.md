# JetSport FT-10C protocol notes

Protocol details below are based on captured traffic and observed device behavior. This is not an official specification. Unknown bytes and fields are intentionally left uninterpreted.

## Observed GATT identifiers

| Item | UUID / value | Notes |
|---|---|---|
| FT-10C service | `000055ff-0000-1000-8000-00805f9b34fb` | Main profile service used by the app |
| Write characteristic | `000033f1-0000-1000-8000-00805f9b34fb` | App-to-bracelet write channel |
| Notification characteristic | `000033f2-0000-1000-8000-00805f9b34fb` | Bracelet-to-app notification channel |
| CCCD | `0x2902` | Notification subscription descriptor |
| Standard Battery Service | `0x180F / 0x2A19` | Observed value was unreliable on the tested FT-10C |
| Auxiliary service | `0000fee7-0000-1000-8000-00805f9b34fb` | Includes characteristics whose payload semantics are not fully established |

## Notification packet families

### `A2` — battery

The app sends the FT-10C-specific battery request through the main write channel. A notification beginning with `A2` and containing at least two bytes is decoded as a percentage from byte 1, constrained to `0..100`. Only a valid `A2` response advances the refresh timestamp. On the tested bracelet, the standard Battery Service characteristic returned `100%` even when the bracelet display showed a lower value; therefore it is not the preferred source.

### `E5` — heart rate

Observed packet pattern: `E5 00 00 XX`, where `XX` is interpreted as beats per minute in the confirmed notification form.

### `C7` — blood pressure

Observed packet pattern: `C7 00 00 SS DD`, where `SS` and `DD` are interpreted as systolic and diastolic values for the confirmed packet form.

### `B1` — live steps

- The decoder recognizes an 8-byte-or-longer `B1` packet.
- Bytes 1–2 contain the year (big-endian); bytes 3, 4, and 5 contain month, day, and hour; bytes 6–7 contain the 16-bit step count (big-endian).
- The date must be a real calendar date (including leap-year validation), and hour must be `0..23`; invalid packets are ignored before persistence or UI dispatch.
- Captured packets may be 18 bytes long. Bytes after byte 7 are preserved only in the raw diagnostic log; their meaning is not established.
- The count is an absolute total for that date/hour. A later B1 update replaces the stored value for the same hour rather than being added to it.

### `B2` — step history

- History data records are 18 bytes and begin with `B2`.
- The parser validates date/time fields and activity step totals.
- A repeated date/hour record replaces the prior record within the transfer session.
- Completion marker is `B2 FD`; an optional trailing byte is preserved but not interpreted.
- The transfer is committed only when the session reaches the completion marker.
- The history request uses the `B2 FA` command family; its full payload and parameter semantics are not asserted here.

### `D1 0A` — Phone Finder activation

When the bracelet's Find Phone function is activated, the app observes a two-byte `D1 0A` notification on `0x33F2`. This is an incoming event marker, not a command to transmit back. The app uses it to start the phone-side sound/vibration alert. The exact bracelet-side internal implementation beyond this observed notification is unknown.

## Time synchronization (`A3`)

The app encodes the time packet as:

`A3 | year-high | year-low | month | day | hour | minute | second`

- Year is big-endian.
- Month is 1-based (`1..12`), not Android `Calendar.MONTH`'s zero-based value.
- The app uses local phone time for automatic synchronization.
- The observed device response echoes the `A3` packet. The echo confirms packet reception but does not independently verify the displayed bracelet clock.

## Profile synchronization (`A9`)

The project uses the `A9` profile synchronization command and has observed an `A9 00` response. The exact command payload fields are not documented here because the captured evidence does not establish a complete byte-level specification.

## B1/B2 overlap and database merge policy

B1 is a live absolute hourly total; B2 is a history snapshot with activity breakdown. A B1 notification may arrive while a B2 transfer is in progress. The app records the wall-clock time of each accepted B2 request and each B1 update. When a complete B2 transfer is committed, a B1 total for the same hour is preserved if its notification was received at or after the B2 request began. The B2 run/walk fields are still imported. B1 updates received before the B2 request do not override the subsequent B2 snapshot. Incomplete B2 transfers are discarded. The timestamp is local merge metadata, not a bracelet-provided protocol field.

## Protocol research status

| Area | Status |
|---|---|
| Time sync (`A3`) | Byte layout implemented; packet echo observed |
| HR (`E5`) | Confirmed notification form |
| BP (`C7`) | Confirmed notification form |
| FT-10C battery (`A2`) | Confirmed request/notification path used by app |
| Live steps (`B1`) | Date/hour/count fields validated and decoded |
| Step history (`B2`) | Record structure and end marker parsed/validated |
| Profile sync (`A9`) | Command/response family observed; payload not fully documented |
| Phone Finder (`D1 0A`) | Incoming activation event observed and handled |
| SpO2 | Not implemented; investigation paused |
| Auxiliary payloads | Raw data may be logged; semantics not fully established |
