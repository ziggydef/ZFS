create or replace function public.mark_chat_read(p_chat_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  update public.chat_members
     set last_read_at = now()
   where chat_id = p_chat_id
     and user_id = auth.uid();

  if not found then
    raise exception 'Chat not found or membership required';
  end if;
end;
$$;

revoke all on function public.mark_chat_read(uuid) from public;
grant execute on function public.mark_chat_read(uuid) to authenticated;

comment on function public.mark_chat_read(uuid)
is 'Marks the authenticated member read position for a chat.';

create or replace function public.bump_chat_updated_at()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.chats set updated_at = now() where id = new.chat_id;
  return new;
end;
$$;

drop trigger if exists messages_bump_chat_updated_at on public.messages;
create trigger messages_bump_chat_updated_at
after insert on public.messages
for each row execute function public.bump_chat_updated_at();

comment on function public.bump_chat_updated_at()
is 'Keeps chat ordering current when a new message is inserted.';
