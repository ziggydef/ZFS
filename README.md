# becamce

Native Android messenger built with Kotlin and Jetpack Compose.

## Stack
- Kotlin, Jetpack Compose, Material 3
- Android SDK 36, minSdk 26, JDK 17
- Supabase Auth, PostgreSQL and PostgREST
- Android Keystore + AES-GCM encrypted session storage

## Implemented in this milestone
- Email registration, sign-in and password reset
- Profile editing: display name, unique public username and bio
- Case-insensitive username uniqueness enforced by PostgreSQL
- People search by `@username` or display name
- Direct-chat creation from search results
- Basic chat list, conversation view and message sending
- Edit/delete own messages in the conversation UI, backed by server-checked RPCs
- Mark-chat-read RPC and periodic message refresh
- Manual-only GitHub Actions APK workflow

## Not yet release-complete
Chat/message refresh currently uses polling rather than Realtime WebSockets. Group management, media sharing, voice messages, calls, channels, bot API, push notifications, offline queue, and audited end-to-end encryption are not implemented. Supabase integration has not been tested against a live project. A real backend URL and publishable key are required for account and cloud features.

See [backend setup](docs/BACKEND_SETUP.md) and [implementation roadmap](docs/IMPLEMENTATION_ROADMAP.md). Apply migrations `0001` through `0008` in order.

## Build policy
The GitHub Actions workflow is manual-only and requires explicit final-build confirmation. Do not start intermediate builds; the release APK build is reserved for the final review.
