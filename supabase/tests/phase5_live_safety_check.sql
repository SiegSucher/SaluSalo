-- Salu-Salo Phase 5 part 1 (reviews, reports, Email verified): safety check for
-- the live database.
--
-- Run it in the Supabase dashboard after migrations/20261002_phase5_reviews_reports.sql:
-- SQL Editor -> New query -> paste this whole file -> Run. Leave the role on
-- "postgres".
--
-- It picks the newest CLAIMED reservation, its recipient and the listing's
-- donor, and the newest other account (the "outsider"; preferably one with no
-- reservation on that listing). It pretends to be each
-- of them, the way the app's Firebase token does, and tries things the app must
-- never allow. The "Sanity" checks must succeed: they prove that the pretend
-- sign-in works, so the refusals are real. A check that cannot run reports SKIP.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did (reviews, reports, status changes, alerts, the email_verified
-- values), so nothing is saved and no phone gets a live update. The error text
-- is the report; it starts with "SAFETY CHECK FINISHED". Any other error means
-- the check itself did not run.

do $$
declare
  v_res        public.reservations;
  v_post       public.food_posts;
  v_rcp        public.users;
  v_donor      public.users;
  v_out        public.users;
  v_chat       uuid;
  c_rcp        text;
  c_donor      text;
  c_out        text;
  c_foreign    text;
  v_id         uuid;
  v_bool       boolean;
  v_first      boolean;
  v_second     boolean;
  v_rating     integer;
  v_before     public.users;
  v_after      public.users;
  n            bigint;
  m            bigint;
  v_out_linked boolean := false;
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
  -- What and who to test with.
  select * into v_res from public.reservations
  where status = 'CLAIMED'
  order by updated_at desc
  limit 1;
  if v_res.id is not null then
    select * into v_post from public.food_posts where id = v_res.food_post_id;
    select * into v_rcp from public.users where id = v_res.recipient_id;
    select * into v_donor from public.users where id = v_post.donor_id;
    -- Preferably an account with no reservation on this listing.
    select * into v_out from public.users u
    where u.id <> v_rcp.id and u.id <> v_donor.id
    order by exists (select 1 from public.reservations r
                     where r.food_post_id = v_post.id and r.recipient_id = u.id),
             u.created_at desc
    limit 1;
    -- A chat between the recipient and the donor, which the outsider is not part of.
    select id into v_chat from public.conversations
    where recipient_id = v_rcp.id and donor_id = v_donor.id
    order by created_at desc
    limit 1;
  end if;
  if v_out.id is not null then
    v_out_linked := exists (select 1 from public.reservations
                            where food_post_id = v_post.id and recipient_id = v_out.id);
  end if;

  c_rcp := json_build_object('sub', v_rcp.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo')::text;
  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo')::text;
  c_out := json_build_object('sub', v_out.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo')::text;
  -- The recipient's user ID, but in a token issued for another Firebase project.
  c_foreign := json_build_object('sub', v_rcp.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/another-project', 'aud', 'another-project')::text;

  -- Start clean for the report checks: no open reports by these accounts
  -- (removed as postgres, and put back by the closing error).
  if v_res.id is not null then
    delete from public.reports where reporter_id in (v_rcp.id, v_donor.id, coalesce(v_out.id, v_rcp.id));
  end if;

  -- ---------------------------------------------------------------------------
  -- Sanity checks.
  -- ---------------------------------------------------------------------------

  v_label := 'Sanity: the recipient''s pretend sign-in works';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      v_id := public.current_salusalo_profile_id();
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(v_id = v_rcp.id, false);
      v_note := format('signed in: %s', case when v_id is null then 'no' else 'yes' end);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the outsider''s pretend sign-in works';
  v_ok := null; v_note := 'no claimed reservation or no third account found';
  if v_out.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      v_id := public.current_salusalo_profile_id();
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(v_id = v_out.id, false);
      v_note := format('signed in: %s', case when v_id is null then 'no' else 'yes' end);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the recipient can rate the claimed reservation, and a second send changes the same review';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := false;
    begin
      delete from public.reviews where reservation_id = v_res.id;
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      select s.created into v_first from public.submit_review(v_res.id, 4, '  Safety check  ') s;
      select s.created, s.rating into v_second, v_rating from public.submit_review(v_res.id, 5, null) s;
      perform set_config('role', 'postgres', true);
      select count(*) into n from public.reviews where reservation_id = v_res.id;
      v_ok := v_first and not v_second and v_rating = 5 and n = 1;
      v_note := format('first created: %s, second created: %s, rating now: %s, rows: %s',
        v_first, v_second, v_rating, n);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the recipient can report the listing they picked up';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('LISTING', v_post.id, 'MISLEADING', 'Safety check');
      perform set_config('role', 'postgres', true);
      select count(*) into n from public.reports
      where reporter_id = v_rcp.id and reported_user_id = v_donor.id and target_type = 'LISTING';
      v_ok := n = 1;
      v_note := format('%s report(s) saved', n);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Reviews.
  -- ---------------------------------------------------------------------------

  v_label := 'The outsider cannot rate the recipient''s reservation';
  v_ok := null; v_note := 'no claimed reservation or no third account found';
  if v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_res.id, 1, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the review was accepted';
    exception when others then
      v_ok := sqlerrm like '%only your own pickups%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The donor cannot rate their own listing''s reservation';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_res.id, 5, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the review was accepted';
    exception when others then
      v_ok := sqlerrm like '%only your own pickups%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Ratings of 0 and 6 are refused';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_res.id, 0, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := '0 was accepted; ';
    exception when others then
      v_ok := v_ok and sqlerrm like '%1 to 5 stars%';
      v_note := '0: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_res.id, 6, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || '6 was accepted';
    exception when others then
      v_ok := v_ok and sqlerrm like '%1 to 5 stars%';
      v_note := v_note || '6: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A 501-character comment is refused';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_res.id, 4, repeat('x', 501));
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the comment was accepted';
    exception when others then
      v_ok := sqlerrm like '%500 characters or fewer%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A reservation that is RESERVED or CANCELLED cannot be rated';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := true; v_note := '';
    update public.reservations set status = 'RESERVED' where id = v_res.id;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_res.id, 4, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'RESERVED was accepted; ';
    exception when others then
      v_ok := v_ok and sqlerrm like '%marked as claimed%';
      v_note := 'RESERVED: ' || sqlerrm || '; ';
    end;
    update public.reservations set status = 'CANCELLED' where id = v_res.id;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_res.id, 4, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'CANCELLED was accepted';
    exception when others then
      v_ok := v_ok and sqlerrm like '%marked as claimed%';
      v_note := v_note || 'CANCELLED: ' || sqlerrm;
    end;
    update public.reservations set status = 'CLAIMED' where id = v_res.id;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login, submit_review is refused';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    begin
      perform set_config('request.jwt.claims', '', true);
      perform set_config('role', 'anon', true);
      perform public.submit_review(v_res.id, 5, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the review was accepted';
    exception when others then
      v_ok := sqlstate = '42501';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A token from another Firebase project cannot rate';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    begin
      perform set_config('request.jwt.claims', c_foreign, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_res.id, 5, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the review was accepted';
    exception when others then
      v_ok := sqlerrm like '%Not authenticated%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A signed-in user cannot read or write reviews or reports directly';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.reviews;
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := format('reviews readable (%s rows); ', n);
    exception when others then
      v_ok := v_ok and sqlstate = '42501';
      v_note := 'read reviews: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      insert into public.reviews (reservation_id, reviewer_id, reviewee_id, rating)
      values (v_res.id, v_rcp.id, v_donor.id, 5);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'review inserted; ';
    exception when others then
      v_ok := v_ok and sqlstate = '42501';
      v_note := v_note || 'insert review: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.reports;
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || format('reports readable (%s rows)', n);
    exception when others then
      v_ok := v_ok and sqlstate = '42501';
      v_note := v_note || 'read reports: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login, reports cannot be read';
  begin
    perform set_config('request.jwt.claims', '', true);
    perform set_config('role', 'anon', true);
    select count(*) into n from public.reports;
    perform set_config('role', 'postgres', true);
    v_ok := false; v_note := format('%s row(s) returned', n);
  exception when others then
    v_ok := sqlstate = '42501';
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Reports.
  -- ---------------------------------------------------------------------------

  v_label := 'A second open report of the same listing is refused';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('LISTING', v_post.id, 'SPAM_FAKE', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the report was accepted';
    exception when others then
      v_ok := sqlerrm like '%already reported this%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The donor cannot report their own listing or themself';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('LISTING', v_post.id, 'MISLEADING', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'own listing accepted; ';
    exception when others then
      v_ok := v_ok and sqlerrm like '%your own listing%';
      v_note := 'LISTING: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('DONOR', v_post.id, 'SPAM', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'themself accepted';
    exception when others then
      v_ok := v_ok and sqlerrm like '%report yourself%';
      v_note := v_note || 'DONOR: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot report through a chat they are not in';
  v_ok := null; v_note := 'no chat between the recipient and the donor, or no third account';
  if v_chat is not null and v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('CHAT', v_chat, 'ABUSIVE', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the report was accepted';
    exception when others then
      v_ok := sqlerrm like '%your own chats%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'An unknown reason, or a reason for the other kind of target, is refused';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    delete from public.reports where reporter_id = v_rcp.id;
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('LISTING', v_post.id, 'BOGUS', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'BOGUS accepted; ';
    exception when others then
      v_ok := v_ok and sqlerrm like '%reason from the list%';
      v_note := 'BOGUS: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('LISTING', v_post.id, 'NO_SHOW', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'NO_SHOW for a listing accepted; ';
    exception when others then
      v_ok := v_ok and sqlerrm like '%reason from the list%';
      v_note := v_note || 'NO_SHOW for a listing: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('DONOR', v_post.id, 'UNSAFE_FOOD', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'UNSAFE_FOOD for a person accepted';
    exception when others then
      v_ok := v_ok and sqlerrm like '%reason from the list%';
      v_note := v_note || 'UNSAFE_FOOD for a person: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'OTHER without details, and 1001 characters of details, are refused';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('DONOR', v_post.id, 'OTHER', '   ');
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'OTHER without details accepted; ';
    exception when others then
      v_ok := v_ok and sqlerrm like '%Describe the problem%';
      v_note := 'OTHER: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('DONOR', v_post.id, 'SPAM', repeat('x', 1001));
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || '1001 characters accepted';
    exception when others then
      v_ok := v_ok and sqlerrm like '%1000 characters or fewer%';
      v_note := v_note || '1001: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'After 10 reports in 24 hours, the next one is refused';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    delete from public.reports where reporter_id = v_rcp.id;
    -- Ten earlier reports by the recipient (as postgres), none of them about this listing's donor.
    insert into public.reports (reporter_id, reported_user_id, target_type, source, reason, created_at)
    select v_rcp.id, v_donor.id, 'LISTING', 'LISTING', 'MISLEADING', now() - interval '1 hour'
    from generate_series(1, 10);
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('DONOR', v_post.id, 'SPAM', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the 11th report was accepted';
    exception when others then
      v_ok := sqlerrm like '%up to 10 reports a day%';
      v_note := sqlerrm;
    end;
    delete from public.reports where reporter_id = v_rcp.id;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- A listing the outsider cannot see (marked REMOVED as postgres).
  -- ---------------------------------------------------------------------------

  if v_res.id is not null then
    update public.food_posts set status = 'REMOVED' where id = v_post.id;
  end if;

  v_label := 'After removal: the outsider gets no donor details or reviews, and cannot report the listing or its donor';
  v_ok := null; v_note := 'no claimed reservation or no third account found';
  if v_out_linked then
    v_note := 'the outsider has a reservation on this listing';
  elsif v_out.id is not null then
    v_ok := true;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.get_listing_donor_profiles(array[v_post.id]);
      select count(*) into m from public.get_listing_donor_reviews(v_post.id, 50);
      perform set_config('role', 'postgres', true);
      v_ok := n = 0 and m = 0;
      v_note := format('donor profiles: %s, reviews: %s; ', n, m);
    exception when others then
      v_ok := false; v_note := sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('LISTING', v_post.id, 'MISLEADING', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'listing report accepted; ';
    exception when others then
      v_ok := v_ok and sqlerrm like '%no longer available%';
      v_note := v_note || 'LISTING: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.submit_report('DONOR', v_post.id, 'SPAM', null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'donor report accepted';
    exception when others then
      v_ok := v_ok and sqlerrm like '%no longer available%';
      v_note := v_note || 'DONOR: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'After removal: the recipient still gets the donor''s details and reviews (they reserved it)';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.get_listing_donor_profiles(array[v_post.id]);
      select count(*) into m from public.get_listing_donor_reviews(v_post.id, 50);
      perform set_config('role', 'postgres', true);
      v_ok := n = 1 and m >= 1;
      v_note := format('donor profiles: %s, reviews: %s', n, m);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Email verified.
  -- ---------------------------------------------------------------------------

  v_label := 'sync_my_email_verified sets the caller''s own value from the token and changes nothing else';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := false;
    begin
      update public.users set email_verified = false where id = v_rcp.id;
      select * into v_before from public.users where id = v_rcp.id;
      -- A verified token whose email differs only in case and spaces.
      perform set_config('request.jwt.claims', json_build_object('sub', v_rcp.firebase_uid,
        'role', 'authenticated', 'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo',
        'email', ' ' || upper(v_rcp.email) || ' ', 'email_verified', true)::text, true);
      perform set_config('role', 'authenticated', true);
      v_first := public.sync_my_email_verified();
      perform set_config('role', 'postgres', true);
      select * into v_after from public.users where id = v_rcp.id;
      v_ok := v_first and v_after.email_verified
        and (to_jsonb(v_after) - 'email_verified' - 'updated_at') = (to_jsonb(v_before) - 'email_verified' - 'updated_at');
      -- Then an unverified token.
      perform set_config('request.jwt.claims', json_build_object('sub', v_rcp.firebase_uid,
        'role', 'authenticated', 'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo',
        'email', v_rcp.email, 'email_verified', false)::text, true);
      perform set_config('role', 'authenticated', true);
      v_second := public.sync_my_email_verified();
      perform set_config('role', 'postgres', true);
      select email_verified into v_bool from public.users where id = v_rcp.id;
      v_ok := v_ok and not v_second and not v_bool;
      v_note := format('verified token: %s (stored %s), unverified token: %s (stored %s), other columns unchanged: %s',
        v_first, v_after.email_verified, v_second, v_bool,
        (to_jsonb(v_after) - 'email_verified' - 'updated_at') = (to_jsonb(v_before) - 'email_verified' - 'updated_at'));
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider''s sync changes only their own row, and a token for another email is not verified';
  v_ok := null; v_note := 'no claimed reservation or no third account found';
  if v_out.id is not null then
    v_ok := false;
    begin
      update public.users set email_verified = false where id = v_rcp.id;
      perform set_config('request.jwt.claims', json_build_object('sub', v_out.firebase_uid,
        'role', 'authenticated', 'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo',
        'email', v_rcp.email, 'email_verified', true)::text, true);
      perform set_config('role', 'authenticated', true);
      v_first := public.sync_my_email_verified();
      perform set_config('role', 'postgres', true);
      select email_verified into v_bool from public.users where id = v_rcp.id;
      v_ok := not v_first and not v_bool;
      v_note := format('outsider stored: %s, recipient still: %s', v_first, v_bool);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A signed-in user cannot UPDATE users directly';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      update public.users set email_verified = true where id = v_rcp.id;
      get diagnostics n = row_count;
      perform set_config('role', 'postgres', true);
      v_ok := n = 0;
      v_note := format('no error, %s row(s) updated', n);
    exception when others then
      v_ok := sqlstate = '42501';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A new profile inserted with email_verified = true is stored unverified';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', json_build_object('sub', 'phase5-safety-check-new-user',
      'role', 'authenticated', 'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo',
      'email', 'phase5-safety-check@example.invalid', 'email_verified', true)::text, true);
    perform set_config('role', 'authenticated', true);
    insert into public.users (firebase_uid, name, email, email_verified)
    values ('phase5-safety-check-new-user', 'Safety Check', 'phase5-safety-check@example.invalid', true);
    perform set_config('role', 'postgres', true);
    select email_verified into v_bool from public.users where firebase_uid = 'phase5-safety-check-new-user';
    v_ok := v_bool = false;
    v_note := format('stored email_verified: %s', v_bool);
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login, and with another project''s token, sync_my_email_verified is refused';
  v_ok := true; v_note := '';
  begin
    perform set_config('request.jwt.claims', '', true);
    perform set_config('role', 'anon', true);
    perform public.sync_my_email_verified();
    perform set_config('role', 'postgres', true);
    v_ok := false; v_note := 'anon accepted; ';
  exception when others then
    v_ok := v_ok and sqlstate = '42501';
    v_note := 'anon: ' || sqlerrm || '; ';
  end;
  if v_res.id is not null then
    begin
      perform set_config('request.jwt.claims', c_foreign, true);
      perform set_config('role', 'authenticated', true);
      perform public.sync_my_email_verified();
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'foreign token accepted';
    exception when others then
      v_ok := v_ok and sqlerrm like '%Not authenticated%';
      v_note := v_note || 'foreign token: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'get_listing_donor_names still works for older builds';
  v_ok := null; v_note := 'no claimed reservation found';
  if v_res.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rcp, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.get_listing_donor_names(array[v_post.id]);
      perform set_config('role', 'postgres', true);
      v_ok := n = 1;
      v_note := format('%s name(s) returned for the reserved listing', n);
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nReservation checked: "%s" (claimed), recipient %s, donor %s. Outsider: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_post.name, 'none'), coalesce(v_rcp.name, 'none'),
    coalesce(v_donor.name, 'none'), coalesce(v_out.name, 'none'), v_report);
end $$;
