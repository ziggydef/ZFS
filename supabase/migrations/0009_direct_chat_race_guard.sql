-- Serialize concurrent direct-chat creation for the same pair of users.
-- This prevents two devices pressing "Написать" at the same time from
-- creating duplicate one-to-one conversations.
create or replace function public.create_chat(
  p_kind public.chat_kind,
  p_title text,
  p_member_ids uuid[]
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_chat_id uuid;
  v_member_count integer;
  v_other_user uuid;
  v_pair_key bigint;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  if p_member_ids is null then
    p_member_ids := array[]::uuid[];
  end if;

  select count(distinct member_id)
    into v_member_count
    from unnest(p_member_ids) as members(member_id)
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

  if p_kind = 'direct' then
    v_other_user := p_member_ids[1];
    v_pair_key := hashtextextended(
      least(auth.uid()::text, v_other_user::text) || ':' ||
      greatest(auth.uid()::text, v_other_user::text),
      0
    );
    perform pg_advisory_xact_lock(v_pair_key);

    select c.id
      into v_chat_id
      from public.chats c
     where c.kind = 'direct'
       and exists (
         select 1 from public.chat_members m
          where m.chat_id = c.id and m.user_id = auth.uid()
       )
       and exists (
         select 1 from public.chat_members m
          where m.chat_id = c.id and m.user_id = v_other_user
       )
       and (select count(*) from public.chat_members m where m.chat_id = c.id) = 2
     order by c.created_at asc
     limit 1;

    if v_chat_id is not null then
      return v_chat_id;
    end if;
  end if;

  insert into public.chats(kind, title, created_by)
  values (p_kind, nullif(trim(p_title), ''), auth.uid())
  returning id into v_chat_id;

  insert into public.chat_members(chat_id, user_id, role)
  values (v_chat_id, auth.uid(), 'owner');

  insert into public.chat_members(chat_id, user_id, role)
  select v_chat_id, member_id, 'member'
    from unnest(p_member_ids) as members(member_id);

  return v_chat_id;
end;
$$;

revoke all on function public.create_chat(public.chat_kind, text, uuid[]) from public;
grant execute on function public.create_chat(public.chat_kind, text, uuid[]) to authenticated;

comment on function public.create_chat(public.chat_kind, text, uuid[])
is 'Atomically creates chats and serializes direct-chat lookup to prevent duplicates.';
