create table if not exists public.call_sessions (
  id uuid primary key default gen_random_uuid(),
  chat_id uuid not null references public.chats(id) on delete cascade,
  caller_id uuid not null references auth.users(id) on delete cascade,
  callee_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'ringing'
    check (status in ('ringing', 'accepted', 'rejected', 'ended', 'missed')),
  offer_sdp text not null,
  answer_sdp text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint call_distinct_users check (caller_id <> callee_id)
);

create index if not exists call_sessions_callee_status_idx
  on public.call_sessions(callee_id, status, created_at desc);
create index if not exists call_sessions_caller_created_idx
  on public.call_sessions(caller_id, created_at desc);

alter table public.call_sessions enable row level security;

create policy "Call participants can read calls"
on public.call_sessions for select to authenticated
using (
  auth.uid() in (caller_id, callee_id)
  and public.is_chat_member(chat_id)
);

create policy "Chat members can start direct calls"
on public.call_sessions for insert to authenticated
with check (
  caller_id = auth.uid()
  and public.is_chat_member(chat_id)
  and exists (
    select 1 from public.chat_members m
    where m.chat_id = call_sessions.chat_id
      and m.user_id = call_sessions.callee_id
  )
);

create policy "Call participants can update calls"
on public.call_sessions for update to authenticated
using (auth.uid() in (caller_id, callee_id))
with check (auth.uid() in (caller_id, callee_id));

create table if not exists public.call_ice_candidates (
  id uuid primary key default gen_random_uuid(),
  call_id uuid not null references public.call_sessions(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  candidate jsonb not null,
  created_at timestamptz not null default now()
);

create index if not exists call_ice_candidates_call_created_idx
  on public.call_ice_candidates(call_id, created_at);

alter table public.call_ice_candidates enable row level security;

create policy "Call participants can read ICE candidates"
on public.call_ice_candidates for select to authenticated
using (
  exists (
    select 1 from public.call_sessions c
    where c.id = call_ice_candidates.call_id
      and auth.uid() in (c.caller_id, c.callee_id)
  )
);

create policy "Call participants can send ICE candidates"
on public.call_ice_candidates for insert to authenticated
with check (
  sender_id = auth.uid()
  and exists (
    select 1 from public.call_sessions c
    where c.id = call_ice_candidates.call_id
      and auth.uid() in (c.caller_id, c.callee_id)
  )
);

grant select, insert, update on public.call_sessions to authenticated;
grant select, insert on public.call_ice_candidates to authenticated;
