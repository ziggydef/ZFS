-- becamce initial data model
-- Apply through Supabase SQL Editor after creating a Supabase project.
create extension if not exists pgcrypto;

create type public.chat_kind as enum ('direct', 'group', 'secret');
create type public.member_role as enum ('owner', 'admin', 'member');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  display_name text not null default '',
  avatar_path text,
  bio text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint username_format check (username is null or username ~ '^[a-zA-Z0-9_]{3,32}$')
);

create table public.chats (
  id uuid primary key default gen_random_uuid(),
  kind public.chat_kind not null,
  title text,
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.chat_members (
  chat_id uuid not null references public.chats(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role public.member_role not null default 'member',
  joined_at timestamptz not null default now(),
  last_read_at timestamptz,
  primary key (chat_id, user_id)
);

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  chat_id uuid not null references public.chats(id) on delete cascade,
  sender_id uuid not null references auth.users(id),
  body text,
  media_path text,
  media_mime text,
  reply_to uuid references public.messages(id) on delete set null,
  edited_at timestamptz,
  expires_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  constraint message_has_content check (body is not null or media_path is not null)
);

create index messages_chat_created_idx on public.messages(chat_id, created_at desc);
create index chat_members_user_idx on public.chat_members(user_id, chat_id);
create index messages_expiry_idx on public.messages(expires_at) where expires_at is not null;

create table public.channels (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id),
  title text not null,
  username text unique,
  description text not null default '',
  is_public boolean not null default false,
  created_at timestamptz not null default now()
);

create table public.channel_subscriptions (
  channel_id uuid not null references public.channels(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  subscribed_at timestamptz not null default now(),
  primary key (channel_id, user_id)
);

create table public.channel_posts (
  id uuid primary key default gen_random_uuid(),
  channel_id uuid not null references public.channels(id) on delete cascade,
  author_id uuid not null references auth.users(id),
  body text,
  media_path text,
  created_at timestamptz not null default now(),
  edited_at timestamptz,
  deleted_at timestamptz,
  constraint post_has_content check (body is not null or media_path is not null)
);

create index channel_posts_channel_created_idx on public.channel_posts(channel_id, created_at desc);

alter table public.profiles enable row level security;
alter table public.chats enable row level security;
alter table public.chat_members enable row level security;
alter table public.messages enable row level security;
alter table public.channels enable row level security;
alter table public.channel_subscriptions enable row level security;
alter table public.channel_posts enable row level security;

-- SECURITY DEFINER avoids recursive RLS evaluation when membership policies
-- need to ask whether the current user belongs to a chat.
create or replace function public.is_chat_member(p_chat_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.chat_members m
    where m.chat_id = p_chat_id and m.user_id = auth.uid()
  );
$$;

create policy "Profiles visible to authenticated users"
on public.profiles for select to authenticated using (true);
create policy "Users create own profile"
on public.profiles for insert to authenticated with check (auth.uid() = id);
create policy "Users update own profile"
on public.profiles for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

create policy "Members read chats"
on public.chats for select to authenticated
using (public.is_chat_member(id));
create policy "Authenticated users create chats"
on public.chats for insert to authenticated with check (created_by = auth.uid());

create policy "Members read membership"
on public.chat_members for select to authenticated
using (user_id = auth.uid() or public.is_chat_member(chat_id));
create policy "Chat creator adds first membership"
on public.chat_members for insert to authenticated
with check (
  user_id = auth.uid()
  and exists (select 1 from public.chats c where c.id = chat_id and c.created_by = auth.uid())
);

create policy "Chat members read messages"
on public.messages for select to authenticated
using (public.is_chat_member(messages.chat_id));
create policy "Members send as themselves"
on public.messages for insert to authenticated
with check (
  sender_id = auth.uid()
  and public.is_chat_member(messages.chat_id)
);
create policy "Authors edit own messages"
on public.messages for update to authenticated
using (sender_id = auth.uid())
with check (sender_id = auth.uid());

create policy "Public channels or subscribers can read channel"
on public.channels for select to authenticated
using (is_public or owner_id = auth.uid() or exists (
  select 1 from public.channel_subscriptions s where s.channel_id = id and s.user_id = auth.uid()
));
create policy "Users create channels"
on public.channels for insert to authenticated with check (owner_id = auth.uid());
create policy "Owners update channels"
on public.channels for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());

create policy "Users read own subscriptions"
on public.channel_subscriptions for select to authenticated using (user_id = auth.uid());
create policy "Users subscribe as themselves"
on public.channel_subscriptions for insert to authenticated with check (user_id = auth.uid());
create policy "Users unsubscribe themselves"
on public.channel_subscriptions for delete to authenticated using (user_id = auth.uid());

create policy "Subscribers read channel posts"
on public.channel_posts for select to authenticated
using (exists (
  select 1 from public.channels c
  where c.id = channel_id and (
    c.is_public or c.owner_id = auth.uid() or exists (
      select 1 from public.channel_subscriptions s where s.channel_id = c.id and s.user_id = auth.uid()
    )
  )
));
create policy "Channel owners publish posts"
on public.channel_posts for insert to authenticated
with check (author_id = auth.uid() and exists (
  select 1 from public.channels c where c.id = channel_id and c.owner_id = auth.uid()
));
create policy "Authors edit own channel posts"
on public.channel_posts for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());

-- Profile rows are created by the auth trigger in migration 0003.
-- Never put a Supabase service-role key in the Android application.
