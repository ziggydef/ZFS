-- Normalize legacy handles and safely release invalid/case-colliding values
-- before enforcing the public username policy.
with ranked as (
  select id, username,
         row_number() over (partition by lower(username) order by created_at, id) as position
    from public.profiles
   where username is not null
)
update public.profiles p
   set username = null
  from ranked r
 where p.id = r.id
   and (
     r.position > 1
     or lower(r.username) !~ '^[a-z0-9_]{4,24}$'
   );

update public.profiles
   set username = lower(username)
 where username is not null;

alter table public.profiles drop constraint if exists username_format;
alter table public.profiles
  add constraint username_format
  check (username is null or username ~ '^[a-z0-9_]{4,24}$');

create unique index if not exists profiles_username_lower_unique
  on public.profiles (lower(username))
  where username is not null;

comment on column public.profiles.username is
  'Public, case-insensitive unique handle. Stored lowercase; 4-24 ASCII letters, digits, underscores.';
