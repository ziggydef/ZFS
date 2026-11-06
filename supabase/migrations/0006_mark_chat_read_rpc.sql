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
