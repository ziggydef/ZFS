# Backend setup

The backend is planned on Supabase. A project must be created by the project owner; no credentials are committed to this repository.

1. Create a Supabase project.
2. Apply migrations in order in the SQL Editor: `0001_initial_schema.sql`, then `0002_chat_creation_rpc.sql`.
3. Enable the intended Auth providers and configure email verification.
4. Create private Storage buckets for profile media and chat attachments.
5. Add Storage policies based on chat membership and channel subscription.
6. Configure the Android app with the project URL and publishable/anon key using build-time configuration.
7. Keep the service-role key exclusively in server-side functions/secrets.
8. Configure a server-side scheduled worker for message expiry. A client-side timer is not sufficient.
9. Test every RLS policy with at least two separate test accounts before real user data is used.

The current Android app does not connect to this backend yet. Do not paste service-role keys into source code, GitHub commits, screenshots, or chat.
