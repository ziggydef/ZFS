# becamce

Native Android messenger project. Current repository milestone: initial UI foundation.

## Stack
- Kotlin and Jetpack Compose
- Android SDK 36, minSdk 26, JDK 17
- Planned backend: Supabase Auth, PostgreSQL, Realtime and Storage
- Planned push: Firebase Cloud Messaging
- Planned end-to-end encryption: established protocol/library, not custom cryptography

## Current state
The app now includes email sign-up/sign-in/password-reset screens, a profile screen with sign-out, Supabase Auth REST calls, and AES-GCM encrypted session storage backed by Android Keystore. A Supabase chat data repository now supports chat/message reads, message sending, chat creation, edit/delete RPCs and read markers; a profile repository supports profile read/update and username availability. These repositories are not yet fully connected to the main navigation UI, and live Supabase behavior has not been verified. Realtime WebSocket delivery, contact discovery, media transfer, calls, channels, bot API, and E2EE are not implemented yet. Authentication requires a Supabase project and the repository Actions secrets described in `docs/BACKEND_SETUP.md`.

## Build policy
The GitHub Actions workflow is manual-only and requires the explicit boolean confirmation input. No builds are triggered by commits. The single final APK build should be started only after implementation and review are complete.
