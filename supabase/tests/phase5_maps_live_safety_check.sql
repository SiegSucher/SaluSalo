-- Salu-Salo Phase 5 part 2 (Maps, the pickup pin): safety check for the live
-- database.
--
-- Run it in the Supabase dashboard after migrations/20261003_phase5_maps.sql:
-- SQL Editor -> New query -> paste this whole file -> Run. Leave the role on
-- "postgres".
--
-- It picks the donor of the newest listing (or, if there is no listing, the
-- newest account) and the newest other account (the "outsider"). It pretends
-- to be each of them, the way the app's Firebase token does, inserts two test
-- listings as the donor through the same insert policy the app uses, and tries
-- things the app must never allow. The "Sanity" checks must succeed: they prove
-- that the pretend sign-in and the normal pin changes work, so the refusals are
-- real. A check that cannot run reports SKIP.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did (the test listings and every change to them), so nothing is saved
-- and no phone sees the test listings. The error text is the report; it starts
-- with "SAFETY CHECK FINISHED". Any other error means the check itself did not
-- run.

do $$
declare
  v_donor      public.users;
  v_out        public.users;
  c_donor      text;
  c_out        text;
  c_foreign    text;
  v_exp        timestamptz := now() + interval '1 day';
  v_pin_id     uuid := gen_random_uuid();   -- test listing posted with a pin
  v_two_id     uuid := gen_random_uuid();   -- test listing posted without a pin
  v_pin_ok     boolean := false;            -- the listing with a pin was inserted
  v_two_ok     boolean := false;            -- the listing without a pin was inserted
  v_row        public.food_posts;
  v_before     public.food_posts;
  v_after      public.food_posts;
  v_id         uuid;
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
  order by p.created_at desc
  limit 1;
  if v_donor.id is null then
    select * into v_donor from public.users order by created_at desc limit 1;
  end if;
  if v_donor.id is not null then
    select * into v_out from public.users
    where id <> v_donor.id
    order by created_at desc
    limit 1;
  end if;

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo')::text;
  c_out := json_build_object('sub', v_out.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo')::text;
  -- The donor's user ID, but in a token issued for another Firebase project.
  c_foreign := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/another-project', 'aud', 'another-project')::text;

  -- ---------------------------------------------------------------------------
  -- Sanity checks.
  -- ---------------------------------------------------------------------------

  v_label := 'Sanity: the donor''s pretend sign-in works';
  v_ok := null; v_note := 'no account found';
  if v_donor.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      v_id := public.current_salusalo_profile_id();
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(v_id = v_donor.id, false);
      v_note := format('signed in: %s', case when v_id is null then 'no' else 'yes' end);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the outsider''s pretend sign-in works';
  v_ok := null; v_note := 'no second account found';
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

  v_label := 'Sanity: the donor can post a listing with a pin (the app''s insert), stored with 6 decimals';
  v_ok := null; v_note := 'no account found';
  if v_donor.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, description, category, quantity, unit, image_url,
        pickup_location, expiration_date, status, price, pickup_start, pickup_end, handover, allergens,
        storage_notes, extra_image_paths, pickup_latitude, pickup_longitude)
      values (v_pin_id, v_donor.id, 'Safety check: pin', null, 'Donation', 1, 'pack', null,
        'Safety check plaza', v_exp, 'AVAILABLE', 0, null, null, null, null,
        null, '{}', 9.6478123, 123.8540126);
      perform set_config('role', 'postgres', true);
      select * into v_row from public.food_posts where id = v_pin_id;
      v_pin_ok := v_row.id is not null;
      v_ok := coalesce(v_row.pickup_latitude = 9.647812 and v_row.pickup_longitude = 123.854013, false);
      v_note := format('stored %s, %s', v_row.pickup_latitude, v_row.pickup_longitude);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: a listing can still be posted without a pin (older builds)';
  v_ok := null; v_note := 'no account found';
  if v_donor.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, description, category, quantity, unit, image_url,
        pickup_location, expiration_date, status, price, pickup_start, pickup_end, handover, allergens,
        storage_notes, extra_image_paths)
      values (v_two_id, v_donor.id, 'Safety check: no pin', null, 'Donation', 1, 'pack', null,
        'Safety check plaza', v_exp, 'AVAILABLE', 0, null, null, null, null,
        null, '{}');
      perform set_config('role', 'postgres', true);
      select * into v_row from public.food_posts where id = v_two_id;
      v_two_ok := v_row.id is not null;
      v_ok := coalesce(v_two_ok and v_row.pickup_latitude is null and v_row.pickup_longitude is null, false);
      v_note := format('stored %s, %s', coalesce(v_row.pickup_latitude::text, 'NULL'),
        coalesce(v_row.pickup_longitude::text, 'NULL'));
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the donor can move the pin and change other fields in one call (update_my_food_post_with_pin)';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      select * into v_row from public.update_my_food_post_with_pin(v_pin_id, 'Safety check: pin moved', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 9.66, 123.87);
      perform set_config('role', 'postgres', true);
      select * into v_after from public.food_posts where id = v_pin_id;
      v_ok := coalesce(v_row.pickup_latitude = 9.66 and v_row.pickup_longitude = 123.87
        and v_after.pickup_latitude = 9.66 and v_after.pickup_longitude = 123.87
        and v_after.name = 'Safety check: pin moved', false);
      v_note := format('returned %s, %s; stored %s, %s, name "%s"', v_row.pickup_latitude,
        v_row.pickup_longitude, v_after.pickup_latitude, v_after.pickup_longitude, v_after.name);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the donor can add a pin to a listing that has none';
  v_ok := null; v_note := 'the test listing without a pin was not inserted';
  if v_two_ok then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_two_id, 'Safety check: no pin', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, -9.65, -123.854);
      perform set_config('role', 'postgres', true);
      select * into v_after from public.food_posts where id = v_two_id;
      v_ok := coalesce(v_after.pickup_latitude = -9.65 and v_after.pickup_longitude = -123.854, false);
      v_note := format('stored %s, %s', coalesce(v_after.pickup_latitude::text, 'NULL'), coalesce(v_after.pickup_longitude::text, 'NULL'));
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Inserts.
  -- ---------------------------------------------------------------------------

  v_label := 'A new listing with only a latitude, or only a longitude, is refused';
  v_ok := null; v_note := 'no account found';
  if v_donor.id is not null then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_latitude, pickup_longitude)
      values (gen_random_uuid(), v_donor.id, 'Safety check: half pin', 'Donation', 1, 'pack',
        'Safety check plaza', v_exp, 'AVAILABLE', 0, 9.65, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'latitude only was accepted; ';
    exception when others then
      v_ok := coalesce(v_ok and sqlerrm like '%food_posts_pickup_pin_check%', false);
      v_note := 'latitude only: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_latitude, pickup_longitude)
      values (gen_random_uuid(), v_donor.id, 'Safety check: half pin', 'Donation', 1, 'pack',
        'Safety check plaza', v_exp, 'AVAILABLE', 0, null, 123.854);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'longitude only was accepted';
    exception when others then
      v_ok := coalesce(v_ok and sqlerrm like '%food_posts_pickup_pin_check%', false);
      v_note := v_note || 'longitude only: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A new listing with latitude 91 or longitude 181 is refused';
  v_ok := null; v_note := 'no account found';
  if v_donor.id is not null then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_latitude, pickup_longitude)
      values (gen_random_uuid(), v_donor.id, 'Safety check: bad pin', 'Donation', 1, 'pack',
        'Safety check plaza', v_exp, 'AVAILABLE', 0, 91, 123.854);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'latitude 91 was accepted; ';
    exception when others then
      v_ok := coalesce(v_ok and sqlerrm like '%food_posts_pickup_pin_check%', false);
      v_note := 'latitude 91: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_latitude, pickup_longitude)
      values (gen_random_uuid(), v_donor.id, 'Safety check: bad pin', 'Donation', 1, 'pack',
        'Safety check plaza', v_exp, 'AVAILABLE', 0, 9.65, 181);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'longitude 181 was accepted';
    exception when others then
      v_ok := coalesce(v_ok and sqlerrm like '%food_posts_pickup_pin_check%', false);
      v_note := v_note || 'longitude 181: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Edits. v_before is the listing with a pin as it is now; every refused edit
  -- must leave it exactly like this.
  -- ---------------------------------------------------------------------------

  select * into v_before from public.food_posts where id = v_pin_id;

  v_label := 'Editing with only one pin coordinate is refused, and nothing changes';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_pin_id, 'Safety check: changed', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 9.7, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      select * into v_after from public.food_posts where id = v_pin_id;
      v_ok := coalesce(sqlerrm like '%Send both pickup pin coordinates, or neither.%'
        and v_after.pickup_latitude is not distinct from v_before.pickup_latitude
        and v_after.pickup_longitude is not distinct from v_before.pickup_longitude and v_after.name is not distinct from v_before.name, false);
      v_note := sqlerrm || format(' (pin now %s, %s)', coalesce(v_after.pickup_latitude::text, 'NULL'), coalesce(v_after.pickup_longitude::text, 'NULL'));
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Editing with latitude 90.5, longitude -180.5 or NaN is refused, and nothing changes';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_pin_id, 'Safety check: changed', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 90.5, 123.87);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := '90.5 was accepted; ';
    exception when others then
      v_ok := coalesce(v_ok and sqlerrm like '%not a valid map position%', false);
      v_note := '90.5: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_pin_id, 'Safety check: changed', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 9.66, -180.5);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || '-180.5 was accepted; ';
    exception when others then
      v_ok := coalesce(v_ok and sqlerrm like '%not a valid map position%', false);
      v_note := v_note || '-180.5: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_pin_id, 'Safety check: changed', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 'NaN'::numeric, 123.87);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'NaN was accepted';
    exception when others then
      v_ok := coalesce(v_ok and sqlerrm like '%not a valid map position%', false);
      v_note := v_note || 'NaN: ' || sqlerrm;
    end;
    select * into v_after from public.food_posts where id = v_pin_id;
    v_ok := coalesce(v_ok and v_after.pickup_latitude is not distinct from v_before.pickup_latitude
      and v_after.pickup_longitude is not distinct from v_before.pickup_longitude and v_after.name is not distinct from v_before.name, false);
    v_note := v_note || format(' (pin now %s, %s)', coalesce(v_after.pickup_latitude::text, 'NULL'), coalesce(v_after.pickup_longitude::text, 'NULL'));
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'An edit refused for another field (a blank name) does not move the pin either';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_pin_id, '   ', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 10.1, 124.1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      select * into v_after from public.food_posts where id = v_pin_id;
      v_ok := coalesce(sqlerrm like '%Enter the food name.%'
        and v_after.pickup_latitude is not distinct from v_before.pickup_latitude
        and v_after.pickup_longitude is not distinct from v_before.pickup_longitude, false);
      v_note := sqlerrm || format(' (pin now %s, %s)', coalesce(v_after.pickup_latitude::text, 'NULL'), coalesce(v_after.pickup_longitude::text, 'NULL'));
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'An edit with no pin (both NULL) keeps the pin: a pin cannot be removed';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      select * into v_row from public.update_my_food_post_with_pin(v_pin_id, 'Safety check: no pin sent', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, null, null);
      perform set_config('role', 'postgres', true);
      select * into v_after from public.food_posts where id = v_pin_id;
      v_ok := coalesce(v_after.name = 'Safety check: no pin sent' and v_before.pickup_latitude is not null
        and v_after.pickup_latitude is not distinct from v_before.pickup_latitude
        and v_after.pickup_longitude is not distinct from v_before.pickup_longitude
        and v_row.pickup_latitude is not distinct from v_before.pickup_latitude, false);
      v_note := format('name "%s", pin %s, %s', v_after.name, coalesce(v_after.pickup_latitude::text, 'NULL'), coalesce(v_after.pickup_longitude::text, 'NULL'));
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'An edit from an older build (update_my_food_post) keeps the pin';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post(v_pin_id, 'Safety check: older build', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null);
      perform set_config('role', 'postgres', true);
      select * into v_after from public.food_posts where id = v_pin_id;
      v_ok := coalesce(v_after.name = 'Safety check: older build' and v_before.pickup_latitude is not null
        and v_after.pickup_latitude is not distinct from v_before.pickup_latitude
        and v_after.pickup_longitude is not distinct from v_before.pickup_longitude, false);
      v_note := format('name "%s", pin %s, %s', v_after.name, coalesce(v_after.pickup_latitude::text, 'NULL'), coalesce(v_after.pickup_longitude::text, 'NULL'));
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  select * into v_before from public.food_posts where id = v_pin_id;

  v_label := 'The outsider cannot move the donor''s pin';
  v_ok := null; v_note := 'no second account, or the test listing was not inserted';
  if v_out.id is not null and v_pin_ok then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_pin_id, 'Safety check: outsider', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 10.1, 124.1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      select * into v_after from public.food_posts where id = v_pin_id;
      v_ok := coalesce(sqlerrm like '%You can edit only your own listing.%'
        and v_after.pickup_latitude is not distinct from v_before.pickup_latitude and v_after.name is not distinct from v_before.name, false);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The pin of an expired listing cannot be changed';
  v_ok := null; v_note := 'the test listing without a pin was not inserted';
  if v_two_ok then
    update public.food_posts set expiration_date = now() - interval '1 minute' where id = v_two_id;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_two_id, 'Safety check: expired', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 10.1, 124.1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      v_ok := coalesce(sqlerrm like '%has expired and can no longer be edited.%', false);
      v_note := sqlerrm;
    end;
    update public.food_posts set expiration_date = v_exp where id = v_two_id;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The pin of a REMOVED listing cannot be changed';
  v_ok := null; v_note := 'the test listing without a pin was not inserted';
  if v_two_ok then
    update public.food_posts set status = 'REMOVED' where id = v_two_id;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_two_id, 'Safety check: removed', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 10.1, 124.1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      v_ok := coalesce(sqlerrm like '%was removed and can no longer be edited.%', false);
      v_note := sqlerrm;
    end;
    update public.food_posts set status = 'AVAILABLE' where id = v_two_id;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A listing that does not exist is refused';
  v_ok := null; v_note := 'no account found';
  if v_donor.id is not null then
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(gen_random_uuid(), 'Safety check: missing', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 10.1, 124.1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      v_ok := coalesce(sqlerrm like '%This listing no longer exists.%', false);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login (anon), update_my_food_post_with_pin cannot be called';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    begin
      perform set_config('request.jwt.claims', '', true);
      perform set_config('role', 'anon', true);
      perform public.update_my_food_post_with_pin(v_pin_id, 'Safety check: anon', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 10.1, 124.1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      v_ok := coalesce(sqlerrm like '%permission denied%', false);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A token from another Firebase project is refused';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    begin
      perform set_config('request.jwt.claims', c_foreign, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post_with_pin(v_pin_id, 'Safety check: foreign', null,
        'Donation', 0, 'Safety check plaza', v_exp, null, null, null, null, null, 10.1, 124.1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      v_ok := coalesce(sqlerrm like '%Not authenticated%', false);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The donor cannot change the pin with a direct UPDATE';
  v_ok := null; v_note := 'the test listing with a pin was not inserted';
  if v_pin_ok then
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      update public.food_posts set pickup_latitude = 10.1, pickup_longitude = 124.1 where id = v_pin_id;
      get diagnostics n = row_count;
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := format('the UPDATE ran (%s row(s))', n);
    exception when others then
      v_ok := coalesce(sqlerrm like '%permission denied%', false);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Who sees the pin: exactly who sees the listing.
  -- ---------------------------------------------------------------------------

  v_label := 'The outsider sees the pin of the donor''s available listing';
  v_ok := null; v_note := 'no second account, or the test listing was not inserted';
  if v_out.id is not null and v_pin_ok then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select * into v_row from public.food_posts where id = v_pin_id;
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(v_row.pickup_latitude is not null
        and v_row.pickup_latitude is not distinct from v_before.pickup_latitude
        and v_row.pickup_longitude is not distinct from v_before.pickup_longitude, false);
      v_note := format('outsider sees %s, %s', coalesce(v_row.pickup_latitude::text, 'nothing'),
        coalesce(v_row.pickup_longitude::text, 'nothing'));
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Once the listing expires, the outsider no longer sees it or its pin; the donor still does';
  v_ok := null; v_note := 'no second account, or the test listing was not inserted';
  if v_out.id is not null and v_pin_ok then
    v_ok := false;
    update public.food_posts set expiration_date = now() - interval '1 minute' where id = v_pin_id;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.food_posts where id = v_pin_id;
      perform set_config('request.jwt.claims', c_donor, true);
      select count(*) into m from public.food_posts where id = v_pin_id and pickup_latitude is not null;
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(n = 0 and m = 1, false);
      v_note := format('outsider sees %s row(s); donor sees %s row(s) with the pin', n, m);
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Outsider: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_out.name, 'none'), v_report);
end $$;
