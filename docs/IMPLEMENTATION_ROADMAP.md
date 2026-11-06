# Implementation roadmap

Items are marked complete when implemented in source; live-backend verification and release testing are tracked separately.

## Foundation
- [x] Private GitHub repository and Android Compose project
- [x] Dark violet glass-inspired interface
- [x] Manual-only final APK workflow
- [x] PostgreSQL schema, RLS baseline and ordered migrations
- [x] Domain models, repository contracts and input validation tests
- [ ] Full unit/UI test coverage and dependency/build verification

## Identity and usernames
- [x] Email sign-up, sign-in and password-reset UI
- [x] Supabase Auth REST integration
- [x] Android Keystore encrypted session storage and refresh
- [x] Sign-out
- [x] Profile read/update
- [x] Public username editor and server-side case-insensitive uniqueness
- [x] Username availability check and profile search by username/display name
- [ ] Supabase project configuration and live account-flow verification
- [ ] Account deletion, session revocation and lifecycle hardening
- [ ] Phone login UI (intentionally deferred)

## Messaging
- [x] Atomic direct/group chat creation RPC
- [x] Chat and message PostgREST repository
- [x] Direct-chat creation from people search
- [x] Reuse existing one-to-one conversations instead of duplicating them
- [x] Create group conversations from selected people
- [x] Basic chat list and conversation screen
- [x] Send message and periodic message refresh
- [x] Edit/delete/read RPCs in data layer
- [ ] Supabase Realtime WebSocket delivery
- [ ] Pagination, local cache and offline queue
- [x] Edit/delete own messages in the conversation UI
- [x] Reply to messages in the conversation UI
- [ ] Delivery/read state in UI
- [ ] Group membership management, roles and invites
- [ ] Image/video/document uploads and private storage
- [ ] Voice recording and playback

## Privacy and safety
- [ ] Audited secret-chat protocol and device verification
- [ ] Encrypted attachment format
- [ ] Server-side expiry/deletion worker
- [ ] Block/report controls and rate limits
- [ ] Security review and RLS testing with multiple accounts

## Other product features
- [ ] Channels and moderation
- [ ] Bot registration and scoped API
- [ ] Push notifications
- [ ] Theme editor, accessibility and localization review

## Release
- [ ] Configure Supabase URL/key in repository Actions secrets
- [ ] Run automated tests and inspect final source changes
- [ ] One final GitHub Actions APK build
