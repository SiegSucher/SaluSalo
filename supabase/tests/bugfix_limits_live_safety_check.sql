-- Salu-Salo bug fix pass, round 2: safety check for Storage and rate limits (audit #24, #25).
--
-- Run it in the Supabase dashboard after migrations/20261009_bugfix_storage_and_rate_limits.sql:
-- SQL Editor -> New query -> paste this whole file -> Run. Leave the role on
-- "postgres".
--
-- It checks the two buckets' limits, then takes the donor of the newest
-- listing (or the newest account) and the newest other account (the
-- "recipient"). As them it posts a test listing of 20 packs, opens a chat and
-- sends messages up to the per-minute limit, and reserves up to the
-- 10-minute limit; the next message and reservation must be refused, and the
-- donor must still be able to reply. The limits count the recipient's real
-- recent messages and reservations too, so the check works out how many are
-- left first.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did, so nothing is saved, no alert is kept and no phone gets a live
-- update. The error text is the report; it starts with "SAFETY CHECK FINISHED".
-- Any other error means the check itself did not run.

do $$
declare
  v_donor      public.users;
  v_rec        public.users;
  c_donor      text;
  c_rec        text;
  c_iss        text := 'https://securetoken.google.com/salusalo';
  v_post_id    uuid := gen_random_uuid();
  v_conv       public.conversations;
  v_left       integer;
  v_qty        integer;
  n            bigint;
  v_ready      boolean := false;
  v_label      text;
  v_ok         boolean;
  v_note       text;
  a_label      text[] := '{}';
  a_ok         boolean[] := '{}';
  a_note       text[] := '{}';
  v_report     text := '';
  v_pass       integer := 0;
  v_fail       integer := 0;
  v_skip       integer := 0;
begin
  select u.* into v_donor from public.users u
  join public.food_posts p on p.donor_id = u.id
  order by p.created_at desc limit 1;
  if v_donor.id is null then
    select * into v_donor from public.users order by created_at desc limit 1;
  end if;
  select * into v_rec from public.users
  where id is distinct from v_donor.id order by created_at desc limit 1;
  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;

  v_label := 'Both photo buckets take only images, at most 5 MB each, and stay private';
  select count(*) into n from storage.buckets
  where id in ('food-images', 'profile-images') and public = false
    and file_size_limit = 5242880
    and allowed_mime_types @> array['image/jpeg']
    and allowed_mime_types <@ array['image/jpeg', 'image/png', 'image/webp'];
  v_ok := n = 2;
  v_note := format('%s of 2 buckets set', n);
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the donor posts a test listing (20 packs) and the recipient opens a chat about it';
  v_ok := null; v_note := 'fewer than two accounts';
  if v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price)
      values (v_post_id, v_donor.id, 'Safety check: limits', 'Food', 20, 'packs',
        'Safety check plaza', now() + interval '1 day', 'AVAILABLE', 0);
      perform set_config('request.jwt.claims', c_rec, true);
      select * into v_conv from public.start_listing_conversation(v_post_id);
      perform set_config('role', 'postgres', true);
      v_ready := v_conv.id is not null;
      v_ok := v_ready;
      v_note := case when v_ready then 'chat opened' else 'no chat' end;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The recipient can send up to 20 messages a minute, and the next one is refused';
  v_ok := null; v_note := 'the sanity check did not pass';
  if v_ready then
    v_ok := false;
    begin
      select greatest(20 - count(*), 0) into v_left from public.messages
      where sender_id = v_rec.id and created_at > now() - interval '1 minute';
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      for i in 1 .. v_left loop
        perform public.send_message(v_conv.id, 'Safety check message ' || i);
      end loop;
      begin
        perform public.send_message(v_conv.id, 'One too many');
        v_note := format('%s sent, then one more was allowed', v_left);
      exception when others then
        v_ok := sqlerrm = 'You are sending messages too quickly. Wait a minute and try again.';
        v_note := format('%s sent, then: %s', v_left, sqlerrm);
      end;
      perform set_config('role', 'postgres', true);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false;
      v_note := 'a message within the limit was refused: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The donor can still reply (the limit is per person)';
  v_ok := null; v_note := 'the sanity check did not pass';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.send_message(v_conv.id, 'Safety check reply');
      perform set_config('role', 'postgres', true);
      v_ok := true;
      v_note := 'sent';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The recipient can make up to 10 reservations in 10 minutes; the next is refused and changes nothing';
  v_ok := null; v_note := 'the sanity check did not pass';
  if v_ready then
    v_ok := false;
    begin
      select greatest(10 - count(*), 0) into v_left from public.reservations
      where recipient_id = v_rec.id and created_at > now() - interval '10 minutes';
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      for i in 1 .. v_left loop
        perform public.create_reservation_at_price(v_post_id, 1, 0);
      end loop;
      begin
        perform public.create_reservation_at_price(v_post_id, 1, 0);
        v_note := format('%s made, then one more was allowed', v_left);
      exception when others then
        perform set_config('role', 'postgres', true);
        select quantity into v_qty from public.food_posts where id = v_post_id;
        v_ok := sqlerrm = 'You made many reservations in a short time. Wait a few minutes and try again.'
          and v_qty = 20 - v_left;
        v_note := format('%s made, %s packs left, then: %s', v_left, v_qty, sqlerrm);
      end;
      perform set_config('role', 'postgres', true);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false;
      v_note := 'a reservation within the limit was refused: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  for i in 1 .. coalesce(array_length(a_label, 1), 0) loop
    if a_ok[i] is null then
      v_skip := v_skip + 1;
    elsif a_ok[i] then
      v_pass := v_pass + 1;
    else
      v_fail := v_fail + 1;
    end if;
    v_report := v_report || format(E'\n%s %s. %s: %s',
      case when a_ok[i] is null then 'SKIP' when a_ok[i] then 'PASS' else 'FAIL' end,
      i, a_label[i], coalesce(a_note[i], '-'));
  end loop;

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'), v_report);
end $$;
