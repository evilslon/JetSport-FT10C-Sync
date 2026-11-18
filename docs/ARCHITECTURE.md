# Architecture overview

This note describes the source layout at the v0.5.3.27 checkpoint. It is a high-level guide, not a complete API reference.

## Main layers

- **BLE** (`ble/`): scan coordination, connection lifecycle, GATT access, notification enablement, command serialization, and sync scheduling.
- **Device** (`device/`): FT-10C profile, capability definitions, and device matching.
- **Protocol** (`protocol/`): stateless notification decoding and history packet parsing.
- **Sync** (`sync/`): synchronization state/coordinators for time, battery, and history.
- **Data** (`data/`): settings, active-day/profile state, measurements, and bounded technical log.
- **Domain** (`domain/`): activity calculation logic.
- **Presentation** (`presentation/`): UI state, view models, screen controllers, Compose UI, and action routing.

## Connection and notification flow

1. Scan and identify a compatible device using its name token or advertised service UUID.
2. Connect and discover GATT services/characteristics.
3. Enable notifications on the FT-10C notify characteristic and write its CCCD.
4. The notification subscription is treated as a stateful operation; synchronization actions are gated by subscription readiness.
5. Incoming notification bytes are decoded into protocol events, then routed to the appropriate sync or measurement handler.
6. Connection-scoped scheduled work is cancelled when the connection is lost or replaced.

## Reliability boundaries

- The command queue serializes GATT writes and tracks their completion.
- History is accumulated in a session and committed only after a valid completion marker; incomplete transfers are not treated as complete history.
- Bluetooth OFF handling stops scanning, closes the GATT connection, cancels active sync work, and clears incomplete history state.
- The technical log is bounded to avoid unbounded growth.

## Testing

JVM unit tests cover decoders, parsers, queue/state coordinators, screen state, metrics, and other isolated logic. Hardware/Bluetooth integration behavior still requires testing on a real phone and bracelet.
