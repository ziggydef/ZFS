-- Prevent self-service subscription to private channels.
-- Private-channel invitations will be handled by a separate trusted invite flow.
drop policy if exists "Users subscribe as themselves" on public.channel_subscriptions;
create policy "Users subscribe to public channels"
on public.channel_subscriptions for insert to authenticated
with check (
  user_id = auth.uid()
  and exists (
    select 1
      from public.channels c
     where c.id = channel_id
       and c.is_public = true
  )
);

-- An author may edit a post only while they still own its channel.
drop policy if exists "Authors edit own channel posts" on public.channel_posts;
create policy "Channel owners edit own posts"
on public.channel_posts for update to authenticated
using (
  author_id = auth.uid()
  and exists (
    select 1 from public.channels c
    where c.id = channel_id and c.owner_id = auth.uid()
  )
)
with check (
  author_id = auth.uid()
  and exists (
    select 1 from public.channels c
    where c.id = channel_id and c.owner_id = auth.uid()
  )
);

comment on policy "Users subscribe to public channels" on public.channel_subscriptions
is 'Only public channels accept self-service subscriptions; private channels require an invite flow.';
