-- Salu-Salo admin dashboard: safety check for the live database (PROJECT_STATUS.md §25).
--
-- Run it in the Supabase dashboard after migrations/20261012_admin_dashboard.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres".
--
-- It takes the donor of the newest listing (or the newest account), the newest
-- other account (the "recipient") and, if there is one, a third account (the
-- "admin"; otherwise the recipient is also the admin). Inside the check it sets
-- those accounts' roles itself (the donor never admin). As the donor it posts two
-- test listings (and, as postgres, one hidden REMOVED listing), as the recipient it reserves and reports them, and as the admin
-- it uses the admin functions. The "Sanity" checks must succeed: they prove the
-- pretend sign-in works, so the refusals are real. A check that cannot run
-- reports SKIP. The recipient makes 1 reservation and 2 reports.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the check
-- did (roles, listings, reservation, reports, notes, alerts, audit rows), so
-- nothing is saved and no phone gets an alert. The error text is the report; it
-- starts with "SAFETY CHECK FINISHED". Any other error means the check itself did
-- not run.

do $$
declare
  v_donor   public.users;
  v_rec     public.users;
  v_admin   public.users;
  c_donor   text;
  c_rec     text;
  c_admin   text;
  c_ghost   text;
  c_iss     text := 'https://securetoken.google.com/salusalo';
  v_l1      uuid := gen_random_uuid();
  v_l2      uuid := gen_random_uuid();
  v_l3      uuid := gen_random_uuid();
  v_path3   text;
  v_bool3   boolean;
  v_path    text;
  v_res     public.reservations;
  v_rep1    uuid;
  v_rep2    uuid;
  v_j       jsonb;
  v_qty     integer;
  v_status  text;
  v_reason  text;
  v_bool    boolean;
  v_bool2   boolean;
  n         bigint;
  n2        bigint;
  v_label   text;
  v_ok      boolean;
  v_note    text;
  a_label   text[] := '{}';
  a_ok      boolean[] := '{}';
  a_note    text[] := '{}';
  v_report  text := '';
  v_pass    integer := 0;
  v_fail    integer := 0;
  v_skip    integer := 0;
begin
  select u.* into v_donor from public.users u
  join public.food_posts p on p.donor_id = u.id
  order by p.created_at desc limit 1;
  if v_donor.id is null then
    select * into v_donor from public.users order by created_at desc limit 1;
  end if;
  select * into v_rec from public.users
  where id is distinct from v_donor.id order by created_at desc limit 1;
  select * into v_admin from public.users
  where id is distinct from v_donor.id and id is distinct from v_rec.id order by created_at desc limit 1;
  if v_admin.id is null then
    v_admin := v_rec;
  end if;

  -- Known roles for the check (undone by the final error).
  update public.users set role = 'RECIPIENT' where id in (v_donor.id, v_rec.id, v_admin.id);

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec   := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_admin := json_build_object('sub', v_admin.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_ghost := json_build_object('sub', 'safety-check-no-profile', 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  v_path  := v_donor.firebase_uid || '/' || v_l1 || '/image.jpg';

  -- 1
  v_label := 'Sanity: the donor posts two listings; the recipient reserves 2 of the first and reports both';
  v_ok := null; v_note := 'fewer than two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end, image_url)
      values (v_l1, v_donor.id, 'Safety check: admin 1', 'Food', 10, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours', v_path);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l2, v_donor.id, 'Safety check: admin 2', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours');
      perform set_config('request.jwt.claims', c_rec, true);
      select * into v_res from public.create_reservation_with_hold(v_l1, 2, 0, 30);
      perform public.submit_report('LISTING', v_l1, 'UNSAFE_FOOD', null);
      perform public.submit_report('LISTING', v_l2, 'MISLEADING', null);
      perform set_config('role', 'postgres', true);
      select id into v_rep1 from public.reports where food_post_id = v_l1 and status = 'OPEN';
      select id into v_rep2 from public.reports where food_post_id = v_l2 and status = 'OPEN';
      v_ok := v_res.id is not null and v_rep1 is not null and v_rep2 is not null;
      v_note := format('reservation %s, reports %s and %s', v_res.id is not null, v_rep1 is not null, v_rep2 is not null);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'A signed-in account that is not an admin is refused (admin_overview)';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    perform public.admin_overview();
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Admin access required';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'A token without a profile is refused (admin_whoami)';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_ghost, true);
    perform set_config('role', 'authenticated', true);
    perform public.admin_whoami();
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Admin access required';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'No one signed out (anon) can run an admin function';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', '{}', true);
    perform set_config('role', 'anon', true);
    perform public.admin_overview();
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm like 'permission denied for function admin_overview%';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'Clients cannot read the audit log or report notes directly';
  v_ok := true; v_note := '';
  for i in 1 .. 2 loop
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      if i = 1 then perform count(*) from public.admin_audit_log; else perform count(*) from public.report_notes; end if;
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || case i when 1 then 'audit log readable; ' else 'notes readable; ' end;
    exception when others then
      perform set_config('role', 'postgres', true);
      if sqlerrm not like 'permission denied%' then v_ok := false; end if;
      v_note := v_note || sqlerrm || '; ';
    end;
  end loop;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'A non-admin cannot remove a listing, and the listing stays';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_remove_listing(v_l2, 'Safety check');
      perform set_config('role', 'postgres', true);
      v_note := 'it was removed';
    exception when others then
      perform set_config('role', 'postgres', true);
      select status into v_status from public.food_posts where id = v_l2;
      v_note := sqlerrm || ', status ' || coalesce(v_status, 'missing');
      v_ok := sqlerrm = 'Admin access required' and v_status = 'AVAILABLE';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'Sanity: after role = ADMIN, admin_whoami names the admin';
  v_ok := false;
  begin
    update public.users set role = 'ADMIN' where id = v_admin.id;
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_whoami();
    perform set_config('role', 'postgres', true);
    v_ok := (v_j -> 'me' ->> 'id')::uuid = v_admin.id and v_j -> 'me' ? 'email' and not (v_j -> 'me' ? 'firebase_uid');
    v_note := v_j -> 'me' ->> 'name';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'Overview counts match the tables';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_overview();
    perform set_config('role', 'postgres', true);
    select count(*) into n from public.users;
    select count(*) into n2 from public.reports where status = 'OPEN';
    v_ok := (v_j -> 'counts' ->> 'users')::bigint = n and (v_j -> 'counts' ->> 'pendingReports')::bigint = n2
        and jsonb_typeof(v_j -> 'activity') = 'array' and jsonb_array_length(v_j -> 'rescue' -> 'weeklyItems') = 12;
    v_note := format('users %s/%s, open reports %s/%s', v_j -> 'counts' ->> 'users', n, v_j -> 'counts' ->> 'pendingReports', n2);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'admin_listings finds listing 1 with its quantities, reservation and report';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_listings('Safety check: admin 1', 'ALL', 'ANY', null, 1, 10);
      perform set_config('role', 'postgres', true);
      v_ok := (v_j ->> 'total')::int = 1
          and v_j -> 'rows' -> 0 ->> 'status' = 'AVAILABLE'
          and (v_j -> 'rows' -> 0 ->> 'quantity')::int = 8
          and (v_j -> 'rows' -> 0 ->> 'initialQuantity')::int = 10
          and (v_j -> 'rows' -> 0 ->> 'openReservations')::int = 1
          and (v_j -> 'rows' -> 0 ->> 'openReports')::int = 1
          and v_j -> 'rows' -> 0 ->> 'coverPath' = v_path;
      v_note := left(v_j::text, 300);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'Photo rule: the admin may read a hidden (REMOVED) listing''s photo; another account and a token without a profile may not';
  v_ok := false;
  begin
    v_path3 := v_donor.firebase_uid || '/' || v_l3 || '/image.jpg';
    insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
      expiration_date, status, price, image_url)
    values (v_l3, v_donor.id, 'Safety check: admin 3', 'Food', 3, 'packs', 'Safety check plaza',
      now() + interval '1 day', 'REMOVED', 0, v_path3);
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_bool := public.can_read_food_image(v_path3);
    perform set_config('request.jwt.claims', c_ghost, true);
    v_bool2 := public.can_read_food_image(v_path3);
    v_bool3 := null;
    if v_rec.id is distinct from v_admin.id then
      perform set_config('request.jwt.claims', c_rec, true);
      v_bool3 := public.can_read_food_image(v_path3);
    end if;
    perform set_config('role', 'postgres', true);
    v_ok := v_bool and not v_bool2 and not coalesce(v_bool3, false);
    v_note := format('admin %s, no profile %s, other account %s', v_bool, v_bool2, coalesce(v_bool3::text, 'not checked (recipient is the admin)'));
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 11
  v_label := 'The admin adds a note to report 1; the report shows it with the admin''s name';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_add_report_note(v_rep1, 'Safety check note');
      v_j := public.admin_report(v_rep1);
      perform set_config('role', 'postgres', true);
      v_ok := jsonb_array_length(v_j -> 'notes') = 1 and v_j -> 'notes' -> 0 ->> 'by' = v_admin.name
          and v_j -> 'notes' -> 0 ->> 'text' = 'Safety check note' and v_j -> 'reporterDetail' ->> 'id' = v_rec.id::text;
      v_note := left((v_j -> 'notes')::text, 200);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 12
  v_label := 'Removing without a reason is refused';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_remove_listing(v_l1, '   ');
      perform set_config('role', 'postgres', true);
      v_note := 'it was removed';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm = 'Give a reason (up to 500 characters).';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 13
  v_label := 'The admin removes listing 1: REMOVED, quantity kept, reservation cancelled, report resolved';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_remove_listing(v_l1, 'Safety check removal');
      perform set_config('role', 'postgres', true);
      select status, quantity into v_status, v_qty from public.food_posts where id = v_l1;
      select r.status || '/' || coalesce(r.cancel_reason, '-') into v_reason from public.reservations r where r.id = v_res.id;
      select count(*) into n from public.reports
      where id = v_rep1 and status = 'RESOLVED' and resolution_action = 'LISTING_REMOVED'
        and resolved_by = v_admin.id and resolution_note = 'Safety check removal';
      v_ok := (v_j ->> 'cancelled')::int = 1 and (v_j ->> 'closedReports')::int = 1
          and v_status = 'REMOVED' and v_qty = 8 and v_reason = 'CANCELLED/LISTING_REMOVED' and n = 1;
      v_note := format('%s; listing %s qty %s; reservation %s; report resolved %s', v_j, v_status, v_qty, v_reason, n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 14
  v_label := 'One "Listing removed" alert for the recipient and one "Your listing was removed" for the donor';
  v_ok := null; v_note := 'check 13 did not pass';
  if a_ok[13] then
    select coalesce(bool_and(reservation_alerts), true) into v_bool from public.notification_preferences
    where user_id in (v_donor.id, v_rec.id);
    if not v_bool then
      v_note := 'one of the two accounts has Reservation updates turned off';
    else
      select count(*) into n from public.notifications
      where user_id = v_rec.id and food_post_id = v_l1 and type = 'RESERVATION_REMOVED' and title = 'Listing removed'
        and reservation_id = v_res.id;
      select count(*) into n2 from public.notifications
      where user_id = v_donor.id and food_post_id = v_l1 and type = 'RESERVATION_REMOVED' and title = 'Your listing was removed'
        and body = 'Salu-Salo removed Safety check: admin 1: Safety check removal';
      v_ok := n = 1 and n2 = 1;
      v_note := format('recipient %s, donor %s', n, n2);
    end if;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 15
  v_label := 'Removing it again is refused and sends no second alert';
  v_ok := null; v_note := 'check 13 did not pass';
  if a_ok[13] then
    v_ok := false;
    select count(*) into n from public.notifications where food_post_id = v_l1;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_remove_listing(v_l1, 'Again');
      perform set_config('role', 'postgres', true);
      v_note := 'it was removed again';
    exception when others then
      perform set_config('role', 'postgres', true);
      select count(*) into n2 from public.notifications where food_post_id = v_l1;
      v_note := sqlerrm || format(', alerts %s -> %s', n, n2);
      v_ok := sqlerrm = 'This listing was already removed.' and n = n2;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 16
  v_label := 'Dismissing listing 2''s reports closes report 2; dismissing it again is refused';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_dismiss_listing_reports(v_l2, null);
      perform set_config('role', 'postgres', true);
      select status || '/' || resolution_note into v_reason from public.reports where id = v_rep2;
      v_note := format('%s, %s', v_j, v_reason);
      begin
        perform set_config('request.jwt.claims', c_admin, true);
        perform set_config('role', 'authenticated', true);
        perform public.admin_dismiss_report(v_rep2, null);
        perform set_config('role', 'postgres', true);
        v_note := v_note || ', dismissed twice';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_ok := (v_j ->> 'dismissed')::int = 1 and v_reason = 'DISMISSED/No problem found.'
            and sqlerrm = 'This report is already closed.';
        v_note := v_note || ', ' || sqlerrm;
      end;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 17
  v_label := 'Listing details: reviewed, removed by the admin with the reason, photo path listed';
  v_ok := null; v_note := 'check 13 did not pass';
  if a_ok[13] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_mark_listing_reviewed(v_l2, null);
      v_bool := (public.admin_listing(v_l2) ->> 'reviewedAt') is not null;
      v_j := public.admin_listing(v_l1);
      perform set_config('role', 'postgres', true);
      v_ok := v_bool and v_j ->> 'removedByName' = v_admin.name and v_j ->> 'removedReason' = 'Safety check removal'
          and v_j -> 'photoPaths' = jsonb_build_array(v_path) and v_j ->> 'status' = 'REMOVED'
          and not (v_j -> 'donor' ? 'firebase_uid');
      v_note := format('reviewed %s, removed by %s, reason %s', v_bool, v_j ->> 'removedByName', v_j ->> 'removedReason');
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 18
  v_label := 'The audit log has the four admin actions, newest first';
  v_ok := null; v_note := 'check 17 did not pass';
  if a_ok[17] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_audit(null, v_admin.id, 'ALL', 'TODAY', 1, 50);
      perform set_config('role', 'postgres', true);
      select count(distinct e ->> 'action') into n
      from jsonb_array_elements(v_j -> 'rows') e
      where (e ->> 'targetId')::uuid in (v_l1, v_l2, v_rep1);
      v_ok := n = 4 and (v_j -> 'rows' -> 0 ->> 'at')::bigint >= (v_j -> 'rows' -> 1 ->> 'at')::bigint;
      v_note := format('%s distinct actions', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 19
  v_label := 'Reservation, user and resolved-report pages read the new state';
  v_ok := null; v_note := 'check 13 did not pass';
  if a_ok[13] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_reservation(v_res.id);
      v_bool := v_j ->> 'cancelReason' = 'LISTING_REMOVED' and v_j -> 'recipient' ->> 'id' = v_rec.id::text;
      v_j := public.admin_user(v_donor.id);
      v_bool := v_bool and (v_j -> 'stats' ->> 'listings')::int >= 2 and v_j ->> 'role' = 'DONOR';
      v_j := public.admin_reports('Safety check: admin 1', 'RESOLVED', 'ALL', 'ALL', 1, 10);
      perform set_config('role', 'postgres', true);
      v_ok := v_bool and (v_j ->> 'total')::int = 1 and v_j -> 'rows' -> 0 -> 'resolution' ->> 'action' = 'LISTING_REMOVED';
      v_note := left(v_j::text, 200);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 20
  v_label := 'Paging: one user per page; a page past the end gives the last page';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_users(null, 'ALL', 9999, 1);
    perform set_config('role', 'postgres', true);
    select count(*) into n from public.users;
    v_ok := (v_j ->> 'pageSize')::int = 1 and jsonb_array_length(v_j -> 'rows') = 1
        and (v_j ->> 'pages')::bigint = n and (v_j ->> 'page')::bigint = n;
    v_note := format('page %s of %s, %s users', v_j ->> 'page', v_j ->> 'pages', n);
  exception when others then
    perform set_config('role', 'postgres', true);
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s. Admin: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'), coalesce(v_admin.name, 'none'), v_report);
end $$;
