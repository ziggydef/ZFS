# becamce architecture notes

## Product scope
A standalone Android messenger, not a Telegram fork.

## Android layers
- presentation: Compose screens, navigation, UI state
- domain: use cases and immutable models
- data: repositories, remote data sources, local cache
- core: session handling, error mapping, media utilities

## Planned modules
- app: application entry point and navigation
- core-design: glass theme and reusable components
- core-model: user, chat, message and channel models
- core-data: repository contracts and implementations
- feature-auth: sign-up, sign-in, profile
- feature-chats: direct and group conversations
- feature-voice: voice message recording/playback
- feature-channels: channel discovery and publishing
- feature-settings: privacy and appearance

The first commit keeps the Gradle project as a single app module to minimize build complexity. Split modules only when the interfaces stabilize.

## Backend
Supabase Auth + PostgreSQL + Realtime + Storage. Database baseline is in
`supabase/migrations/0001_initial_schema.sql`. Row Level Security is enabled
for user-facing tables. Review and extend policies before production use.

## Security requirements
- Keep service-role credentials server-side only.
- Use short-lived signed URLs for private media.
- Secret chats must use a reviewed end-to-end encryption protocol and audited library.
- Never claim E2EE until key verification, device lifecycle, forward secrecy,
  and encrypted attachment handling are implemented and tested.
- Message expiry needs a trusted server-side scheduled deletion process; a client
  countdown alone is not secure deletion.
- Bots must use a scoped server-side API with explicit permissions and rate limits.

## Build policy
GitHub Actions is manual-only and gated by a confirmation input. Commits do not
trigger builds. Run one final APK build after implementation, dependency review,
and source-level validation.
