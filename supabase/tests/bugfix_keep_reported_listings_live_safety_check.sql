-- Salu-Salo bug fix pass, round 4: safety check for the live database
-- (PROJECT_STATUS.md §30, audit #36).
--
-- Run it in the Supabase dashboard after migrations/20261015_bugfix_keep_reported_listings.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres".
--
-- It takes the donor of the newest listing (or the newest account) and the
-- newest other account (the "recipient"). As the donor it posts four test
-- listings; as the recipient it reports one, messages about another and
-- reserves (then cancels) the third; then the donor deletes all four. Only the
-- listing nobody touched may be deleted; the other three must stay as REMOVED
-- with their report, chat and reservation. The "Sanity" check must succeed: it
-- proves that the pretend sign-in works. A check that cannot run reports SKIP.
-- The recipient sends 1 report, 1 message and 1 reservation, so a check fails
-- if that account already hit a limit (10 reports a day, 10 reservations in 10
-- minutes).
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did, so nothing is saved, no alert is kept and no phone gets a live
-- update. The error text is the report; it starts with "SAFETY CHECK
-- FINISHED". Any other error means the check itself did not run.

do $$
declare
  v_donor      public.users;
  v_rec        public.users;
  c_donor      text;
  c_rec        text;
  c_iss        text := 'https://securetoken.google.com/salusalo';
  v_plain      uuid := gen_random_uuid();   -- nobody touches it
  v_reported   uuid := gen_random_uuid();   -- the recipient reports it
  v_chatted    uuid := gen_random_uuid();   -- the recipient messages the donor about it
  v_reserved   uuid := gen_random_uuid();   -- reserved, then cancelled (as before)
  v_conv       public.conversations;
  v_res        public.reservations;
  v_report_id  uuid;
  v_outcome    text;
  v_cancelled  integer;
  v_status     text;
  n            bigint;
  n2           bigint;
  v_label      text;
  v_ok         boolean;
  v_note       text;
  v_setup      boolean := false;
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
  v_label := 'Sanity: the donor posts four listings; the recipient reports one, messages about one and reserves one';
  v_ok := null; v_note := 'needs two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      select x.id, v_donor.id, x.name, 'Other', 3, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours'
      from (values (v_plain, 'Safety check: untouched'), (v_reported, 'Safety check: reported'),
                   (v_chatted, 'Safety check: chatted'), (v_reserved, 'Safety check: reserved')) x(id, name);

      perform set_config('request.jwt.claims', c_rec, true);
      perform public.submit_report('LISTING', v_reported, 'MISLEADING', 'Safety check report');
      select * into v_conv from public.start_listing_conversation(v_chatted);
      perform public.send_message(v_conv.id, 'Safety check message');
      select * into v_res from public.create_reservation(v_reserved, 1);
      perform public.cancel_reservation(v_res.id);
      perform set_config('role', 'postgres', true);
      select rp.id into v_report_id from public.reports rp
      where rp.reporter_id = v_rec.id and rp.food_post_id = v_reported;

      v_setup := true;
      v_ok := true;
      v_note := 'all set up';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2-5: the donor deletes each listing; only the untouched one is really deleted.
  for i in 1 .. 4 loop
    v_label := case i
      when 1 then 'A listing nobody reserved, reported or messaged about is deleted (as before)'
      when 2 then 'A reported listing is kept as REMOVED, and its report still points at it'
      when 3 then 'A listing with a chat is kept as REMOVED, and the chat and its message stay'
      else 'A listing that was reserved once is kept as REMOVED (as before)' end;
    v_ok := null; v_note := 'the setup above failed';
    if v_setup then
      v_ok := false;
      begin
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        select r.outcome, r.cancelled_reservations into v_outcome, v_cancelled
        from public.remove_my_food_post(case i when 1 then v_plain when 2 then v_reported
                                               when 3 then v_chatted else v_reserved end) r;
        perform set_config('role', 'postgres', true);
        select p.status into v_status from public.food_posts p
        where p.id = case i when 1 then v_plain when 2 then v_reported when 3 then v_chatted else v_reserved end;
        if i = 1 then
          v_ok := v_outcome = 'DELETED' and v_status is null;
        elsif i = 2 then
          select count(*) into n from public.reports rp where rp.food_post_id = v_reported;
          v_ok := v_outcome = 'REMOVED' and v_cancelled = 0 and v_status = 'REMOVED' and n = 1;
        elsif i = 3 then
          select count(*) into n from public.messages m
          join public.conversations c on c.id = m.conversation_id
          where c.food_post_id = v_chatted;
          v_ok := v_outcome = 'REMOVED' and v_cancelled = 0 and v_status = 'REMOVED' and n = 1;
        else
          v_ok := v_outcome = 'REMOVED' and v_cancelled = 0 and v_status = 'REMOVED';
        end if;
        v_note := format('outcome %s, %s cancelled, listing %s%s', v_outcome, v_cancelled,
          coalesce(v_status, 'gone'), case when i in (2, 3) then format(', %s kept', n) else '' end);
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
      end;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- 6
  v_label := 'The admin''s report shows the removed listing itself, not "Deleted listing"';
  v_ok := null; v_note := 'the setup above failed';
  if v_setup then
    select public.admin_report_json(rp) -> 'target' ->> 'id' into v_note
    from public.reports rp where rp.id = v_report_id;
    v_ok := coalesce(v_note = v_reported::text, false);
    v_note := 'target ' || coalesce(v_note, 'missing (Deleted listing)');
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'The removed listings are hidden from the recipient, who still has the chat';
  v_ok := null; v_note := 'the setup above failed';
  if v_setup then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.food_posts p where p.id in (v_reported, v_chatted);
      select count(*) into n2 from public.get_my_conversations(v_conv.id);
      perform set_config('role', 'postgres', true);
      v_ok := n = 0 and n2 = 1;
      v_note := format('listings seen: %s; chat in Messages: %s', n, case when n2 = 1 then 'yes' else 'no' end);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'Only the donor may delete: the recipient is refused';
  v_ok := null; v_note := 'needs two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      perform public.remove_my_food_post(coalesce(
        (select p.id from public.food_posts p where p.donor_id = v_donor.id order by p.created_at desc limit 1),
        gen_random_uuid()));
      perform set_config('role', 'postgres', true);
      v_note := 'it was allowed';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm in ('You can delete only your own listing.', 'This listing no longer exists.');
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'remove_my_food_post: signed-in callers only (not anon, not PUBLIC)';
  v_ok := has_function_privilege('authenticated', 'public.remove_my_food_post(uuid)', 'execute')
    and not has_function_privilege('anon', 'public.remove_my_food_post(uuid)', 'execute');
  v_note := case when v_ok then 'as expected' else 'wrong privileges' end;
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'), v_report);
end $$;
