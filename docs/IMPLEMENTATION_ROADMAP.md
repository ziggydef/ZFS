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
- [ ] Supabase project and Auth configuration
- [ ] Sign-up, sign-in, sign-out, password recovery
- [ ] Profile creation and username availability
- [ ] Secure session persistence and logout-all-devices

## Messaging
- [x] Atomic direct/group chat creation RPC with initial membership
- [ ] Direct-chat client integration
- [ ] Group membership management, roles and invites
- [ ] Message send/receive with Realtime
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
