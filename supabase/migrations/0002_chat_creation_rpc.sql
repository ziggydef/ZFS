-- Atomic chat creation. The caller is always added as owner.
-- Client apps must call this function instead of inserting chat/member rows separately.
create or replace function public.create_chat(
  p_kind public.chat_kind,
  p_title text,
  p_member_ids uuid[]
)
returns uuid
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_chat_id uuid;
  v_member_count integer;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  if p_member_ids is null then
    p_member_ids := array[]::uuid[];
  end if;

  select count(distinct member_id)
    into v_member_count
    from unnest(p_member_ids) as member_id
   where member_id <> auth.uid();

  if v_member_count <> cardinality(p_member_ids) then
    raise exception 'Member list contains duplicates or the caller';
  end if;

  if p_kind in ('direct', 'secret') and v_member_count <> 1 then
    raise exception 'Direct and secret chats require exactly one other member';
  end if;

  if p_kind = 'group' and v_member_count < 1 then
    raise exception 'Groups require at least one other member';
  end if;

  if p_kind = 'group' and coalesce(trim(p_title), '') = '' then
    raise exception 'Group title is required';
  end if;

  insert into public.chats(kind, title, created_by)
  values (p_kind, nullif(trim(p_title), ''), auth.uid())
  returning id into v_chat_id;

  insert into public.chat_members(chat_id, user_id, role)
  values (v_chat_id, auth.uid(), 'owner');

  insert into public.chat_members(chat_id, user_id, role)
  select v_chat_id, member_id, 'member'
    from unnest(p_member_ids) as member_id;

  return v_chat_id;
end;
$$;

revoke all on function public.create_chat(public.chat_kind, text, uuid[]) from public;
grant execute on function public.create_chat(public.chat_kind, text, uuid[]) to authenticated;

-- Editing/deleting messages is only allowed while the author remains a member.
drop policy if exists "Authors edit own messages" on public.messages;
create policy "Authors edit own messages"
on public.messages for update to authenticated
using (sender_id = auth.uid() and public.is_chat_member(chat_id))
with check (sender_id = auth.uid() and public.is_chat_member(chat_id));

comment on function public.create_chat(public.chat_kind, text, uuid[])
is 'Creates a chat and its initial membership atomically for the authenticated user.';
