-- Salu-Salo bug fix pass, group A: safety check for the live database.
--
-- Run it in the Supabase dashboard after migrations/20261005_bugfix_security_integrity.sql:
-- SQL Editor -> New query -> paste this whole file -> Run. Leave the role on
-- "postgres".
--
-- It takes the donor of the newest listing (or the newest account), the
-- newest other account (the "recipient") and the next other account (the
-- "outsider"), plus a made-up Firebase account that has no SaluSalo profile. It
-- pretends to be each of them, the way the app's Firebase token does:
--   * #1: it posts a test listing as the donor, reserves from it as the
--     recipient, and checks who may cancel and claim that reservation;
--   * #10: who may read the photos of the newest visible listing, before and
--     after it is removed;
--   * #11: that the database itself refuses bad new listings.
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
  v_post       public.food_posts;
  v_row        public.food_posts;
  v_res        public.reservations;
  c_donor      text;
  c_rec        text;
  c_out        text;
  c_ghost      text;
  c_iss        text := 'https://securetoken.google.com/salusalo';
  v_test_id    uuid := gen_random_uuid();
  v_bad_id     uuid;
  v_id         uuid;
  v_status     text;
  v_qty        integer;
  v_out_linked boolean := false;
  v_has_photo  boolean := false;
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

  -- The newest listing anyone may see, with a cover photo in Storage (for #10).
  select * into v_post from public.food_posts p
  where p.status = 'AVAILABLE' and p.expiration_date > now() and p.image_url is not null
    and exists (select 1 from storage.objects o where o.bucket_id = 'food-images' and o.name = p.image_url)
  order by p.created_at desc limit 1;
  if v_post.id is not null and v_out.id is not null then
    v_out_linked := v_post.donor_id = v_out.id
      or exists (select 1 from public.reservations where food_post_id = v_post.id and recipient_id = v_out.id)
      or exists (select 1 from public.saved_listings where food_post_id = v_post.id and user_id = v_out.id)
      or exists (select 1 from public.conversations where food_post_id = v_post.id
                 and (recipient_id = v_out.id or donor_id = v_out.id));
  end if;

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_out := json_build_object('sub', v_out.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  -- A valid Firebase token (with the role claim) for an account with no users row.
  c_ghost := json_build_object('sub', 'safety-check-no-profile-' || md5(random()::text), 'role', 'authenticated',
    'iss', c_iss, 'aud', 'salusalo')::text;

  -- ---------------------------------------------------------------------------
  -- #1: cancel and claim need a profile and the right person.
  -- ---------------------------------------------------------------------------

  v_label := 'Sanity: the donor posts a test listing (3 packs) and the recipient reserves 1';
  v_ok := null; v_note := 'fewer than two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price)
      values (v_test_id, v_donor.id, 'Safety check: reservations', 'Donation', 3, 'pack',
        'Safety check plaza', now() + interval '1 day', 'AVAILABLE', 0);
      perform set_config('request.jwt.claims', c_rec, true);
      select * into v_res from public.create_reservation(v_test_id, 1);
      perform set_config('role', 'postgres', true);
      select quantity into v_qty from public.food_posts where id = v_test_id;
      v_ok := v_res.status = 'RESERVED' and v_qty = 2;
      v_note := format('reservation %s, %s left', v_res.status, v_qty);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the made-up account has a valid token but no profile';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_ghost, true);
    perform set_config('role', 'authenticated', true);
    v_ok := public.is_salusalo_firebase_jwt() and public.current_salusalo_profile_id() is null;
    perform set_config('role', 'postgres', true);
    v_note := case when v_ok then 'token accepted, no profile' else 'unexpected' end;
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- Each refusal below must leave the reservation RESERVED and 2 packs left.
  for i in 1 .. 7 loop
    v_label := case i
      when 1 then 'An account without a profile cannot cancel the reservation'
      when 2 then 'An account without a profile cannot claim the reservation'
      when 3 then 'The donor cannot cancel the recipient''s reservation'
      when 4 then 'The recipient cannot claim their own reservation (only the donor can)'
      when 5 then 'The outsider cannot cancel the recipient''s reservation'
      when 6 then 'The outsider cannot claim a reservation on someone else''s listing'
      else 'Without a login, cancel_reservation is refused' end;
    v_ok := null; v_note := 'no test reservation';
    if i in (5, 6) and v_out.id is null then
      v_note := 'no third account';
    elsif v_res.id is not null then
      begin
        perform set_config('request.jwt.claims',
          case when i in (1, 2) then c_ghost when i = 3 then c_donor when i = 4 then c_rec
               when i in (5, 6) then c_out else '' end, true);
        perform set_config('role', case when i = 7 then 'anon' else 'authenticated' end, true);
        if i in (1, 3, 5, 7) then
          perform public.cancel_reservation(v_res.id);
        else
          perform public.claim_reservation(v_res.id);
        end if;
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := 'it was allowed';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := case i
          when 1 then sqlerrm = 'Profile not found'
          when 2 then sqlerrm = 'Profile not found'
          when 3 then sqlerrm like '%only your own reservation%'
          when 4 then sqlerrm like '%Only the food donor%'
          when 5 then sqlerrm like '%only your own reservation%'
          when 6 then sqlerrm like '%Only the food donor%'
          else sqlstate = '42501' or sqlerrm like '%Not authenticated%' end;
      end;
      select r.status, p.quantity into v_status, v_qty
      from public.reservations r join public.food_posts p on p.id = r.food_post_id
      where r.id = v_res.id;
      if v_status <> 'RESERVED' or v_qty <> 2 then
        v_ok := false;
        v_note := v_note || format(' (but the reservation is now %s with %s left)', v_status, v_qty);
      end if;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- The two allowed actions, each undone at once (the inner error rolls back
  -- just that block).
  v_label := 'The recipient can cancel their reservation, and the quantity comes back (undone at once)';
  v_ok := null; v_note := 'no test reservation';
  if v_res.id is not null then
    v_ok := false;
    begin
      begin
        perform set_config('request.jwt.claims', c_rec, true);
        perform set_config('role', 'authenticated', true);
        perform public.cancel_reservation(v_res.id);
        perform set_config('role', 'postgres', true);
        select r.status, p.quantity into v_status, v_qty
        from public.reservations r join public.food_posts p on p.id = r.food_post_id
        where r.id = v_res.id;
        raise exception 'UNDO_CANCEL';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_CANCEL' then raise; end if;
      end;
      v_ok := v_status = 'CANCELLED' and v_qty = 3;
      v_note := format('%s, %s left', v_status, v_qty);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The donor can mark the reservation claimed (undone at once)';
  v_ok := null; v_note := 'no test reservation';
  if v_res.id is not null then
    v_ok := false;
    begin
      begin
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        perform public.claim_reservation(v_res.id);
        perform set_config('role', 'postgres', true);
        select status into v_status from public.reservations where id = v_res.id;
        raise exception 'UNDO_CLAIM';
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'UNDO_CLAIM' then raise; end if;
      end;
      v_ok := v_status = 'CLAIMED';
      v_note := v_status;
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- #10: listing photos.
  -- ---------------------------------------------------------------------------

  -- The read rule finds a photo's listing by the second folder, so only a path
  -- without its listing's ID loses readers. A photo left in an older Firebase
  -- UID folder of its donor (an account re-linked to a new UID) stays readable.
  v_label := 'Every listing photo path has its listing''s ID as second folder (<Firebase UID>/<listing ID>/<file>)';
  v_ok := false;
  begin
    select count(*) filter (where split_part(x.path, '/', 2) <> p.id::text),
           count(*) filter (where split_part(x.path, '/', 1) <> u.firebase_uid)
      into n, v_qty
    from public.food_posts p
    join public.users u on u.id = p.donor_id
    cross join lateral unnest(array_remove(array[p.image_url], null) || coalesce(p.extra_image_paths, '{}')) as x(path);
    v_ok := n = 0;
    v_note := format('%s path(s) without their listing''s ID (only their uploader can read them); %s in an older Firebase UID folder of the donor (still readable)', n, v_qty);
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the outsider can read the cover of a listing they can see';
  v_ok := null; v_note := 'no visible listing with a stored cover, or no third account';
  if v_out_linked then
    v_note := 'the outsider owns, reserved, saved or chatted about the newest listing';
  elsif v_post.id is not null and v_out.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from storage.objects where bucket_id = 'food-images' and name = v_post.image_url;
      perform set_config('role', 'postgres', true);
      v_has_photo := n = 1;
      v_ok := v_has_photo;
      v_note := format('%s object(s) readable', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Of other people''s photos, the outsider reads exactly those of listings they may see, saved or chat about';
  v_ok := null; v_note := 'no third account';
  if v_out.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from storage.objects o
      where o.bucket_id = 'food-images'
        and (storage.foldername(o.name))[1] is distinct from v_out.firebase_uid;
      perform set_config('role', 'postgres', true);
      -- As postgres, the same rule written out on its own.
      select count(*) into v_qty from storage.objects o
      where o.bucket_id = 'food-images'
        and (storage.foldername(o.name))[1] is distinct from v_out.firebase_uid
        and exists (select 1 from public.food_posts p
                    where (p.image_url = o.name or o.name = any(p.extra_image_paths))
                      and ((p.status = 'AVAILABLE' and p.expiration_date > now())
                           or exists (select 1 from public.reservations r where r.food_post_id = p.id and r.recipient_id = v_out.id)
                           or exists (select 1 from public.saved_listings s where s.food_post_id = p.id and s.user_id = v_out.id)
                           or exists (select 1 from public.conversations c where c.food_post_id = p.id
                                      and (c.recipient_id = v_out.id or c.donor_id = v_out.id))));
      v_ok := n = v_qty;
      v_note := format('%s readable, %s expected, %s other people''s photo(s) in the bucket', n, v_qty,
        (select count(*) from storage.objects o where o.bucket_id = 'food-images'
           and (storage.foldername(o.name))[1] is distinct from v_out.firebase_uid));
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'After the listing is removed, the outsider can no longer read its cover, and the donor still can';
  v_ok := null; v_note := 'the sanity check above did not pass';
  if v_has_photo then
    v_ok := false;
    begin
      update public.food_posts set status = 'REMOVED' where id = v_post.id;
      select firebase_uid into v_status from public.users where id = v_post.donor_id;
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from storage.objects where bucket_id = 'food-images' and name = v_post.image_url;
      perform set_config('request.jwt.claims',
        json_build_object('sub', v_status, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text, true);
      select count(*) into v_qty from storage.objects where bucket_id = 'food-images' and name = v_post.image_url;
      perform set_config('role', 'postgres', true);
      v_ok := n = 0 and v_qty = 1;
      v_note := format('outsider: %s, donor: %s', n, v_qty);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'An account without a profile can read no one''s listing photos';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_ghost, true);
    perform set_config('role', 'authenticated', true);
    select count(*) into n from storage.objects where bucket_id = 'food-images';
    perform set_config('role', 'postgres', true);
    v_ok := n = 0;
    v_note := format('%s readable', n);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- #11: the database checks new listings.
  -- ---------------------------------------------------------------------------

  v_label := 'A new listing gets the server''s time, not the date the app sends';
  v_ok := null; v_note := 'no account';
  if v_donor.id is not null then
    v_ok := false;
    begin
      v_id := gen_random_uuid();
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, created_at, updated_at)
      values (v_id, v_donor.id, 'Safety check: dates', 'Donation', 1, 'pack', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, '2099-01-01', '2000-01-01');
      perform set_config('role', 'postgres', true);
      select * into v_row from public.food_posts where id = v_id;
      v_ok := v_row.created_at = now() and v_row.updated_at = now();
      v_note := format('stored created_at %s', v_row.created_at);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  for i in 1 .. 5 loop
    v_label := case i
      when 1 then 'A new listing with quantity 0 is refused'
      when 2 then 'A new listing with quantity 10,001 is refused'
      when 3 then 'A new listing that already expired is refused'
      when 4 then 'A new listing whose pickup already ended is refused'
      else 'A new listing with a 2,001-character description is refused' end;
    v_ok := null; v_note := 'no account';
    if v_donor.id is not null then
      v_ok := false;
      begin
        v_bad_id := gen_random_uuid();
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        insert into public.food_posts (id, donor_id, name, description, category, quantity, unit,
          pickup_location, expiration_date, status, price, pickup_start, pickup_end)
        values (v_bad_id, v_donor.id, 'Safety check: refused',
          case when i = 5 then repeat('a', 2001) end, 'Donation',
          case i when 1 then 0 when 2 then 10001 else 1 end, 'pack', 'Safety check plaza',
          case when i = 3 then now() - interval '1 minute' else now() + interval '1 day' end,
          'AVAILABLE', 0,
          case when i = 4 then now() - interval '2 hours' end,
          case when i = 4 then now() - interval '1 hour' end);
        perform set_config('role', 'postgres', true);
        v_note := 'it was stored';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := sqlerrm like case i when 1 then 'Enter a quantity%' when 2 then 'Enter a quantity%'
          when 3 then 'Choose a future expiration%' when 4 then 'Pickup must end in the future%'
          else 'The description must be%' end;
      end;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  v_label := 'An edit to a 2,001-character description is refused, and the listing is unchanged';
  v_ok := null; v_note := 'the test listing was not created';
  if v_res.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post(v_test_id, 'Safety check: reservations', repeat('b', 2001), 'Donation',
        0, 'Safety check plaza', now() + interval '1 day', null, null, null, null, null);
      perform set_config('role', 'postgres', true);
      v_note := 'it was saved';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm like 'The description must be%'
        and (select description from public.food_posts where id = v_test_id) is null;
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s. Outsider: %s. Photo listing: "%s".%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'),
    coalesce(v_out.name, 'none'), coalesce(v_post.name, 'none'), v_report);
end $$;
