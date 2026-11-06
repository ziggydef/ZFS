# Implementation roadmap

This is the delivery checklist for becamce. Items are not considered complete until implemented and verified.

## Foundation
- [x] Private GitHub repository
- [x] Kotlin / Compose Android project
- [x] Initial dark glass chat-list UI
- [x] Manual-only final APK workflow
- [x] Initial PostgreSQL schema and RLS baseline
- [x] Domain models and repository contracts for auth, profiles and chats
- [x] Shared validation rules and JUnit tests for account/message input
- [x] Dependency/build compatibility review without triggering APK builds
- [ ] Unit and UI test coverage

## Identity and sessions
- [x] SQL auth profile trigger and ordered backend migrations
- [ ] Supabase project and Auth configuration + GitHub Actions secrets
- [x] Email sign-up, sign-in and password-reset UI
- [ ] Phone sign-in UI deferred by product decision; backend endpoints retained for later
- [x] Supabase Auth REST integration
- [x] Android Keystore encrypted session persistence and refresh
- [x] Sign-out UI
- [ ] End-to-end account flow verification against a configured Supabase project
- [x] Profile fetch/update and username availability repository
- [x] Secure encrypted session persistence
- [ ] Logout-all-devices and full account lifecycle

## Messaging
- [x] Atomic direct/group chat creation RPC with initial membership
- [x] Supabase PostgREST chat repository and atomic chat creation call
- [x] Message send, edit, delete and read-position RPC integration in data layer
- [ ] Direct-chat UI and contact discovery
- [ ] Group membership management, roles and invites
- [ ] Message send/receive with Realtime (initial repository currently polls; websocket transport pending)
- [ ] Pagination, local cache, offline queue
- [ ] Reply, edit, delete, delivery/read state
- [ ] Image, video and document upload with private storage
- [ ] Voice recording, playback and upload

## Privacy and safety
- [ ] Secret-chat protocol selection and implementation using audited libraries
- [ ] Device identity and safety-number verification
- [ ] Encrypted attachment format
- [ ] Server-side expiry/deletion worker
- [ ] Block/report controls and rate limits

## Channels and bots
- [ ] Public/private channels and subscriptions
- [x] RLS hardening for public subscriptions and channel post edits
- [x] Secure message edit/delete RPCs with restricted direct table mutation
- [ ] Channel publishing and moderation roles
- [ ] Bot registration and scoped API tokens
- [ ] Bot permissions, quotas, revocation and abuse controls

## Product finish
- [ ] Theme editor and message appearance settings
- [ ] Push notifications
- [ ] Accessibility and localization review
- [ ] Security review and migration tests
- [ ] One final GitHub Actions APK build

## Build discipline
The workflow is manual-only. Do not run it during implementation. The final workflow dispatch must be performed once after all planned release checks pass.
