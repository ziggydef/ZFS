# Backend setup

The backend uses Supabase. The project owner must create the Supabase project; credentials are not committed to this repository.

1. Create a Supabase project.
2. Apply every SQL migration in order: `0001_initial_schema.sql` through `0013_disable_unimplemented_secret_chats.sql`.
3. Enable Email Auth in Authentication → Sign In / Providers. Add `becamce://auth/callback` to Authentication → URL Configuration → Redirect URLs so password-reset links return to the app. Phone sign-in is intentionally hidden in the current app version; no SMS provider is needed for the current email-only milestone.
4. Add the project URL and publishable/anon key as GitHub Actions repository secrets named `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
5. Never put a service-role key in the Android app, source control, Actions secrets used by the client build, screenshots, or chat.
6. Test policies with at least two separate test accounts before using real user data.

## Android configuration

The app reads `SUPABASE_URL` and `SUPABASE_ANON_KEY` at build time. The GitHub Actions workflow passes those secrets into Gradle. Without them, the APK can still compile, but authentication and cloud data features show a configuration error.

## Identity and usernames

The app supports public usernames of 4–24 lowercase ASCII letters, digits, and underscores. Usernames are case-insensitive and unique. Users can set or change a username in Profile; the Contacts tab searches profiles by username or display name. Apply migrations `0007_username_system.sql`, `0008_reuse_direct_chats.sql`, and `0009_direct_chat_race_guard.sql` after the preceding migrations. Migration 0008 reuses an existing one-to-one conversation; migration 0009 serializes simultaneous creation requests for the same pair to prevent race-condition duplicates. Migration 0010 restricts execution of membership-check and trigger-only SECURITY DEFINER helpers. Migration 0011 adds server-authorized add/remove operations for group members; the Android management UI is still pending. Migration 0012 creates a private `chat-attachments` Storage bucket, member-only read policies, and message filename metadata. Migration 0013 rejects secret-chat records until end-to-end encryption is implemented.

## Current implementation notes

Email sign-up/sign-in, password-reset email and in-app password update through the recovery deep link, encrypted session storage, profile editing, username availability checks, profile discovery, direct-chat creation (reusing an existing conversation), group creation from selected search results, and basic message send/read screens are implemented in the client. Chat and message updates currently use periodic PostgREST polling, not Supabase Realtime. Group administration UI, voice messages, calls, channels, bots, push notifications, and end-to-end encryption remain unfinished. Chat attachments support selected image, PDF, text, ZIP and Office formats up to 20 MB; files are stored privately and opened using short-lived signed URLs. Supabase behavior has not been verified against a live project.

Phone OTP methods remain in the data layer for a future release, but phone UI is disabled. Real SMS delivery would require a provider and may cost money. Keep message expiry and storage authorization server-side; client-side checks are not security boundaries.
