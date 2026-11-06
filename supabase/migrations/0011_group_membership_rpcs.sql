-- Server-authorized group membership management.
-- Owners/admins may add members; only owners may remove admins or change roles.
create or replace function public.manage_group_member(
  p_chat_id uuid,
  p_user_id uuid,
  p_action text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_kind public.chat_kind;
  v_actor_role public.member_role;
  v_target_role public.member_role;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  if p_user_id is null or p_action not in ('add', 'remove') then
    raise exception 'Invalid group member operation';
  end if;

  select c.kind into v_kind
    from public.chats c
   where c.id = p_chat_id;

  if v_kind is distinct from 'group'::public.chat_kind then
    raise exception 'Group not found';
  end if;

  select m.role into v_actor_role
    from public.chat_members m
   where m.chat_id = p_chat_id
     and m.user_id = auth.uid();

  if v_actor_role is null then
    raise exception 'Group membership required';
  end if;

  select m.role into v_target_role
    from public.chat_members m
   where m.chat_id = p_chat_id
     and m.user_id = p_user_id;

  if p_action = 'add' then
    if v_actor_role not in ('owner'::public.member_role, 'admin'::public.member_role) then
      raise exception 'Only group admins can add members';
    end if;

    insert into public.chat_members(chat_id, user_id, role)
    values (p_chat_id, p_user_id, 'member'::public.member_role)
    on conflict (chat_id, user_id) do nothing;
    return;
  end if;

  -- A member may leave; admins may remove regular members; owners control admins.
  if p_user_id = auth.uid() then
    if v_actor_role = 'owner'::public.member_role then
      raise exception 'Transfer ownership before leaving the group';
    end if;
  elsif v_actor_role = 'owner'::public.member_role then
    if v_target_role = 'owner'::public.member_role then
      raise exception 'Cannot remove the group owner';
    end if;
  elsif v_actor_role = 'admin'::public.member_role then
    if v_target_role is distinct from 'member'::public.member_role then
      raise exception 'Admins can remove regular members only';
    end if;
  else
    raise exception 'Only group admins can remove other members';
  end if;

  delete from public.chat_members
   where chat_id = p_chat_id
     and user_id = p_user_id;

  if not found then
    raise exception 'Group member not found';
  end if;
end;
$$;

revoke all on function public.manage_group_member(uuid, uuid, text) from public;
revoke all on function public.manage_group_member(uuid, uuid, text) from anon;
grant execute on function public.manage_group_member(uuid, uuid, text) to authenticated;

comment on function public.manage_group_member(uuid, uuid, text)
is 'Adds/removes group members with server-enforced owner/admin permissions; group owners must transfer ownership before leaving.';
