-- Salu-Salo admin Analytics: safety check for the live database (PROJECT_STATUS.md §26).
--
-- Run it in the Supabase dashboard after migrations/20261013_admin_analytics.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres".
--
-- It takes the donor of the newest listing (or the newest account), the newest
-- other account (the "recipient") and, if there is one, a third account (the
-- "admin"; otherwise the recipient is also the admin), and sets their roles itself.
-- The admin reads Analytics, then the donor posts a test listing, the recipient
-- reserves 2 and the donor marks them picked up, and the admin reads Analytics
-- again: every number must have moved by exactly that much.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the check
-- did (roles, listing, reservation, alerts), so nothing is saved. The error text is
-- the report; it starts with "SAFETY CHECK FINISHED".

do $$
declare
  v_donor   public.users;
  v_rec     public.users;
  v_admin   public.users;
  c_donor   text;
  c_rec     text;
  c_admin   text;
  c_iss     text := 'https://securetoken.google.com/salusalo';
  v_l1      uuid := gen_random_uuid();
  v_res     public.reservations;
  v_before  jsonb;
  v_after   jsonb;
  v_j       jsonb;
  v_last    integer;
  v_w       integer;
  n         bigint;
  v_bad     text;
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

  update public.users set role = 'RECIPIENT' where id in (v_donor.id, v_rec.id);
  update public.users set role = 'ADMIN' where id = v_admin.id;

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec   := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_admin := json_build_object('sub', v_admin.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;

  -- 1
  v_label := 'Sanity: the admin reads 12 weeks of Analytics';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_before := public.admin_analytics(12);
    perform set_config('role', 'postgres', true);
    v_ok := jsonb_array_length(v_before -> 'weekStarts') = 12;
    v_note := format('listings %s, reservations %s', v_before -> 'kpis' ->> 'listings', v_before -> 'kpis' ->> 'reservations');
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'Sanity: the donor posts 5 packs, the recipient reserves 2, the donor marks them picked up';
  v_ok := null; v_note := 'fewer than two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l1, v_donor.id, 'Safety check: analytics', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours');
      perform set_config('request.jwt.claims', c_rec, true);
      select * into v_res from public.create_reservation_with_hold(v_l1, 2, 0, 30);
      perform set_config('request.jwt.claims', c_donor, true);
      perform public.claim_reservation(v_res.id);
      perform set_config('role', 'postgres', true);
      select count(*) into n from public.reservations where id = v_res.id and status = 'CLAIMED';
      v_ok := n = 1;
      v_note := format('claimed %s', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'Every number moved by exactly the test''s listing, reservation and pickup';
  v_ok := null; v_note := 'check 1 or 2 did not pass';
  if a_ok[1] and a_ok[2] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_after := public.admin_analytics(12);
      perform set_config('role', 'postgres', true);
      v_last := 11;
      v_ok := (v_after -> 'kpis' ->> 'listings')::int = (v_before -> 'kpis' ->> 'listings')::int + 1
          and (v_after -> 'kpis' ->> 'reservations')::int = (v_before -> 'kpis' ->> 'reservations')::int + 1
          and (v_after -> 'kpis' ->> 'completed')::int = (v_before -> 'kpis' ->> 'completed')::int + 1
          and (v_after -> 'kpis' ->> 'itemsRescued')::numeric = (v_before -> 'kpis' ->> 'itemsRescued')::numeric + 2
          and (v_after -> 'series' -> 'listings' ->> v_last)::int = (v_before -> 'series' -> 'listings' ->> v_last)::int + 1
          and (v_after -> 'series' -> 'completed' ->> v_last)::int = (v_before -> 'series' -> 'completed' ->> v_last)::int + 1
          and (v_after -> 'series' -> 'itemsRescued' ->> v_last)::numeric = (v_before -> 'series' -> 'itemsRescued' ->> v_last)::numeric + 2
          and (v_after -> 'statusCounts' ->> 'AVAILABLE')::int = (v_before -> 'statusCounts' ->> 'AVAILABLE')::int + 1;
      v_note := format('kpis before %s, after %s', v_before -> 'kpis', v_after -> 'kpis');
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'For 4, 8 and 12 weeks the weekly series add up to the totals, and the statuses to the listings';
  v_ok := false; v_bad := '';
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    foreach v_w in array array[4, 8, 12] loop
      v_j := public.admin_analytics(v_w);
      if jsonb_array_length(v_j -> 'weekStarts') <> v_w
         or (select sum(x::numeric) from jsonb_array_elements_text(v_j -> 'series' -> 'listings') x) <> (v_j -> 'kpis' ->> 'listings')::numeric
         or (select sum(x::numeric) from jsonb_array_elements_text(v_j -> 'series' -> 'reservations') x) <> (v_j -> 'kpis' ->> 'reservations')::numeric
         or (select sum(x::numeric) from jsonb_array_elements_text(v_j -> 'series' -> 'completed') x) <> (v_j -> 'kpis' ->> 'completed')::numeric
         or (select sum(x::numeric) from jsonb_array_elements_text(v_j -> 'series' -> 'itemsRescued') x) <> (v_j -> 'kpis' ->> 'itemsRescued')::numeric
         or (select sum(value::numeric) from jsonb_each_text(v_j -> 'statusCounts')) <> (v_j -> 'kpis' ->> 'listings')::numeric
      then
        v_bad := v_bad || v_w || ' weeks; ';
      end if;
    end loop;
    perform set_config('role', 'postgres', true);
    v_ok := v_bad = '';
    v_note := case when v_bad = '' then 'all three agree' else 'mismatch: ' || v_bad end;
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'Only 4, 8 or 12 weeks are accepted';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    perform public.admin_analytics(5);
    perform set_config('role', 'postgres', true);
    v_note := '5 weeks answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Choose 4, 8 or 12 weeks.';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'A signed-in account that is not an admin is refused';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    perform public.admin_analytics(4);
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Admin access required';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'No one signed out (anon) can run it';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', '{}', true);
    perform set_config('role', 'anon', true);
    perform public.admin_analytics(4);
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm like 'permission denied for function admin_analytics%';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'Privileges: authenticated may run admin_analytics, anon may not, and no client may run its helper';
  v_ok := to_regprocedure('public.admin_analytics(integer)') is not null
      and has_function_privilege('authenticated', 'public.admin_analytics(integer)', 'execute')
      and not has_function_privilege('anon', 'public.admin_analytics(integer)', 'execute')
      and to_regprocedure('public.admin_analytics_kpis(timestamptz,timestamptz)') is not null
      and not has_function_privilege('authenticated', 'public.admin_analytics_kpis(timestamptz,timestamptz)', 'execute')
      and not has_function_privilege('anon', 'public.admin_analytics_kpis(timestamptz,timestamptz)', 'execute');
  v_note := case when v_ok then 'as expected' else 'a privilege is wrong or a function is missing' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'Analytics returns counts only: no names or emails';
  v_ok := null; v_note := 'check 3 did not pass';
  if a_ok[3] then
    v_ok := position('@' in v_after::text) = 0
        and position(v_donor.name in v_after::text) = 0
        and position(v_rec.name in v_after::text) = 0;
    v_note := case when v_ok then 'no personal data' else 'a name or email appears' end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'The previous period is compared only when the first account is older than it';
  v_ok := false; v_bad := '';
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    foreach v_w in array array[4, 8, 12] loop
      v_j := public.admin_analytics(v_w);
      perform set_config('role', 'postgres', true);
      if (jsonb_typeof(v_j -> 'previous') = 'object')
         is distinct from ((select min(created_at) from public.users) <= now() - 2 * v_w * interval '7 days') then
        v_bad := v_bad || v_w || ' weeks; ';
      end if;
      perform set_config('role', 'authenticated', true);
    end loop;
    perform set_config('role', 'postgres', true);
    v_ok := v_bad = '';
    v_note := case when v_bad = '' then format('first account %s', (select min(created_at)::date from public.users)) else 'wrong for ' || v_bad end;
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
