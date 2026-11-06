-- Username/password-only authentication.
-- Supabase Auth still requires an email-shaped identifier internally; this
-- reserved .invalid address is never used for email delivery or shown in the app.
alter table public.profiles
  add column if not exists auth_username text;

update public.profiles
   set auth_username = lower(username)
 where auth_username is null
   and username is not null
   and username ~ '^[a-zA-Z0-9_]{4,24}$';

create unique index if not exists profiles_auth_username_unique
  on public.profiles (lower(auth_username))
  where auth_username is not null;

-- Existing accounts with a valid username keep their data and password.
-- Their Auth email becomes an internal alias so the app can sign in by username.
update auth.users u
   set email = lower(p.username) || '@auth.becamce.invalid',
       email_confirmed_at = coalesce(u.email_confirmed_at, now()),
       updated_at = now()
  from public.profiles p
 where p.id = u.id
   and p.username is not null
   and p.username ~ '^[a-zA-Z0-9_]{4,24}$'
   and u.email is distinct from lower(p.username) || '@auth.becamce.invalid';

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_display_name text;
  v_username text;
begin
  v_display_name := coalesce(
    nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
    nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
    'becamce user'
  );
  v_username := lower(nullif(trim(new.raw_user_meta_data ->> 'username'), ''));

  insert into public.profiles(id, display_name, username, auth_username)
  values (new.id, v_display_name, v_username, v_username)
  on conflict (id) do update
    set display_name = excluded.display_name,
        username = coalesce(public.profiles.username, excluded.username),
        auth_username = coalesce(public.profiles.auth_username, excluded.auth_username);

  return new;
end;
$$;

drop trigger if exists on_auth_user_created_becamce on auth.users;
create trigger on_auth_user_created_becamce
after insert on auth.users
for each row execute procedure public.handle_new_user();

revoke all on function public.handle_new_user() from public;
