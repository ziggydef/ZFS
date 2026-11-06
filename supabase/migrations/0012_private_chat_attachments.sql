-- Private storage bucket for chat attachments.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'chat-attachments',
  'chat-attachments',
  false,
  20971520,
  array[
    'image/jpeg','image/png','image/webp','image/gif',
    'application/pdf',
    'text/plain',
    'application/zip',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation'
  ]
)
on conflict (id) do update
set public = false,
    file_size_limit = 20971520,
    allowed_mime_types = excluded.allowed_mime_types;

-- Object path: <sender UUID>/<chat UUID>/<unique filename>.
create policy "Chat members upload attachments as themselves"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'chat-attachments'
  and (storage.foldername(name))[1] = auth.uid()::text
  and case
    when (storage.foldername(name))[2] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    then public.is_chat_member(((storage.foldername(name))[2])::uuid)
    else false
  end
);

create policy "Chat members read attachments"
on storage.objects for select to authenticated
using (
  bucket_id = 'chat-attachments'
  and case
    when (storage.foldername(name))[2] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    then public.is_chat_member(((storage.foldername(name))[2])::uuid)
    else false
  end
);

create policy "Uploaders may delete own attachments"
on storage.objects for delete to authenticated
using (
  bucket_id = 'chat-attachments'
  and owner_id = auth.uid()::text
);

alter table public.messages
  add column if not exists media_file_name text;

alter table public.messages
  drop constraint if exists message_media_metadata_check;

alter table public.messages
  add constraint message_media_metadata_check
  check (
    media_path is null
    or (
      media_mime is not null
      and media_file_name is not null
      and length(media_file_name) between 1 and 180
    )
  );
