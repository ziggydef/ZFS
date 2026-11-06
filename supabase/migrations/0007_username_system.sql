-- Stable, case-insensitive usernames for public discovery.
-- Existing profiles without a username remain valid.
alter table public.profiles drop constraint if exists username_format;
alter table public.profiles
  add constraint username_format
  check (username is null or username ~ '^[a-z0-9_]{4,24}$');

create unique index if not exists profiles_username_lower_unique
  on public.profiles (lower(username))
  where username is not null;

comment on column public.profiles.username is
  'Public, case-insensitive unique handle. Stored lowercase; 4-24 ASCII letters, digits, underscores.';
