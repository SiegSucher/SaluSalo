-- Salu-Salo bug fix pass, group C: safety check for the live database.
--
-- Run it in the Supabase dashboard after migrations/20261006_bugfix_reservations.sql:
-- SQL Editor -> New query -> paste this whole file -> Run. Leave the role on
-- "postgres".
--
-- It takes the donor of the newest listing (or the newest account), the
-- newest other account (the "recipient") and the next other account (the
-- "outsider"), plus a made-up Firebase account that has no SaluSalo profile.
-- As the donor it posts a test listing (3 packs at ₱25.00, pickup started an
-- hour ago), and checks:
--   * #8: a reservation goes through only at the listing's current price;
--   * #23: no reservation once the pickup time has ended;
--   * #9: only the donor can release an open reservation, which gives the
--     quantity back and alerts the recipient.
-- The "Sanity" checks must succeed: they prove that the pretend sign-in works,
-- so the refusals are real. A check that cannot run reports SKIP.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did, so nothing is saved, no alert is kept and no phone gets a live
-- update. The error text is the report; it starts with "SAFETY CHECK FINISHED".
-- Any other error means the check itself did not run.

do $$
declare
  v_donor      public.users;
  v_rec        public.users;
  v_out        public.users;
  v_res        public.reservations;
  v_other      public.reservations;
  c_donor      text;
  c_rec        text;
  c_out        text;
  c_ghost      text;
  c_iss        text := 'https://securetoken.google.com/salusalo';
  v_post_id    uuid := gen_random_uuid();
  v_status     text;
  v_reason     text;
  v_qty        integer;
  v_price      numeric;
  n            bigint;
  m            bigint;
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
  -- Who to test with.
  select u.* into v_donor from public.users u
  join public.food_posts p on p.donor_id = u.id
  order by p.created_at desc limit 1;
  if v_donor.id is null then
    select * into v_donor from public.users order by created_at desc limit 1;
  end if;
  select * into v_rec from public.users
  where id is distinct from v_donor.id order by created_at desc limit 1;
  select * into v_out from public.users
  where id is distinct from v_donor.id and id is distinct from v_rec.id
  order by created_at desc limit 1;

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_out := json_build_object('sub', v_out.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_ghost := json_build_object('sub', 'safety-check-no-profile-' || md5(random()::text), 'role', 'authenticated',
    'iss', c_iss, 'aud', 'salusalo')::text;

  -- ---------------------------------------------------------------------------
  -- #8: the price.
  -- ---------------------------------------------------------------------------

  v_label := 'Sanity: the donor posts a test listing and the recipient reserves 1 at ₱25.00';
  v_ok := null; v_note := 'fewer than two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_post_id, v_donor.id, 'Safety check: reservations', 'Food', 3, 'packs',
        'Safety check plaza', now() + interval '1 day', 'AVAILABLE', 25,
        now() - interval '1 hour', now() + interval '2 hours');
      perform set_config('request.jwt.claims', c_rec, true);
      select * into v_res from public.create_reservation_at_price(v_post_id, 1, 25.00);
      perform set_config('role', 'postgres', true);
      select quantity into v_qty from public.food_posts where id = v_post_id;
      v_ok := v_res.status = 'RESERVED' and v_res.unit_price = 25 and v_qty = 2;
      v_note := format('reservation %s at %s, %s left', v_res.status, v_res.unit_price, v_qty);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  for i in 1 .. 3 loop
    v_label := case i
      when 1 then 'Reserving at an old price (₱20.00) is refused and names the current price'
      when 2 then 'Reserving without a price is refused'
      else 'After the donor changes the price to ₱30.00, reserving at ₱25.00 is refused' end;
    v_ok := null; v_note := 'the sanity check above did not pass';
    if v_res.id is not null then
      v_ok := false;
      begin
        if i = 3 then
          perform set_config('request.jwt.claims', c_donor, true);
          perform set_config('role', 'authenticated', true);
          perform public.update_my_food_post(v_post_id, 'Safety check: reservations', null, 'Food', 30,
            'Safety check plaza', now() + interval '1 day', now() - interval '1 hour', now() + interval '2 hours',
            null, null, null);
        end if;
        perform set_config('request.jwt.claims', c_rec, true);
        perform set_config('role', 'authenticated', true);
        perform public.create_reservation_at_price(v_post_id, 1, case i when 1 then 20 when 2 then null else 25 end);
        perform set_config('role', 'postgres', true);
        v_note := 'it was reserved';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := sqlerrm like case when i = 3 then 'The price changed to ₱30.00 per unit.%'
                                  else 'The price changed to ₱25.00 per unit.%' end;
      end;
      select quantity into v_qty from public.food_posts where id = v_post_id;
      if v_qty <> 2 then
        v_ok := false; v_note := v_note || format(' (but %s left)', v_qty);
      end if;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  v_label := 'At the new price ₱30.00 the reservation goes through and records ₱30.00 (undone at once)';
  v_ok := null; v_note := 'the sanity check above did not pass';
  if v_res.id is not null then
    v_ok := false;
    begin
      begin
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        perform public.update_my_food_post(v_post_id, 'Safety check: reservations', null, 'Food', 30,
          'Safety check plaza', now() + interval '1 day', now() - interval '1 hour', now() + interval '2 hours',
          null, null, null);
        perform set_config('request.jwt.claims', c_rec, true);
        select * into v_other from public.create_reservation_at_price(v_post_id, 1, 30);
        perform set_config('role', 'postgres', true);
        raise exception 'UNDO_RESERVE';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_RESERVE' then raise; end if;
      end;
      v_ok := v_other.status = 'RESERVED' and v_other.unit_price = 30;
      v_note := format('%s at %s', v_other.status, v_other.unit_price);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A removed listing gives its usual refusal, not its price';
  v_ok := null; v_note := 'the sanity check above did not pass';
  if v_res.id is not null then
    v_ok := false;
    begin
      begin
        update public.food_posts set status = 'REMOVED' where id = v_post_id;
        perform set_config('request.jwt.claims', c_out, true);
        perform set_config('role', 'authenticated', true);
        begin
          perform public.create_reservation_at_price(v_post_id, 1, 1);
          v_note := 'it was reserved';
        exception when others then
          v_note := sqlerrm;
        end;
        perform set_config('role', 'postgres', true);
        raise exception 'UNDO_REMOVE';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_REMOVE' then raise; end if;
      end;
      v_ok := v_note = 'The donor removed this listing.';
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- #23: the end of the pickup window.
  -- ---------------------------------------------------------------------------

  v_label := 'Once the pickup time has ended, neither reserve function accepts a reservation';
  v_ok := null; v_note := 'the sanity check above did not pass';
  if v_res.id is not null and v_out.id is not null then
    v_ok := false;
    begin
      begin
        update public.food_posts
        set pickup_start = now() - interval '2 hours', pickup_end = now() - interval '1 minute'
        where id = v_post_id;
        perform set_config('request.jwt.claims', c_out, true);
        perform set_config('role', 'authenticated', true);
        begin
          perform public.create_reservation(v_post_id, 1);
          v_note := 'create_reservation reserved';
        exception when others then
          v_note := sqlerrm;
        end;
        begin
          perform public.create_reservation_at_price(v_post_id, 1, 25);
          v_note := v_note || ' / create_reservation_at_price reserved';
        exception when others then
          v_note := v_note || ' / ' || sqlerrm;
        end;
        perform set_config('role', 'postgres', true);
        raise exception 'UNDO_WINDOW';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_WINDOW' then raise; end if;
      end;
      v_ok := v_note = 'The pickup time for this listing has ended. / The pickup time for this listing has ended.';
    exception when others then
      v_note := sqlerrm;
    end;
  elsif v_out.id is null then
    v_note := 'no third account';
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- #9: releasing a reservation.
  -- ---------------------------------------------------------------------------

  for i in 1 .. 4 loop
    v_label := case i
      when 1 then 'The recipient cannot release their own reservation (only the donor can)'
      when 2 then 'The outsider cannot release someone else''s reservation'
      when 3 then 'An account without a profile cannot release a reservation'
      else 'Without a login, release_reservation is refused' end;
    v_ok := null; v_note := 'the sanity check above did not pass';
    if i = 2 and v_out.id is null then
      v_note := 'no third account';
    elsif v_res.id is not null then
      begin
        perform set_config('request.jwt.claims', case i when 1 then c_rec when 2 then c_out when 3 then c_ghost else '' end, true);
        perform set_config('role', case when i = 4 then 'anon' else 'authenticated' end, true);
        perform public.release_reservation(v_res.id);
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := 'it was released';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := case i when 3 then sqlerrm = 'Profile not found'
                       when 4 then sqlstate = '42501' or sqlerrm like '%Not authenticated%'
                       else sqlerrm like 'Only the food donor can release%' end;
      end;
      select status into v_status from public.reservations where id = v_res.id;
      if v_status <> 'RESERVED' then
        v_ok := false; v_note := v_note || format(' (but the reservation is now %s)', v_status);
      end if;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- The outsider first reserves the other 2 packs, so the listing is fully
  -- reserved; releasing 1 must make it available again with 1 pack.
  v_label := 'The donor releases it: cancelled as DONOR_RELEASED, 1 pack back on a fully reserved listing, recipient alerted (undone at once)';
  v_ok := null; v_note := 'the sanity check above did not pass';
  if v_res.id is not null and v_out.id is not null then
    v_ok := false;
    begin
      begin
        perform set_config('request.jwt.claims', c_out, true);
        perform set_config('role', 'authenticated', true);
        perform public.create_reservation_at_price(v_post_id, 2, 25);
        perform set_config('request.jwt.claims', c_donor, true);
        perform public.release_reservation(v_res.id);
        perform set_config('role', 'postgres', true);
        select r.status, r.cancel_reason, p.quantity, p.status into v_status, v_reason, v_qty, v_note
        from public.reservations r join public.food_posts p on p.id = r.food_post_id where r.id = v_res.id;
        select count(*) into n from public.notifications
        where user_id = v_rec.id and reservation_id = v_res.id and type = 'RESERVATION_CANCELLED'
          and title = 'Reservation released';
        select count(*) into m from public.notifications
        where user_id = v_donor.id and reservation_id = v_res.id and type = 'RESERVATION_CANCELLED';
        raise exception 'UNDO_RELEASE';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_RELEASE' then raise; end if;
      end;
      v_ok := v_status = 'CANCELLED' and v_reason = 'DONOR_RELEASED' and v_qty = 1 and v_note = 'AVAILABLE'
        and n = 1 and m = 0;
      v_note := format('%s (%s), listing %s with %s left, recipient alerts: %s, donor alerts: %s',
        v_status, v_reason, v_note, v_qty, n, m);
    exception when others then
      v_note := sqlerrm;
    end;
  elsif v_out.id is null then
    v_note := 'no third account';
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A released reservation cannot be released again, and the donor''s list shows why it was cancelled (undone at once)';
  v_ok := null; v_note := 'the sanity check above did not pass';
  if v_res.id is not null then
    v_ok := false;
    begin
      begin
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        perform public.release_reservation(v_res.id);
        select d.cancel_reason into v_reason from public.get_donor_reservations() d where d.id = v_res.id;
        begin
          perform public.release_reservation(v_res.id);
          v_note := 'released twice';
        exception when others then
          v_note := sqlerrm;
        end;
        perform set_config('role', 'postgres', true);
        raise exception 'UNDO_TWICE';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_TWICE' then raise; end if;
      end;
      v_ok := v_note = 'This reservation was already cancelled' and v_reason = 'DONOR_RELEASED';
      v_note := format('%s; listed reason: %s', v_note, coalesce(v_reason, 'none'));
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A picked-up reservation cannot be released (undone at once)';
  v_ok := null; v_note := 'the sanity check above did not pass';
  if v_res.id is not null then
    v_ok := false;
    begin
      begin
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        perform public.claim_reservation(v_res.id);
        begin
          perform public.release_reservation(v_res.id);
          v_note := 'it was released';
        exception when others then
          v_note := sqlerrm;
        end;
        perform set_config('role', 'postgres', true);
        raise exception 'UNDO_CLAIM';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_CLAIM' then raise; end if;
      end;
      v_ok := v_note = 'A picked-up reservation cannot be released';
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The recipient can still cancel, and that alerts the donor as before (undone at once)';
  v_ok := null; v_note := 'the sanity check above did not pass';
  if v_res.id is not null then
    v_ok := false;
    begin
      begin
        perform set_config('request.jwt.claims', c_rec, true);
        perform set_config('role', 'authenticated', true);
        perform public.cancel_reservation(v_res.id);
        perform set_config('role', 'postgres', true);
        select cancel_reason into v_reason from public.reservations where id = v_res.id;
        select count(*) into n from public.notifications
        where user_id = v_donor.id and reservation_id = v_res.id and type = 'RESERVATION_CANCELLED'
          and title = 'Reservation cancelled';
        raise exception 'UNDO_CANCEL';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_CANCEL' then raise; end if;
      end;
      v_ok := v_reason is null and n = 1;
      v_note := format('reason %s, donor alerts: %s', coalesce(v_reason, 'none'), n);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Report. Raising an error here undoes everything above.
  -- ---------------------------------------------------------------------------

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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s. Outsider: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'),
    coalesce(v_out.name, 'none'), v_report);
end $$;
