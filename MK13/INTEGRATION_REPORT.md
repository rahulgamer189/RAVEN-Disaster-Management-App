# Raven Kotlin Android Integration Report

## Outcome

Raven is now organized as a Kotlin-first Android application. The supplied frontend was translated into a Compose UI reference implementation for the core operational flows, while the supplied Android Bluetooth mesh runtime remains the source of live transport, telemetry, peer discovery, messages, SOS broadcasts, and camp updates.

## Implemented integration

| Area | Result |
|---|---|
| Application shell | Replaced the 1,375-line monolithic activity with a focused `MainActivity` and modular `RavenApp` Compose UI. |
| Frontend UX translation | Added Home, Mesh, Map, Offline Guide, and Settings flows with responsive cards, live metrics, empty states, SOS action, location sharing, message composer, and profile editing. |
| Backend/runtime wiring | UI consumes `BluetoothViewModel` flows backed by `BluetoothService`; peer telemetry and messages are no longer seeded in the interface. |
| Hardcoded runtime data | Removed `teammate_locations.json` bootstrap and the `loadMockTeammates()` path. No sample peer coordinates are inserted at startup. |
| Shared contracts | Added `MeshProtocol` for canonical message types and RFCOMM UUID, and updated client, server, service, ViewModel, and message model to use it. |
| Profile identity | Added `RavenPreferences` and connected the Settings profile editor to the Bluetooth sender identity. |
| Lifecycle | Added explicit Bluetooth scan/advertise shutdown and consolidated duplicate service teardown. |
| Build cleanup | Restored missing Gradle settings, switched to a stable Android/Kotlin/Compose setup, enabled AndroidX, removed obsolete navigation files and a legacy Material Components drawable. |
| Tests | Replaced the generated arithmetic test with Raven-specific mesh protocol and TTL relay tests. |

## Verification

The final verification command was:

```text
./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug
```

The command completed successfully. Kotlin compilation, JVM tests, Android resource processing, dexing, and debug APK packaging all passed. The generated artifact is `android/app/build/outputs/apk/debug/app-debug.apk` with SHA-256:

```text
4400750c75bcc07203ffb032c392af027632f2d6c516f533194bf904efd6733a
```

The test suite covers canonical wire types, TTL decrement behavior, immutability of the original message during relay, and rejection of expired or exhausted messages.

## Remaining validation note

A standalone `lintDebug` run was attempted. Lint analysis reached the Android lint worker but failed during environment teardown with IntelliJ/UAST `AlreadyDisposedException`; it did not report a source lint finding. The build and unit-test verification remain successful. The app should still be exercised on a physical Android device or emulator to validate runtime Bluetooth permissions, BLE advertising/scanning, RFCOMM connectivity, notification behavior, and location availability.

## Project location

The deliverable Android project is under `android/`. Open that directory in Android Studio and use the included Gradle wrapper. The sandbox-local `local.properties` file is intentionally excluded from the archive so Android Studio can generate the correct SDK path for the target machine.
