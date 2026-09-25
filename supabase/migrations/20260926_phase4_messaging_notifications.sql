-- Salu-Salo Phase 4: messaging, in-app notifications and notification preferences.
-- Run after the Phase 1, 2 and 3 migrations (including the Phase 3 donor-names patch).

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

-- One thread per listing and recipient. donor_id is copied from the listing by
-- the RPCs below; clients never supply it.
create table if not exists public.conversations (
    id uuid primary key default gen_random_uuid(),
    food_post_id uuid not null references public.food_posts(id) on delete cascade,
    recipient_id uuid not null references public.users(id) on delete cascade,
    donor_id uuid not null references public.users(id) on delete cascade,
    recipient_last_read_at timestamptz,
    donor_last_read_at timestamptz,
    last_message_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (food_post_id, recipient_id),
    check (recipient_id <> donor_id)
);
create index if not exists conversations_recipient_idx on public.conversations(recipient_id);
create index if not exists conversations_donor_idx on public.conversations(donor_id);
drop trigger if exists conversations_set_updated_at on public.conversations;
create trigger conversations_set_updated_at before update on public.conversations
for each row execute function public.set_updated_at();

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    sender_id uuid not null references public.users(id) on delete cascade,
    body text not null check (length(trim(body)) between 1 and 2000),
    created_at timestamptz not null default now()
);
create index if not exists messages_conversation_idx on public.messages(conversation_id, created_at);

create table if not exists public.notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    type text not null check (type in (
        'MESSAGE', 'RESERVATION_CREATED', 'RESERVATION_CANCELLED', 'RESERVATION_CLAIMED'
    )),
    title text not null,
    body text not null,
    food_post_id uuid references public.food_posts(id) on delete cascade,
    conversation_id uuid references public.conversations(id) on delete cascade,
    reservation_id uuid references public.reservations(id) on delete cascade,
    read_at timestamptz,
    created_at timestamptz not null default now()
);
create index if not exists notifications_user_idx on public.notifications(user_id, created_at desc);

-- A missing row means every alert type is on.
create table if not exists public.notification_preferences (
    user_id uuid primary key references public.users(id) on delete cascade,
    message_alerts boolean not null default true,
    reservation_alerts boolean not null default true,
    recommendations boolean not null default true,
    pickup_reminders boolean not null default true,
    updated_at timestamptz not null default now()
);
drop trigger if exists notification_preferences_set_updated_at on public.notification_preferences;
create trigger notification_preferences_set_updated_at before update on public.notification_preferences
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Grants and RLS. Conversations, messages and notifications are read-only for
-- clients; every write goes through the SECURITY DEFINER functions below.
-- ---------------------------------------------------------------------------

alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.notifications enable row level security;
alter table public.notification_preferences enable row level security;

revoke all on public.conversations from anon;
revoke all on public.conversations from authenticated;
revoke all on public.messages from anon;
revoke all on public.messages from authenticated;
revoke all on public.notifications from anon;
revoke all on public.notifications from authenticated;
revoke all on public.notification_preferences from anon;
revoke all on public.notification_preferences from authenticated;

grant select on public.conversations to authenticated;
grant select on public.messages to authenticated;
grant select on public.notifications to authenticated;
grant select, insert, update on public.notification_preferences to authenticated;

drop policy if exists "Participants may view their conversations" on public.conversations;
create policy "Participants may view their conversations"
on public.conversations for select to authenticated using (
  (select public.is_salusalo_firebase_jwt()) and (
    recipient_id = (select public.current_salusalo_profile_id()) or
    donor_id = (select public.current_salusalo_profile_id())
  )
);

drop policy if exists "Participants may view messages in their conversations" on public.messages;
create policy "Participants may view messages in their conversations"
on public.messages for select to authenticated using (
  (select public.is_salusalo_firebase_jwt()) and
  exists (select 1 from public.conversations c where c.id = conversation_id and (
    c.recipient_id = (select public.current_salusalo_profile_id()) or
    c.donor_id = (select public.current_salusalo_profile_id())
  ))
);

drop policy if exists "Users may view their own notifications" on public.notifications;
create policy "Users may view their own notifications"
on public.notifications for select to authenticated using (
  (select public.is_salusalo_firebase_jwt()) and
  user_id = (select public.current_salusalo_profile_id())
);

drop policy if exists "Users may view their own notification preferences" on public.notification_preferences;
create policy "Users may view their own notification preferences"
on public.notification_preferences for select to authenticated using (
  (select public.is_salusalo_firebase_jwt()) and
  user_id = (select public.current_salusalo_profile_id())
);

drop policy if exists "Users may create their own notification preferences" on public.notification_preferences;
create policy "Users may create their own notification preferences"
on public.notification_preferences for insert to authenticated with check (
  (select public.is_salusalo_firebase_jwt()) and
  user_id = (select public.current_salusalo_profile_id())
);

drop policy if exists "Users may update their own notification preferences" on public.notification_preferences;
create policy "Users may update their own notification preferences"
on public.notification_preferences for update to authenticated using (
  (select public.is_salusalo_firebase_jwt()) and
  user_id = (select public.current_salusalo_profile_id())
) with check (
  user_id = (select public.current_salusalo_profile_id())
);

-- ---------------------------------------------------------------------------
-- Internal: create an alert if the user's preferences allow it. Not callable
-- by clients.
-- ---------------------------------------------------------------------------

create or replace function public.notify_user(
  p_user_id uuid, p_type text, p_title text, p_body text,
  p_food_post_id uuid default null, p_conversation_id uuid default null, p_reservation_id uuid default null
)
returns void
language plpgsql security definer set search_path = public as $$
declare v_prefs public.notification_preferences;
begin
  if p_user_id is null then return; end if;
  select * into v_prefs from public.notification_preferences where user_id = p_user_id;
  if found then
    if p_type = 'MESSAGE' and not v_prefs.message_alerts then return; end if;
    if p_type like 'RESERVATION_%' and not v_prefs.reservation_alerts then return; end if;
  end if;
  insert into public.notifications(user_id, type, title, body, food_post_id, conversation_id, reservation_id)
  values (p_user_id, p_type, p_title, left(p_body, 300), p_food_post_id, p_conversation_id, p_reservation_id);
end; $$;
revoke all on function public.notify_user(uuid, text, text, text, uuid, uuid, uuid) from public;
revoke all on function public.notify_user(uuid, text, text, text, uuid, uuid, uuid) from anon;
revoke all on function public.notify_user(uuid, text, text, text, uuid, uuid, uuid) from authenticated;

-- ---------------------------------------------------------------------------
-- Reservation alerts. These triggers sit beside the Phase 3 RPCs without
-- changing them.
-- ---------------------------------------------------------------------------

create or replace function public.notify_reservation_change()
returns trigger
language plpgsql security definer set search_path = public as $$
declare v_post public.food_posts; v_recipient_name text;
begin
  select * into v_post from public.food_posts where id = new.food_post_id;
  if not found then return new; end if;
  select name into v_recipient_name from public.users where id = new.recipient_id;
  if tg_op = 'INSERT' then
    perform public.notify_user(v_post.donor_id, 'RESERVATION_CREATED', 'New reservation',
      coalesce(v_recipient_name, 'Someone') || ' reserved ' || new.quantity || ' ' || v_post.unit || ' of ' || v_post.name || '.',
      v_post.id, null, new.id);
  elsif new.status is distinct from old.status then
    if new.status = 'CANCELLED' then
      perform public.notify_user(v_post.donor_id, 'RESERVATION_CANCELLED', 'Reservation cancelled',
        coalesce(v_recipient_name, 'Someone') || ' cancelled their reservation of ' || v_post.name || '. The quantity is available again.',
        v_post.id, null, new.id);
    elsif new.status = 'CLAIMED' then
      perform public.notify_user(new.recipient_id, 'RESERVATION_CLAIMED', 'Reservation claimed',
        'The donor marked your reservation of ' || v_post.name || ' as claimed.',
        v_post.id, null, new.id);
    end if;
  end if;
  return new;
end; $$;
revoke all on function public.notify_reservation_change() from public;

drop trigger if exists reservations_notify on public.reservations;
create trigger reservations_notify after insert or update on public.reservations
for each row execute function public.notify_reservation_change();

-- ---------------------------------------------------------------------------
-- Messaging RPCs. Identity always comes from the JWT.
-- ---------------------------------------------------------------------------

-- A recipient starts (or reopens) their thread with a listing's donor.
create or replace function public.start_listing_conversation(p_food_post_id uuid)
returns public.conversations
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_post public.food_posts; v_conversation public.conversations;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  select * into v_post from public.food_posts where id = p_food_post_id;
  if not found then raise exception 'Food post not found'; end if;
  if v_post.donor_id = v_profile then raise exception 'You cannot message yourself about your own listing'; end if;
  select * into v_conversation from public.conversations
  where food_post_id = p_food_post_id and recipient_id = v_profile;
  if found then return v_conversation; end if;
  if not ((v_post.status = 'AVAILABLE' and v_post.expiration_date > now())
          or exists (select 1 from public.reservations r
                     where r.food_post_id = p_food_post_id and r.recipient_id = v_profile)) then
    raise exception 'This listing is no longer available';
  end if;
  insert into public.conversations(food_post_id, recipient_id, donor_id)
  values (p_food_post_id, v_profile, v_post.donor_id)
  on conflict (food_post_id, recipient_id) do nothing
  returning * into v_conversation;
  if v_conversation.id is null then
    select * into v_conversation from public.conversations
    where food_post_id = p_food_post_id and recipient_id = v_profile;
  end if;
  return v_conversation;
end; $$;

-- Either party of a reservation opens the thread between that recipient and the donor.
create or replace function public.open_reservation_conversation(p_reservation_id uuid)
returns public.conversations
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_reservation public.reservations; v_donor uuid; v_conversation public.conversations;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  select * into v_reservation from public.reservations where id = p_reservation_id;
  if not found then raise exception 'Reservation not found'; end if;
  select donor_id into v_donor from public.food_posts where id = v_reservation.food_post_id;
  if not found then raise exception 'Food post not found'; end if;
  if v_profile <> v_reservation.recipient_id and v_profile <> v_donor then
    raise exception 'You are not part of this reservation';
  end if;
  insert into public.conversations(food_post_id, recipient_id, donor_id)
  values (v_reservation.food_post_id, v_reservation.recipient_id, v_donor)
  on conflict (food_post_id, recipient_id) do nothing
  returning * into v_conversation;
  if v_conversation.id is null then
    select * into v_conversation from public.conversations
    where food_post_id = v_reservation.food_post_id and recipient_id = v_reservation.recipient_id;
  end if;
  return v_conversation;
end; $$;

create or replace function public.send_message(p_conversation_id uuid, p_body text)
returns public.messages
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_conversation public.conversations; v_message public.messages;
        v_body text; v_sender_name text; v_food_name text; v_other uuid;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  v_body := trim(coalesce(p_body, ''));
  if length(v_body) = 0 then raise exception 'Type a message first'; end if;
  if length(v_body) > 2000 then raise exception 'Messages can be at most 2000 characters'; end if;
  select * into v_conversation from public.conversations where id = p_conversation_id for update;
  if not found then raise exception 'Conversation not found'; end if;
  if v_profile <> v_conversation.recipient_id and v_profile <> v_conversation.donor_id then
    raise exception 'You are not part of this conversation';
  end if;
  insert into public.messages(conversation_id, sender_id, body)
  values (p_conversation_id, v_profile, v_body) returning * into v_message;
  update public.conversations set
    last_message_at = v_message.created_at,
    recipient_last_read_at = case when v_profile = recipient_id then v_message.created_at else recipient_last_read_at end,
    donor_last_read_at = case when v_profile = donor_id then v_message.created_at else donor_last_read_at end
  where id = p_conversation_id;
  select name into v_sender_name from public.users where id = v_profile;
  select name into v_food_name from public.food_posts where id = v_conversation.food_post_id;
  v_other := case when v_profile = v_conversation.donor_id then v_conversation.recipient_id else v_conversation.donor_id end;
  perform public.notify_user(v_other, 'MESSAGE',
    'New message from ' || coalesce(v_sender_name, 'a Salu-Salo user'),
    coalesce(v_food_name || ': ', '') || v_body,
    v_conversation.food_post_id, p_conversation_id, null);
  return v_message;
end; $$;

create or replace function public.mark_conversation_read(p_conversation_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_conversation public.conversations;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  select * into v_conversation from public.conversations where id = p_conversation_id;
  if not found then raise exception 'Conversation not found'; end if;
  if v_profile = v_conversation.recipient_id then
    update public.conversations set recipient_last_read_at = now() where id = p_conversation_id;
  elsif v_profile = v_conversation.donor_id then
    update public.conversations set donor_last_read_at = now() where id = p_conversation_id;
  else
    raise exception 'You are not part of this conversation';
  end if;
  update public.notifications set read_at = now()
  where user_id = v_profile and conversation_id = p_conversation_id and read_at is null;
end; $$;

-- p_ids = null marks every unread alert of the caller as read.
create or replace function public.mark_notifications_read(p_ids uuid[] default null)
returns integer
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_count integer;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  update public.notifications set read_at = now()
  where user_id = v_profile and read_at is null and (p_ids is null or id = any(p_ids));
  get diagnostics v_count = row_count;
  return v_count;
end; $$;

-- The caller's threads with the listing, the other participant's display name
-- only, the last message and the unread count. p_conversation_id narrows it to
-- one thread.
create or replace function public.get_my_conversations(p_conversation_id uuid default null)
returns table (
    id uuid, food_post_id uuid, food_name text, food_image_path text,
    is_donor boolean, other_name text, last_message text, last_message_mine boolean,
    last_message_at timestamptz, created_at timestamptz, unread_count integer
)
language sql stable security definer set search_path = public
as $$
  with me as (
    select public.current_salusalo_profile_id() as id
    where public.is_salusalo_firebase_jwt()
  )
  select c.id, c.food_post_id, p.name, p.image_url,
         c.donor_id = me.id,
         case when c.donor_id = me.id then ru.name else du.name end,
         lm.body, lm.sender_id = me.id,
         c.last_message_at, c.created_at,
         (select count(*) from public.messages m
          where m.conversation_id = c.id and m.sender_id <> me.id
            and m.created_at > coalesce(
              case when c.donor_id = me.id then c.donor_last_read_at else c.recipient_last_read_at end,
              '-infinity'::timestamptz))::integer
  from me
  join public.conversations c on me.id in (c.donor_id, c.recipient_id)
  join public.food_posts p on p.id = c.food_post_id
  join public.users du on du.id = c.donor_id
  join public.users ru on ru.id = c.recipient_id
  left join lateral (
    select m.body, m.sender_id from public.messages m
    where m.conversation_id = c.id order by m.created_at desc limit 1
  ) lm on true
  where p_conversation_id is null or c.id = p_conversation_id
  order by coalesce(c.last_message_at, c.created_at) desc
$$;

revoke all on function public.start_listing_conversation(uuid) from public;
revoke all on function public.open_reservation_conversation(uuid) from public;
revoke all on function public.send_message(uuid, text) from public;
revoke all on function public.mark_conversation_read(uuid) from public;
revoke all on function public.mark_notifications_read(uuid[]) from public;
revoke all on function public.get_my_conversations(uuid) from public;
grant execute on function public.start_listing_conversation(uuid) to authenticated;
grant execute on function public.open_reservation_conversation(uuid) to authenticated;
grant execute on function public.send_message(uuid, text) to authenticated;
grant execute on function public.mark_conversation_read(uuid) to authenticated;
grant execute on function public.mark_notifications_read(uuid[]) to authenticated;
grant execute on function public.get_my_conversations(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Realtime: stream new messages and alerts. Realtime applies the SELECT
-- policies above, so each client only receives rows it may read.
-- ---------------------------------------------------------------------------

do $$
begin
  if not exists (select 1 from pg_publication_tables
                 where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'messages') then
    alter publication supabase_realtime add table public.messages;
  end if;
  if not exists (select 1 from pg_publication_tables
                 where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'notifications') then
    alter publication supabase_realtime add table public.notifications;
  end if;
end $$;
