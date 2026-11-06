-- Restrict message mutations to narrowly scoped, server-checked RPCs.
-- Direct table UPDATE/DELETE would otherwise allow clients to alter more than
-- the message body (for example chat_id or created_at).
revoke update, delete on table public.messages from authenticated;

create or replace function public.edit_message(
  p_message_id uuid,
  p_body text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  if p_body is null or length(trim(p_body)) = 0 or length(p_body) > 8000 then
    raise exception 'Message text must contain 1 to 8000 characters';
  end if;

  update public.messages
     set body = p_body,
         edited_at = now()
   where id = p_message_id
     and sender_id = auth.uid()
     and public.is_chat_member(chat_id);

  if not found then
    raise exception 'Message not found or edit not allowed';
  end if;
end;
$$;

create or replace function public.delete_message(p_message_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  delete from public.messages
   where id = p_message_id
     and sender_id = auth.uid()
     and public.is_chat_member(chat_id);

  if not found then
    raise exception 'Message not found or delete not allowed';
  end if;
end;
$$;

revoke all on function public.edit_message(uuid, text) from public;
revoke all on function public.delete_message(uuid) from public;
grant execute on function public.edit_message(uuid, text) to authenticated;
grant execute on function public.delete_message(uuid) to authenticated;

comment on function public.edit_message(uuid, text)
is 'Edits only the authenticated author message body and server edit timestamp.';
comment on function public.delete_message(uuid)
is 'Permanently deletes only a message authored by the authenticated user.';
