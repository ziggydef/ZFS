-- Secret-chat records must not be created until an audited end-to-end
-- encryption protocol and device verification are implemented.
-- This server-side guard prevents clients from bypassing the Android UI check.
create or replace function public.reject_unimplemented_secret_chats()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.kind = 'secret'::public.chat_kind then
    raise exception 'Secret chats are not available until end-to-end encryption is implemented';
  end if;
  return new;
end;
$$;

drop trigger if exists reject_unimplemented_secret_chats on public.chats;
create trigger reject_unimplemented_secret_chats
before insert or update of kind on public.chats
for each row execute function public.reject_unimplemented_secret_chats();

revoke all on function public.reject_unimplemented_secret_chats() from public;
revoke all on function public.reject_unimplemented_secret_chats() from anon;
revoke all on function public.reject_unimplemented_secret_chats() from authenticated;

comment on function public.reject_unimplemented_secret_chats()
is 'Rejects secret-chat records until audited end-to-end encryption is available.';
