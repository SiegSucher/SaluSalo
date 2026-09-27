-- Salu-Salo Phase 3 demo fixes: safety check for the live database.
--
-- Run it in the Supabase dashboard after migrations/20260930_phase3_demo_fixes.sql:
-- SQL Editor -> New query -> paste this whole file -> Run. Leave the role on
-- "postgres".
--
-- It picks the newest listing that is unexpired and not removed, its donor, and
-- the newest other account (the "outsider"). It pretends to be each of them,
-- the way the app's Firebase token does, and tries things the app must never
-- allow. It then marks that listing REMOVED (as postgres) and checks that it
-- can no longer be edited, reserved, chatted about, saved or seen by the
-- outsider. The "Sanity" checks must succeed: they prove that the pretend
-- sign-in works, so the refusals are real. A check that cannot run reports SKIP.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did, so nothing is saved, no alert is kept and no phone gets a live
-- update. The error text is the report; it starts with "SAFETY CHECK FINISHED".
-- Any other error means the check itself did not run.

do $$
declare
  v_post       public.food_posts;
  v_donor      public.users;
  v_out        public.users;
  c_donor      text;
  c_out        text;
  c_foreign    text;
  v_id         uuid;
  v_outcome    text;
  v_count      integer;
  v_ids        uuid[];
  v_own        uuid[];
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
  select * into v_post from public.food_posts
  where status <> 'REMOVED' and expiration_date > now()
  order by created_at desc
  limit 1;
  if v_post.id is not null then
    select * into v_donor from public.users where id = v_post.donor_id;
    select * into v_out from public.users
    where id <> v_post.donor_id
    order by created_at desc
    limit 1;
  end if;
  if v_out.id is not null then
    -- A reservation or a chat on the listing gives the outsider legitimate
    -- access to it; the "after removal" checks for the outsider are then skipped.
    v_out_linked := exists (select 1 from public.reservations
                            where food_post_id = v_post.id and recipient_id = v_out.id)
                 or exists (select 1 from public.conversations
                            where food_post_id = v_post.id and recipient_id = v_out.id);
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

  v_label := 'Sanity: the donor''s pretend sign-in works and sees the listing';
  v_ok := null; v_note := 'no unexpired, not removed listing found';
  if v_post.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      v_id := public.current_salusalo_profile_id();
      select count(*) into n from public.food_posts where id = v_post.id;
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(v_id = v_donor.id and n = 1, false);
      v_note := format('signed in: %s, listing seen: %s', case when v_id is null then 'no' else 'yes' end, n);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the outsider''s pretend sign-in works';
  v_ok := null; v_note := 'no other account found';
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

  -- Runs remove_my_food_post as the donor, then undoes it at once (the inner
  -- error rolls back just this block).
  v_label := 'Sanity: the donor can delete or remove their own listing (undone at once)';
  v_ok := null; v_note := 'no unexpired, not removed listing found';
  if v_post.id is not null then
    v_ok := false;
    begin
      begin
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        select r.outcome, r.cancelled_reservations into v_outcome, v_count
        from public.remove_my_food_post(v_post.id) r;
        perform set_config('role', 'postgres', true);
        raise exception 'UNDO_REMOVE';
      exception when others then
        if sqlerrm <> 'UNDO_REMOVE' then raise; end if;
      end;
      v_ok := v_outcome in ('DELETED', 'REMOVED');
      v_note := format('%s, %s open reservation(s) would be cancelled', v_outcome, v_count);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Deleting and removing.
  -- ---------------------------------------------------------------------------

  v_label := 'A direct DELETE by the donor is refused';
  v_ok := null; v_note := 'no unexpired, not removed listing found';
  if v_post.id is not null then
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      delete from public.food_posts where id = v_post.id;
      get diagnostics n = row_count;
      perform set_config('role', 'postgres', true);
      v_ok := n = 0;
      v_note := format('no error, %s row(s) deleted', n);
    exception when others then
      v_ok := sqlstate = '42501';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot remove the donor''s listing';
  v_ok := null; v_note := 'no listing or no other account found';
  if v_post.id is not null and v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.remove_my_food_post(v_post.id);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the listing was removed';
    exception when others then
      v_ok := sqlerrm like '%only your own listing%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login, remove_my_food_post is refused';
  v_ok := null; v_note := 'no unexpired, not removed listing found';
  if v_post.id is not null then
    begin
      perform set_config('request.jwt.claims', '', true);
      perform set_config('role', 'anon', true);
      perform public.remove_my_food_post(v_post.id);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the listing was removed';
    exception when others then
      v_ok := sqlstate = '42501' or sqlerrm like '%Not authenticated%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A token from another Firebase project cannot remove the listing';
  v_ok := null; v_note := 'no unexpired, not removed listing found';
  if v_post.id is not null then
    begin
      perform set_config('request.jwt.claims', c_foreign, true);
      perform set_config('role', 'authenticated', true);
      perform public.remove_my_food_post(v_post.id);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the listing was removed';
    exception when others then
      v_ok := sqlerrm like '%Not authenticated%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Saved listing names.
  -- ---------------------------------------------------------------------------

  v_label := 'The outsider''s get_my_saved_listing_names returns only their own saved listings';
  v_ok := null; v_note := 'no other account found';
  if v_out.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select coalesce(array_agg(g.food_post_id order by g.food_post_id), '{}') into v_ids
      from public.get_my_saved_listing_names() g;
      perform set_config('role', 'postgres', true);
      select coalesce(array_agg(s.food_post_id order by s.food_post_id), '{}') into v_own
      from public.saved_listings s where s.user_id = v_out.id;
      select count(*) into m from public.saved_listings where user_id <> v_out.id;
      v_ok := v_ids = v_own;
      v_note := format('%s returned, %s saved by the outsider, %s saved by other accounts',
        cardinality(v_ids), cardinality(v_own), m);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login, get_my_saved_listing_names is refused';
  begin
    perform set_config('request.jwt.claims', '', true);
    perform set_config('role', 'anon', true);
    select count(*) into n from public.get_my_saved_listing_names();
    perform set_config('role', 'postgres', true);
    v_ok := false; v_note := format('%s row(s) returned', n);
  exception when others then
    v_ok := sqlstate = '42501';
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- After the listing is marked REMOVED (as postgres; undone at the end).
  -- ---------------------------------------------------------------------------

  if v_post.id is not null then
    update public.food_posts set status = 'REMOVED' where id = v_post.id;
    -- The outsider's own saved row, if any, would hide the insert check.
    if v_out.id is not null then
      delete from public.saved_listings where user_id = v_out.id and food_post_id = v_post.id;
    end if;
  end if;

  v_label := 'After removal: the donor cannot edit the listing';
  v_ok := null; v_note := 'no unexpired, not removed listing found';
  if v_post.id is not null then
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.update_my_food_post(v_post.id, v_post.name, v_post.description, v_post.category,
        v_post.price, v_post.pickup_location, v_post.expiration_date, v_post.pickup_start,
        v_post.pickup_end, v_post.handover, v_post.allergens, v_post.storage_notes);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the edit was accepted';
    exception when others then
      v_ok := sqlerrm like '%was removed%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'After removal: the outsider cannot reserve it';
  v_ok := null; v_note := 'no listing or no other account found';
  if v_post.id is not null and v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.create_reservation(v_post.id, 1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the reservation was accepted';
    exception when others then
      v_ok := sqlerrm like '%removed this listing%' or sqlerrm like '%no longer available%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'After removal: the outsider cannot start a chat about it';
  v_ok := null; v_note := 'no listing or no other account found';
  if v_out_linked then
    v_note := 'the outsider already has a reservation or chat on this listing';
  elsif v_post.id is not null and v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.start_listing_conversation(v_post.id);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'a chat was started';
    exception when others then
      v_ok := sqlerrm like '%no longer available%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'After removal: the outsider cannot save it';
  v_ok := null; v_note := 'no listing or no other account found';
  if v_post.id is not null and v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      insert into public.saved_listings(user_id, food_post_id) values (v_out.id, v_post.id);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the listing was saved';
    exception when others then
      v_ok := sqlstate = '42501';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'After removal: the outsider cannot see it or its donor''s name';
  v_ok := null; v_note := 'no listing or no other account found';
  if v_out_linked then
    v_note := 'the outsider already has a reservation or chat on this listing';
  elsif v_post.id is not null and v_out.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.food_posts where id = v_post.id;
      select count(*) into m from public.get_listing_donor_names(array[v_post.id]);
      perform set_config('role', 'postgres', true);
      v_ok := n = 0 and m = 0;
      v_note := format('listing rows seen: %s, donor names returned: %s', n, m);
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nListing checked: "%s" by %s. Outsider: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_post.name, 'none'), coalesce(v_donor.name, 'none'),
    coalesce(v_out.name, 'none'), v_report);
end $$;
