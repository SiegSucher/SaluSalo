-- Salu-Salo suspend accounts: safety check for the live database (PROJECT_STATUS.md §36).
--
-- Run it in the Supabase dashboard after migrations/20261017_admin_suspend_accounts.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres".
--
-- It takes the three newest accounts: A (made an admin inside the check), B (the
-- account it suspends) and C (another account). Inside the check, B posts two
-- listings and C reserves 1 of each (the second listing is then made to look
-- expired); C posts a listing and B reserves twice (C marks the first picked up);
-- B opens a chat about C's listing, C reports B, and B gets a made-up push token.
-- Then A suspends B, checks what that did and what B can still do, and restores B.
-- The "Sanity" checks must succeed: they prove the pretend sign-in works, so the
-- refusals are real. A check that cannot run reports SKIP.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the check
-- did (roles, listings, reservations, the chat, the report, alerts, tokens, the
-- suspension and the audit rows), so nothing is saved and no phone gets a push.
-- The error text is the report; it starts with "SAFETY CHECK FINISHED". Any other
-- error means the check itself did not run.

do $$
declare
  v_a        public.users;
  v_b        public.users;
  v_c        public.users;
  c_iss      text := 'https://securetoken.google.com/salusalo';
  c_a        text;
  c_b        text;
  c_c        text;
  v_l1       uuid := gen_random_uuid();
  v_l2       uuid := gen_random_uuid();
  v_l3       uuid := gen_random_uuid();
  v_path     text;
  v_r        public.reservations;
  r_c1       uuid;
  r_c2       uuid;
  r_b1       uuid;
  r_b2       uuid;
  v_conv     uuid;
  v_report   uuid;
  v_tok      text := 'safety-check-suspend-' || replace(gen_random_uuid()::text, '-', '');
  v_json     jsonb;
  v_text     text;
  v_bool     boolean;
  n          bigint;
  m          bigint;
  k          bigint;
  v_rr       boolean;
  v_label    text;
  v_ok       boolean;
  v_note     text;
  a_label    text[] := '{}';
  a_ok       boolean[] := '{}';
  a_note     text[] := '{}';
  v_out      text := '';
  v_pass     integer := 0;
  v_fail     integer := 0;
  v_skip     integer := 0;
  v_ready    boolean := false;
  v_suspended boolean := false;
begin
  select * into v_a from public.users order by created_at desc limit 1;
  select * into v_b from public.users where id is distinct from v_a.id order by created_at desc limit 1;
  select * into v_c from public.users
  where id is distinct from v_a.id and id is distinct from v_b.id order by created_at desc limit 1;

  if v_c.id is not null then
    -- Known roles and alert switches for the check (undone by the final error).
    update public.users set role = 'RECIPIENT', suspended_at = null, suspension_reason = null
    where id in (v_b.id, v_c.id);
    update public.users set role = 'ADMIN', suspended_at = null, suspension_reason = null where id = v_a.id;
    insert into public.notification_preferences (user_id) values (v_c.id) on conflict (user_id) do nothing;
    update public.notification_preferences set reservation_alerts = true, message_alerts = true where user_id = v_c.id;
    c_a := json_build_object('sub', v_a.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
    c_b := json_build_object('sub', v_b.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
    c_c := json_build_object('sub', v_c.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
    v_path := v_b.firebase_uid || '/' || v_l1 || '/image.jpg';
  end if;

  -- 1
  v_label := 'Sanity: B posts two listings, C reserves both, C posts one and B reserves it twice (one picked up), a chat, a report, a token';
  v_ok := null; v_note := 'needs three accounts';
  if v_c.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end, image_url)
      values (v_l1, v_b.id, 'Safety check: suspend 1', 'Food', 3, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours', v_path);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l2, v_b.id, 'Safety check: suspend 2', 'Food', 3, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours');
      perform set_config('request.jwt.claims', c_c, true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l3, v_c.id, 'Safety check: suspend 3', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours');
      select * into v_r from public.create_reservation(v_l1, 1); r_c1 := v_r.id;
      select * into v_r from public.create_reservation(v_l2, 1); r_c2 := v_r.id;
      perform set_config('request.jwt.claims', c_b, true);
      select * into v_r from public.create_reservation(v_l3, 1); r_b1 := v_r.id;
      select * into v_r from public.create_reservation(v_l3, 1); r_b2 := v_r.id;
      select id into v_conv from public.start_listing_conversation(v_l3);
      perform set_config('request.jwt.claims', c_c, true);
      perform public.claim_reservation(r_b1);
      perform set_config('role', 'postgres', true);
      -- Listing 2 now looks expired, with C's reservation still open.
      update public.food_posts set pickup_start = now() - interval '2 hours', pickup_end = now() - interval '1 hour',
        expiration_date = now() - interval '30 minutes' where id = v_l2;
      insert into public.reports (reporter_id, reported_user_id, target_type, source, conversation_id, reason)
      values (v_c.id, v_b.id, 'USER', 'CHAT', v_conv, 'NO_SHOW') returning id into v_report;
      insert into public.push_tokens (token, user_id) values (v_tok, v_b.id);
      v_ready := r_c1 is not null and r_c2 is not null and r_b1 is not null and r_b2 is not null
                 and v_conv is not null and v_report is not null;
      v_ok := v_ready;
      v_note := case when v_ready then 'all fixtures made' else 'a fixture is missing' end;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'admin_suspend_user refuses a caller who is not an admin';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_c, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_suspend_user(v_b.id, 'NO_SHOWS', 'Safety check');
      perform set_config('role', 'postgres', true);
      v_note := 'it suspended';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm; v_ok := sqlerrm = 'Admin access required';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'An admin cannot suspend themselves';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_suspend_user(v_a.id, 'NO_SHOWS', 'Safety check');
      perform set_config('role', 'postgres', true);
      v_note := 'it suspended';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm; v_ok := sqlerrm = 'You can''t suspend your own account.';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'Another admin cannot be suspended';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := false;
    begin
      update public.users set role = 'ADMIN' where id = v_c.id;
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_suspend_user(v_c.id, 'NO_SHOWS', 'Safety check');
      perform set_config('role', 'postgres', true);
      update public.users set role = 'RECIPIENT' where id = v_c.id;
      v_note := 'it suspended';
    exception when others then
      perform set_config('role', 'postgres', true);
      update public.users set role = 'RECIPIENT' where id = v_c.id;
      v_note := sqlerrm; v_ok := sqlerrm = 'Admins can''t be suspended.';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'An unknown or missing reason is refused';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := true; v_note := '';
    foreach v_text in array array['MEAN', ''] loop
      begin
        perform set_config('request.jwt.claims', c_a, true);
        perform set_config('role', 'authenticated', true);
        perform public.admin_suspend_user(v_b.id, nullif(v_text, ''), 'Safety check');
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := v_note || format('[%s] suspended; ', v_text);
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'Choose a reason.' then v_ok := false; end if;
        v_note := v_note || format('[%s] %s; ', v_text, sqlerrm);
      end;
    end loop;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'A note of only spaces, or of 1,001 characters, is refused';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := true; v_note := '';
    foreach v_text in array array['   ', repeat('x', 1001)] loop
      begin
        perform set_config('request.jwt.claims', c_a, true);
        perform set_config('role', 'authenticated', true);
        perform public.admin_suspend_user(v_b.id, 'NO_SHOWS', v_text);
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := v_note || format('[%s chars] suspended; ', length(v_text));
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'Write a note (up to 1,000 characters).' then v_ok := false; end if;
        v_note := v_note || format('[%s chars] %s; ', length(v_text), sqlerrm);
      end;
    end loop;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'Sanity: the admin suspends B; it reports 2 listings removed and 1 reservation cancelled';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      v_json := public.admin_suspend_user(v_b.id, 'NO_SHOWS', 'Safety check');
      perform set_config('role', 'postgres', true);
      v_suspended := true;
      v_ok := (v_json ->> 'removedListings')::int = 2 and (v_json ->> 'cancelledReservations')::int = 1
              and (v_json ->> 'closedReports')::int >= 1;
      v_note := v_json::text;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'Both of B''s listings are REMOVED (also the expired one) and C''s two reservations on them are LISTING_REMOVED';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    select count(*) into n from public.food_posts where id in (v_l1, v_l2) and status = 'REMOVED';
    select count(*) into m from public.reservations
    where id in (r_c1, r_c2) and status = 'CANCELLED' and cancel_reason = 'LISTING_REMOVED';
    v_ok := n = 2 and m = 2;
    v_note := format('%s listing(s) removed, %s reservation(s) cancelled', n, m);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'B''s open reservation is ACCOUNT_SUSPENDED with its quantity back; the picked-up one stays CLAIMED';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    select quantity into n from public.food_posts where id = v_l3;
    select count(*) into m from public.reservations
    where (id = r_b2 and status = 'CANCELLED' and cancel_reason = 'ACCOUNT_SUSPENDED')
       or (id = r_b1 and status = 'CLAIMED');
    v_ok := n = 4 and m = 2;
    v_note := format('listing 3 has %s (expected 4); %s of 2 reservations as expected', n, m);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'C, the donor, gets "Reservation cancelled" with the exact text';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    select count(*) into n from public.notifications
    where user_id = v_c.id and reservation_id = r_b2 and type = 'RESERVATION_CANCELLED'
      and title = 'Reservation cancelled'
      and body = v_b.name || '''s reservation of Safety check: suspend 3 was cancelled. The quantity is available again.';
    v_ok := n = 1;
    v_note := format('%s matching alert(s)', n);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 11
  v_label := 'B has no push tokens; the report on B is RESOLVED as USER_SUSPENDED; the audit log has USER_SUSPENDED';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    select count(*) into n from public.push_tokens where user_id = v_b.id;
    select count(*) into m from public.reports
    where id = v_report and status = 'RESOLVED' and resolution_action = 'USER_SUSPENDED';
    v_bool := exists (select 1 from public.admin_audit_log
                      where action = 'USER_SUSPENDED' and target_type = 'USER' and target_id = v_b.id);
    v_ok := n = 0 and m = 1 and v_bool;
    v_note := format('%s token(s), report resolved %s, audit row %s', n, m = 1, v_bool);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 12
  v_label := 'A second suspension of B is refused, and restoring C (not suspended) is refused';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_suspend_user(v_b.id, 'SPAM', 'Again');
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'suspended twice; ';
    exception when others then
      perform set_config('role', 'postgres', true);
      if sqlerrm <> 'This account is already suspended.' then v_ok := false; end if;
      v_note := sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_restore_user(v_c.id, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'restored C';
    exception when others then
      perform set_config('role', 'postgres', true);
      if sqlerrm <> 'This account isn''t suspended.' then v_ok := false; end if;
      v_note := v_note || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 13
  v_label := 'Suspended B sees neither its own profile row nor any listing, and gets nothing from get_donor_reservations or is_reservation_recipient';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.users where id = v_b.id;
      select count(*) into m from public.food_posts;
      select count(*) into k from public.get_donor_reservations();
      v_rr := public.is_reservation_recipient(v_l3);
      perform set_config('role', 'postgres', true);
      v_ok := n = 0 and m = 0 and k = 0 and v_rr is false;
      v_note := format('%s profile row(s), %s listing(s), %s donor reservation(s), is_reservation_recipient = %s', n, m, k, v_rr);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := true; v_note := 'refused: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 14
  v_label := 'Suspended B cannot reserve or save a push token (both refused as Not authenticated)';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      perform public.create_reservation(v_l3, 1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'reserved; ';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := 'reserve: ' || sqlerrm || '; ';
      if sqlerrm <> 'Not authenticated' then v_ok := false; end if;
    end;
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      perform public.register_my_push_token(v_tok);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'token saved';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := v_note || 'token: ' || sqlerrm;
      if sqlerrm <> 'Not authenticated' then v_ok := false; end if;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 15
  v_label := 'Suspended B cannot read its own listing photo (can_read_food_image)';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      v_bool := public.can_read_food_image(v_path);
      perform set_config('role', 'postgres', true);
      v_ok := not v_bool;
      v_note := format('can_read_food_image = %s', v_bool);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := true; v_note := 'refused: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 16
  v_label := 'Suspended B can still ask get_my_account_status (suspended, NO_SHOWS) but cannot send a message (Not authenticated)';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      v_json := public.get_my_account_status();
      perform set_config('role', 'postgres', true);
      v_ok := (v_json ->> 'suspended')::boolean and v_json ->> 'reason' = 'NO_SHOWS';
      v_note := v_json::text || '; ';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := 'status: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      perform public.send_message(v_conv, 'Safety check from B');
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'B sent a message';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := v_note || 'send: ' || sqlerrm;
      if sqlerrm <> 'Not authenticated' then v_ok := false; end if;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 17
  v_label := 'C''s message to suspended B is refused with the exact text; after a restore B sees its row and is not suspended';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_c, true);
      perform set_config('role', 'authenticated', true);
      perform public.send_message(v_conv, 'Safety check from C');
      perform set_config('role', 'postgres', true);
      v_note := 'C sent it; ';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := sqlerrm = 'This person can''t receive messages right now.';
      v_note := sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_restore_user(v_b.id, 'Safety check');
      perform set_config('request.jwt.claims', c_b, true);
      select count(*) into n from public.users where id = v_b.id;
      v_json := public.get_my_account_status();
      perform set_config('role', 'postgres', true);
      v_ok := v_ok and n = 1 and not (v_json ->> 'suspended')::boolean
              and exists (select 1 from public.admin_audit_log where action = 'USER_RESTORED' and target_id = v_b.id);
      v_note := v_note || format('after restore: %s row(s), %s', n, v_json::text);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'restore: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 18
  v_label := 'anon cannot run any of the new functions';
  v_ok := false;
  begin
    v_ok := not has_function_privilege('anon', 'public.salusalo_caller_suspended()', 'execute')
        and not has_function_privilege('anon', 'public.get_my_account_status()', 'execute')
        and not has_function_privilege('anon', 'public.admin_suspend_user(uuid,text,text)', 'execute')
        and not has_function_privilege('anon', 'public.admin_restore_user(uuid,text)', 'execute')
        and not has_function_privilege('anon', 'public.admin_suspension_listings(uuid)', 'execute')
        and not has_function_privilege('authenticated', 'public.admin_suspension_listings(uuid)', 'execute');
    v_note := case when v_ok then 'none for anon; the internal helper none for clients' else 'a privilege is too wide' end;
  exception when others then
    v_note := sqlerrm;
  end;
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
    v_out := v_out || format(E'\n%s %s. %s: %s',
      case when a_ok[i] is null then 'SKIP' when a_ok[i] then 'PASS' else 'FAIL' end,
      i, a_label[i], coalesce(a_note[i], '-'));
  end loop;

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nA: %s. B: %s. C: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_a.name, 'none'), coalesce(v_b.name, 'none'), coalesce(v_c.name, 'none'), v_out);
end $$;
