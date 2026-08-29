# Raven Kotlin Android Integration TODO

- [x] Extract and inspect the supplied Android backend archive and source modules.
- [x] Inventory the supplied frontend screens, UX flows, assets, and hardcoded data.
- [x] Identify backend services, persistence, network contracts, and configuration inputs.
- [x] Map frontend flows to Kotlin Android screens and application state.
- [x] Consolidate duplicated models, constants, and networking logic.
- [x] Replace hardcoded runtime values with backend/configuration-driven values.
- [x] Implement the Kotlin Android UI and backend integration.
- [x] Add or update unit and instrumented-test coverage for critical flows.
- [x] Run Gradle compilation and tests; lint was attempted and hit an environment teardown exception.
- [x] Deliver the integrated Kotlin project with a verification summary.

## Scope decision

Raven is being implemented as an Android application with Kotlin as the primary language. The supplied frontend is treated as the UI/UX reference and source of interaction requirements, not as the final runtime platform.

## Open dependency

- [x] Confirm whether the supplied archive contains an HTTP/API backend or Android-local backend code; the supplied backend is Android-local Bluetooth mesh runtime code.
