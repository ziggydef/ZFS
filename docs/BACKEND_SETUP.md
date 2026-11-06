# Backend setup

The backend is planned on Supabase. A project must be created by the project owner; no credentials are committed to this repository.

1. Create a Supabase project.
2. Apply migrations in order in the SQL Editor: `0001_initial_schema.sql`, then `0002_chat_creation_rpc.sql`, then `0003_auth_profile_trigger.sql`, then `0004_channel_rls_hardening.sql`.
3. Enable Email and Phone Auth providers. For phone registration, enable SMS/phone authentication and configure a supported SMS provider (for example Twilio) in Supabase Auth settings. Set sensible OTP expiry and rate limits; test delivery with a number you control.
4. Create private Storage buckets for profile media and chat attachments.
5. Add Storage policies based on chat membership and channel subscription.
6. Configure the Android app with the project URL and publishable/anon key using build-time configuration.
7. Keep the service-role key exclusively in server-side functions/secrets.
8. Configure a server-side scheduled worker for message expiry. A client-side timer is not sufficient.
9. Test every RLS policy with at least two separate test accounts before real user data is used.

The Android client currently uses Supabase Auth for email and phone authentication; messaging, storage, and the other backend features are still under implementation. Do not paste service-role keys into source code, GitHub commits, screenshots, or chat.


## Android app configuration

The Android client reads two build-time environment variables:
- `SUPABASE_URL`: project URL, for example `https://your-project.supabase.co`
- `SUPABASE_ANON_KEY`: the project's publishable/anon key (safe for a client when RLS is correctly configured)

For GitHub Actions, add repository Actions secrets with those exact names. The workflow passes them into Gradle during the final build. Do not add a service-role key to GitHub Actions or the Android app.

The Android auth screen supports email sign-up/sign-in/password-reset and phone-based sign-up/sign-in using a one-time SMS code. Phone auth uses Supabase `POST /auth/v1/otp` with `create_user=true`, followed by `POST /auth/v1/verify` with `type=sms`; successful verification stores the same encrypted session used by email login. Phone numbers must be entered in E.164 format (for example `+4915112345678`). SMS delivery will only work after Phone Auth and an SMS provider are configured in the Supabase dashboard. Session tokens are encrypted with an AES key held by Android Keystore. Supabase email confirmation settings still apply. The app currently needs the Supabase project and keys before authentication requests can succeed.
