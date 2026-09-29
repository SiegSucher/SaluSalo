-- Salu-Salo bug fix pass, round 2: safety check for one rating per reviewer (audit #12).
--
-- Run it in the Supabase dashboard after migrations/20261008_bugfix_one_rating_per_reviewer.sql:
-- SQL Editor -> New query -> paste this whole file -> Run. Leave the role on
-- "postgres".
--
-- It takes the donor of the newest listing (or the newest account), the
-- newest other account (the "recipient") and the next one (the "second
-- reviewer"). As them it posts a test listing, makes three reservations (two
-- by the recipient, one by the second reviewer), has the donor mark all three
-- picked up, and reviews them: the recipient gives 1 star, then 5 stars on a
-- later pickup; the second reviewer gives 3 stars. The donor's rating must
-- count each reviewer once, with their latest review: 2 reviews, 4.0 stars.
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
  c_donor      text;
  c_rec        text;
  c_out        text;
  c_iss        text := 'https://securetoken.google.com/salusalo';
  v_post_id    uuid := gen_random_uuid();
  v_r1         uuid;
  v_r2         uuid;
  v_r3         uuid;
  v_avg        numeric;
  v_count      integer;
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
  select * into v_out from public.users
  where id is distinct from v_donor.id and id is distinct from v_rec.id
  order by created_at desc limit 1;

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_out := json_build_object('sub', v_out.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;

  v_label := 'Sanity: three picked-up reservations reviewed 1 and 5 stars (recipient) and 3 stars (second reviewer)';
  v_ok := null; v_note := 'fewer than three accounts';
  if v_out.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price)
      values (v_post_id, v_donor.id, 'Safety check: ratings', 'Food', 5, 'packs',
        'Safety check plaza', now() + interval '1 day', 'AVAILABLE', 0);
      perform set_config('request.jwt.claims', c_rec, true);
      select id into v_r1 from public.create_reservation(v_post_id, 1);
      select id into v_r2 from public.create_reservation(v_post_id, 1);
      perform set_config('request.jwt.claims', c_out, true);
      select id into v_r3 from public.create_reservation(v_post_id, 1);
      perform set_config('request.jwt.claims', c_donor, true);
      perform public.claim_reservation(v_r1);
      perform public.claim_reservation(v_r2);
      perform public.claim_reservation(v_r3);
      perform set_config('request.jwt.claims', c_rec, true);
      perform public.submit_review(v_r1, 1, null);
      perform set_config('role', 'postgres', true);
      -- Make the 1-star review the older one (inside one transaction every
      -- review gets the same time).
      update public.reviews set created_at = now() - interval '1 hour' where reservation_id = v_r1;
      perform set_config('role', 'authenticated', true);
      perform public.submit_review(v_r2, 5, null);
      perform set_config('request.jwt.claims', c_out, true);
      perform public.submit_review(v_r3, 3, null);
      perform set_config('role', 'postgres', true);
      select count(*) into n from public.reviews where reviewee_id = v_donor.id
        and reservation_id in (v_r1, v_r2, v_r3);
      v_ready := n = 3;
      v_ok := v_ready;
      v_note := format('%s reviews stored', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- Only the test's reviews are compared: the donor may already have others.
  v_label := 'The listing''s donor rating counts each reviewer once, with their latest review';
  v_ok := null; v_note := 'the sanity check did not pass';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select rating_average, rating_count into v_avg, v_count
      from public.get_listing_donor_profiles(array[v_post_id]);
      perform set_config('role', 'postgres', true);
      -- The expected numbers: the donor's other reviewers' latest reviews, plus 5 and 3.
      select round(avg(l.rating), 1), count(*) into v_note, n
      from (select distinct on (r.reviewer_id) r.rating from public.reviews r
            where r.reviewee_id = v_donor.id
            order by r.reviewer_id, r.updated_at desc, r.created_at desc, r.id) l;
      v_ok := v_count = n and v_avg = v_note::numeric;
      v_note := format('%s stars from %s review(s); expected %s from %s', v_avg, v_count, v_note, n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The recipient''s two reviews count once, as 5 stars, everywhere the rating shows';
  v_ok := null; v_note := 'the sanity check did not pass';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.get_my_received_reviews(100) where reviewer_name = v_rec.name;
      v_count := n;
      select count(*) into n from public.get_listing_donor_reviews(v_post_id, 50)
        where reviewer_name = v_rec.name and rating = 5;
      select rating_count into v_avg from public.get_my_rating_summary();
      perform set_config('role', 'postgres', true);
      v_ok := v_count = 1 and n = 1
        and v_avg = (select count(distinct reviewer_id) from public.reviews where reviewee_id = v_donor.id);
      v_note := format('received reviews listed from the recipient: %s; their 5-star review on the listing page: %s; own count %s',
        v_count, n, v_avg);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Both reviews stay stored, and the recipient still sees each as their own';
  v_ok := null; v_note := 'the sanity check did not pass';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.get_my_given_reviews() where reservation_id in (v_r1, v_r2);
      perform set_config('role', 'postgres', true);
      v_ok := n = 2;
      v_note := format('%s of 2 in the recipient''s own reviews', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login, the rating functions are refused';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', '', true);
    perform set_config('role', 'anon', true);
    perform public.get_my_rating_summary();
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_ok := sqlstate = '42501' or sqlerrm like '%Not authenticated%';
    v_note := sqlerrm;
  end;
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s. Second reviewer: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'),
    coalesce(v_out.name, 'none'), v_report);
end $$;
