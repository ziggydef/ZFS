-- Tighten execution privileges for SECURITY DEFINER helpers.
-- RLS policies call is_chat_member only for authenticated sessions.
create or replace function public.is_chat_member(p_chat_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
      from public.chat_members m
     where m.chat_id = p_chat_id
       and m.user_id = auth.uid()
  );
$$;

revoke all on function public.is_chat_member(uuid) from public;
revoke all on function public.is_chat_member(uuid) from anon;
grant execute on function public.is_chat_member(uuid) to authenticated;

-- Trigger-only function: callers must not be able to invoke it directly.
revoke all on function public.bump_chat_updated_at() from public;
revoke all on function public.bump_chat_updated_at() from anon;
revoke all on function public.bump_chat_updated_at() from authenticated;

-- Keep the trigger active after privilege tightening.
comment on function public.is_chat_member(uuid)
is 'Returns whether the current authenticated user belongs to the supplied chat; executable only by authenticated users.';
comment on function public.bump_chat_updated_at()
is 'Trigger-only helper that updates chat ordering after message insertion.';
