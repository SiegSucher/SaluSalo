-- Salu-Salo bug fix pass, round 2, group 3: Storage limits (audit #24) and rate limits (#25).
-- Run after 20261008_bugfix_one_rating_per_reviewer.sql. Safe to run again,
-- and safe on the live data: it changes the two buckets' settings, adds two
-- indexes and redefines send_message() and create_reservation() with the same
-- arguments, results, checks and texts plus a rate limit. Every app build keeps
-- working: the app uploads only JPEG photos of well under 5 MB, sent as
-- image/jpeg (supabase-kt takes the type from the ".jpg" file name).

-- ---------------------------------------------------------------------------
-- 1. (#24) Bucket limits: images only, at most 5 MB each. Before, a client
-- could store any file type up to the project's limit in its own folder.
-- Listing photos are shrunk to a 1600 px JPEG and profile photos to 512 px
-- before upload, typically well under 1 MB.
-- Orphaned files are NOT deleted automatically: supabase/tests/storage_orphans_query.sql
-- lists them (read-only) for a person to review and delete by hand.
-- ---------------------------------------------------------------------------

update storage.buckets
set file_size_limit = 5242880,
    allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp']
where id in ('food-images', 'profile-images');

-- ---------------------------------------------------------------------------
-- 2. (#25) Rate limits on the two actions that alert another person.
-- Counted per signed-in profile under a per-profile advisory lock (as
-- submit_report does), so parallel calls can't slip past the count.
--   * send_message: at most 20 messages per minute.
--   * create_reservation (and so create_reservation_at_price): at most 10
--     reservations per 10 minutes. This also bounds reserve/cancel loops,
--     because each cancel needs a reservation.
-- start_listing_conversation needs no limit (one chat per listing and
-- recipient), and cancel_reservation is bounded by the reservation limit.
-- ---------------------------------------------------------------------------

create index if not exists messages_sender_created_idx on public.messages (sender_id, created_at desc);
create index if not exists reservations_recipient_created_idx on public.reservations (recipient_id, created_at desc);

-- The 20260926 send_message, plus the limit.
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
  perform pg_advisory_xact_lock(hashtextextended('salusalo-message:' || v_profile::text, 0));
  if (select count(*) from public.messages m
      where m.sender_id = v_profile and m.created_at > now() - interval '1 minute') >= 20 then
    raise exception 'You are sending messages too quickly. Wait a minute and try again.';
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
revoke all on function public.send_message(uuid, text) from public;
revoke all on function public.send_message(uuid, text) from anon;
grant execute on function public.send_message(uuid, text) to authenticated;

-- The 20261006 create_reservation, plus the limit (after the listing checks,
-- so a refused listing keeps its usual message).
create or replace function public.create_reservation(p_food_post_id uuid, p_quantity integer)
returns public.reservations
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_post public.food_posts; v_reservation public.reservations;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  if p_quantity is null or p_quantity <= 0 then raise exception 'Choose a quantity greater than zero'; end if;
  select * into v_post from public.food_posts where id = p_food_post_id for update;
  if not found or v_post.expiration_date <= now() then raise exception 'Food post is no longer available'; end if;
  if v_post.status = 'REMOVED' then raise exception 'The donor removed this listing.'; end if;
  if v_post.pickup_end is not null and v_post.pickup_end <= now() then
    raise exception 'The pickup time for this listing has ended.';
  end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  if v_post.donor_id = v_profile then raise exception 'You cannot reserve your own food post'; end if;
  if v_post.status <> 'AVAILABLE' or v_post.quantity < p_quantity then
    raise exception 'Not enough food available. Only % remains.', greatest(v_post.quantity, 0);
  end if;
  perform pg_advisory_xact_lock(hashtextextended('salusalo-reserve:' || v_profile::text, 0));
  if (select count(*) from public.reservations r
      where r.recipient_id = v_profile and r.created_at > now() - interval '10 minutes') >= 10 then
    raise exception 'You made many reservations in a short time. Wait a few minutes and try again.';
  end if;
  insert into public.reservations(food_post_id, recipient_id, quantity)
  values (p_food_post_id, v_profile, p_quantity) returning * into v_reservation;
  update public.food_posts set quantity = quantity - p_quantity,
    status = case when quantity - p_quantity = 0 then 'RESERVED' else 'AVAILABLE' end
  where id = p_food_post_id;
  return v_reservation;
end; $$;
revoke all on function public.create_reservation(uuid, integer) from public;
revoke all on function public.create_reservation(uuid, integer) from anon;
grant execute on function public.create_reservation(uuid, integer) to authenticated;
