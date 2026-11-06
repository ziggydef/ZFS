-- Automatically create a profile row when Supabase Auth creates a user.
-- The profile is intentionally minimal; users can complete it in the app later.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_display_name text;
begin
  v_display_name := coalesce(
    nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
    nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
    split_part(coalesce(new.email, 'becamce user'), '@', 1)
  );

  insert into public.profiles(id, display_name)
  values (new.id, v_display_name)
  on conflict (id) do nothing;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created_becamce on auth.users;
create trigger on_auth_user_created_becamce
after insert on auth.users
for each row execute procedure public.handle_new_user();

revoke all on function public.handle_new_user() from public;
