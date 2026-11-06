# becamce

Native Android messenger project. Current repository milestone: initial UI foundation.

## Stack
- Kotlin and Jetpack Compose
- Android SDK 36, minSdk 26, JDK 17
- Planned backend: Supabase Auth, PostgreSQL, Realtime and Storage
- Planned push: Firebase Cloud Messaging
- Planned end-to-end encryption: established protocol/library, not custom cryptography

## Current state
The current app is a visual foundation with demo chat rows and placeholder navigation. It is not yet a functioning network messenger. Authentication, backend messaging, media transfer, calls, channels, bot API, and E2EE remain to be implemented and tested.

## Build policy
The GitHub Actions workflow is manual-only and requires the explicit boolean confirmation input. No builds are triggered by commits. The single final APK build should be started only after implementation and review are complete.
