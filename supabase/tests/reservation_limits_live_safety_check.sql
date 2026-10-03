-- Salu-Salo reservation limits and pickup deadlines: safety check for the live
-- database (PROJECT_STATUS.md §24).
--
-- Run it in the Supabase dashboard after migrations/20261011_reservation_limits_and_expiry.sql
-- (or pasted right below that file for the trial run): SQL Editor -> New
-- query -> paste -> Run. Leave the role on "postgres".
--
-- It takes the donor of the newest listing (or the newest account) and the
-- newest other account (the "recipient"). As the donor it posts three test
-- listings; as the recipient it reserves; as postgres it moves deadlines into
-- the past and runs the expiry job. The "Sanity" checks must succeed: they
-- prove that the pretend sign-in works, so the refusals are real. A check that
-- cannot run reports SKIP. The recipient makes 4 reservations, so it fails if
-- that account already made 7 or more in the last 10 minutes (rate limit).
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did (and, in the trial run, the migration pasted above it), so nothing
-- is saved, no alert is kept and no phone gets a live update. The error text is
-- the report; it starts with "SAFETY CHECK FINISHED". Any other error means the
-- check itself did not run.

do $$
declare
  v_donor      public.users;
  v_rec        public.users;
  v_r1         public.reservations;   -- 2 on listing 1, 30-minute hold
  v_r2         public.reservations;   -- 1 on listing 1, old function
  v_r4         public.reservations;   -- listing 2, pickup starts later
  v_r5         public.reservations;   -- listing 3, pickup ends soon
  v_tmp        public.reservations;
  c_donor      text;
  c_rec        text;
  c_iss        text := 'https://securetoken.google.com/salusalo';
  v_l1         uuid := gen_random_uuid();
  v_l2         uuid := gen_random_uuid();
  v_l3         uuid := gen_random_uuid();
  v_qty        integer;
  v_qty2       integer;
  v_status     text;
  v_reason     text;
  n            bigint;
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

  -- 1
  v_label := 'Sanity: the donor posts 10 packs with a limit of 3 per person';
  v_ok := null; v_note := 'fewer than two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end, max_per_recipient)
      values (v_l1, v_donor.id, 'Safety check: limits 1', 'Food', 10, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours', 3);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l2, v_donor.id, 'Safety check: limits 2', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() + interval '1 hour', now() + interval '3 hours');
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l3, v_donor.id, 'Safety check: limits 3', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '10 minutes');
      perform set_config('role', 'postgres', true);
      select max_per_recipient into v_qty from public.food_posts where id = v_l1;
      v_ok := v_qty = 3;
      v_note := format('stored limit %s', v_qty);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2, 3
  for i in 1 .. 2 loop
    v_label := case i when 1 then 'A limit of 0 is refused' else 'A limit above the quantity (11 of 10) is refused' end;
    v_ok := null; v_note := 'fewer than two accounts';
    if v_donor.id is not null then
      v_ok := false;
      begin
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
          expiration_date, status, price, max_per_recipient)
        values (gen_random_uuid(), v_donor.id, 'Safety check: bad limit', 'Food', 10, 'packs', 'Safety check plaza',
          now() + interval '1 day', 'AVAILABLE', 0, case i when 1 then 0 else 11 end);
        perform set_config('role', 'postgres', true);
        v_note := 'it was saved';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := case i when 1 then sqlerrm like '%food_posts_max_per_recipient_check%'
                       else sqlerrm = 'The maximum per person must be from 1 to the quantity.' end;
      end;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- 4
  v_label := 'Sanity: the recipient reserves 2 with a 30-minute hold; the deadline is now + 30 minutes';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      select * into v_r1 from public.create_reservation_with_hold(v_l1, 2, 0, 30);
      perform set_config('role', 'postgres', true);
      v_ok := v_r1.status = 'RESERVED' and v_r1.pickup_deadline = now() + interval '30 minutes';
      v_note := format('%s, deadline %s', v_r1.status, v_r1.pickup_deadline);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5, 6
  for i in 1 .. 2 loop
    v_label := case i when 1 then 'A 20-minute hold is refused'
                      else 'Reserving 2 more (4 of a limit of 3) is refused with "You can reserve 1 more."' end;
    v_ok := null; v_note := 'check 4 did not pass';
    if v_r1.id is not null then
      v_ok := false;
      begin
        perform set_config('request.jwt.claims', c_rec, true);
        perform set_config('role', 'authenticated', true);
        perform public.create_reservation_with_hold(v_l1, case i when 1 then 1 else 2 end, 0, case i when 1 then 20 else 30 end);
        perform set_config('role', 'postgres', true);
        v_note := 'it was reserved';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := sqlerrm = case i when 1 then 'Choose how soon you''ll pick up: 15, 30, 60 or 120 minutes.'
                                 else 'This listing allows 3 per person. You can reserve 1 more.' end;
      end;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- 7
  v_label := 'Sanity: an old-style create_reservation of 1 still works, sets no deadline, and the donor''s list has pickup_deadline';
  v_ok := null; v_note := 'check 4 did not pass';
  if v_r1.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      select * into v_r2 from public.create_reservation(v_l1, 1);
      perform set_config('request.jwt.claims', c_donor, true);
      select count(*) into n from public.get_donor_reservations() d
      where d.id = v_r1.id and d.pickup_deadline = v_r1.pickup_deadline;
      perform set_config('role', 'postgres', true);
      v_ok := v_r2.status = 'RESERVED' and v_r2.pickup_deadline is null and n = 1;
      v_note := format('%s, deadline %s, donor rows %s', v_r2.status, coalesce(v_r2.pickup_deadline::text, 'none'), n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'At the limit (3 of 3), one more is refused with "the most this listing allows"';
  v_ok := null; v_note := 'check 7 did not pass';
  if v_r2.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      perform public.create_reservation_with_hold(v_l1, 1, 0, 15);
      perform set_config('role', 'postgres', true);
      v_note := 'it was reserved';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm = 'You''ve reserved the most this listing allows per person (3).';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9, 10
  for i in 1 .. 2 loop
    v_label := case i when 1 then 'Sanity: pickup starts in 1 hour, 60-minute hold: the deadline is now + 2 hours'
                      else 'Sanity: pickup ends in 10 minutes, 120-minute hold: the deadline is the end of pickup' end;
    v_ok := null; v_note := 'check 1 did not pass';
    if a_ok[1] then
      v_ok := false;
      begin
        perform set_config('request.jwt.claims', c_rec, true);
        perform set_config('role', 'authenticated', true);
        select * into v_tmp from public.create_reservation_with_hold(case i when 1 then v_l2 else v_l3 end, 1, 0,
          case i when 1 then 60 else 120 end);
        perform set_config('role', 'postgres', true);
        v_ok := v_tmp.pickup_deadline = now() + case i when 1 then interval '2 hours' else interval '10 minutes' end;
        v_note := format('deadline %s', v_tmp.pickup_deadline);
        if i = 1 then v_r4 := v_tmp; else v_r5 := v_tmp; end if;
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
      end;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- 11
  v_label := 'A signed-in app can''t change pickup_deadline directly';
  v_ok := null; v_note := 'check 4 did not pass';
  if v_r1.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      update public.reservations set pickup_deadline = now() + interval '1 day' where id = v_r1.id;
      perform set_config('role', 'postgres', true);
      v_note := 'the update was allowed';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm like 'permission denied%';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 12
  v_label := 'No app (anon or authenticated) may run expire_overdue_reservations(); authenticated may run create_reservation_with_hold, anon may not';
  v_ok := not has_function_privilege('anon', 'public.expire_overdue_reservations()', 'execute')
      and not has_function_privilege('authenticated', 'public.expire_overdue_reservations()', 'execute')
      and has_function_privilege('authenticated', 'public.create_reservation_with_hold(uuid,integer,numeric,integer)', 'execute')
      and not has_function_privilege('anon', 'public.create_reservation_with_hold(uuid,integer,numeric,integer)', 'execute');
  v_note := case when v_ok then 'as expected' else 'a grant is wrong' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 13
  v_label := 'After the deadline, the donor can''t mark it picked up';
  v_ok := null; v_note := 'check 4 did not pass';
  if v_r1.id is not null then
    v_ok := false;
    update public.reservations set pickup_deadline = now() - interval '1 minute' where id = v_r1.id;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.claim_reservation(v_r1.id);
      perform set_config('role', 'postgres', true);
      v_note := 'it was claimed';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm = 'The pickup time for this reservation has passed.';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 14, 15
  v_label := 'The job expires the overdue reservation (PICKUP_EXPIRED) and gives its 2 packs back';
  v_ok := null; v_note := 'check 13 did not run';
  if v_r1.id is not null then
    v_ok := false;
    select quantity into v_qty from public.food_posts where id = v_l1;
    begin
      perform public.expire_overdue_reservations();
      select status, cancel_reason into v_status, v_reason from public.reservations where id = v_r1.id;
      select quantity into v_qty2 from public.food_posts where id = v_l1;
      v_ok := v_status = 'CANCELLED' and v_reason = 'PICKUP_EXPIRED' and v_qty2 = v_qty + 2;
      v_note := format('%s / %s, quantity %s -> %s', v_status, coalesce(v_reason, 'none'), v_qty, v_qty2);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Running the job again gives nothing back twice';
  v_ok := null; v_note := 'check 14 did not pass';
  if a_ok[14] then
    v_ok := false;
    perform public.expire_overdue_reservations();
    select quantity into v_qty from public.food_posts where id = v_l1;
    v_ok := v_qty = v_qty2;
    v_note := format('quantity %s', v_qty);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 16
  v_label := 'Expiry alerts: one "Reservation expired" each for the recipient and the donor (if their reservation alerts are on)';
  v_ok := null; v_note := 'check 14 did not pass';
  if a_ok[14] then
    select count(*) into n from public.notifications
    where reservation_id = v_r1.id and title = 'Reservation expired'
      and user_id in (v_rec.id, v_donor.id);
    select count(*) into v_qty from public.notification_preferences
    where user_id in (v_rec.id, v_donor.id) and not reservation_alerts;
    v_ok := n = 2 - v_qty;
    v_note := format('%s alerts, %s of the two have reservation alerts off', n, v_qty);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 17
  v_label := 'A REMOVED listing stays REMOVED when its reservation expires';
  v_ok := null; v_note := 'check 10 did not pass';
  if v_r5.id is not null then
    v_ok := false;
    update public.food_posts set status = 'REMOVED' where id = v_l3;
    update public.reservations set pickup_deadline = now() - interval '1 minute' where id = v_r5.id;
    perform public.expire_overdue_reservations();
    select status into v_status from public.food_posts where id = v_l3;
    select status into v_reason from public.reservations where id = v_r5.id;
    v_ok := v_status = 'REMOVED' and v_reason = 'CANCELLED';
    v_note := format('listing %s, reservation %s', v_status, v_reason);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 18
  v_label := 'A cancel after the deadline but before the job gives the quantity back once';
  v_ok := null; v_note := 'check 9 did not pass';
  if v_r4.id is not null then
    v_ok := false;
    update public.reservations set pickup_deadline = now() - interval '1 minute' where id = v_r4.id;
    select quantity into v_qty from public.food_posts where id = v_l2;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      perform public.cancel_reservation(v_r4.id);
      perform set_config('role', 'postgres', true);
      perform public.expire_overdue_reservations();
      select quantity into v_qty2 from public.food_posts where id = v_l2;
      select cancel_reason into v_reason from public.reservations where id = v_r4.id;
      v_ok := v_qty2 = v_qty + 1 and v_reason is null;
      v_note := format('quantity %s -> %s, reason %s', v_qty, v_qty2, coalesce(v_reason, 'none'));
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 19
  v_label := 'create_reservation still has the rate limit, the pickup-end check and the limit';
  select prosrc like '%salusalo-reserve:%' and prosrc like '%pickup_end%' and prosrc like '%max_per_recipient%'
  into v_ok from pg_proc where oid = 'public.create_reservation(uuid,integer)'::regprocedure;
  v_note := case when v_ok then 'all three present' else 'one is missing' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 20
  v_label := 'The Cron job salusalo-expire-reservations exists and is active';
  v_ok := false;
  begin
    execute 'select count(*) from cron.job where jobname = $1 and active and schedule = $2'
      into n using 'salusalo-expire-reservations', '* * * * *';
    v_ok := n = 1;
    v_note := format('%s job(s)', n);
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- Report. Raising an error here undoes everything above.
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
