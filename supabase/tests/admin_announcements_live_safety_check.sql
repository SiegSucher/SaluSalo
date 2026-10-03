-- Salu-Salo admin announcements: safety check for the live database (PROJECT_STATUS.md §27).
--
-- Run it in the Supabase dashboard after migrations/20261014_admin_announcements.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres". Run it
-- before anyone sends a real announcement: an admin who sent one in the last 10
-- minutes would reach the limit this check tests sooner.
--
-- It takes the donor of the newest listing, the newest other account (the
-- "recipient") and, if there is one, a third account (the "admin"; otherwise the
-- recipient is also the admin), and sets their roles itself. The admin sends test
-- announcements to each audience; each must reach exactly the accounts in it.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the check
-- did (roles, announcements, alerts, audit rows), so nothing is saved and no phone
-- gets a test announcement. The error text is the report; it starts with
-- "SAFETY CHECK FINISHED".

do $$
declare
  v_donor    public.users;
  v_rec      public.users;
  v_admin    public.users;
  c_donor    text;
  c_rec      text;
  c_admin    text;
  c_iss      text := 'https://securetoken.google.com/salusalo';
  n_all      bigint;
  n_donors   bigint;
  n_recips   bigint;
  v_msg      text := rpad('Safety check message for everyone. ', 500, 'x');
  v_id_all   uuid;
  v_sent     integer;
  v_j        jsonb;
  v_one      jsonb;
  v_audit    jsonb;
  n          bigint;
  n2         bigint;
  v_bad      text;
  t_title    text[];
  t_msg      text[];
  t_aud      text[];
  t_want     text[];
  v_label    text;
  v_ok       boolean;
  v_note     text;
  a_label    text[] := '{}';
  a_ok       boolean[] := '{}';
  a_note     text[] := '{}';
  v_report   text := '';
  v_pass     integer := 0;
  v_fail     integer := 0;
  v_skip     integer := 0;
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

  -- The audiences, counted here independently of the functions under test.
  select count(*),
         count(*) filter (where exists (select 1 from public.food_posts p where p.donor_id = u.id)),
         count(*) filter (where u.role <> 'ADMIN' and not exists (select 1 from public.food_posts p where p.donor_id = u.id))
    into n_all, n_donors, n_recips
  from public.users u;

  -- 1
  v_label := 'Sanity: the admin reads the audience sizes, and they match the accounts';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_audience_sizes();
    perform set_config('role', 'postgres', true);
    v_ok := (v_j ->> 'ALL')::bigint = n_all and (v_j ->> 'DONORS')::bigint = n_donors
        and (v_j ->> 'RECIPIENTS')::bigint = n_recips;
    v_note := format('sizes %s; expected everyone %s, donors %s, recipients %s', v_j, n_all, n_donors, n_recips);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'An empty audience is refused (everyone who never posted made an admin for a moment)';
  v_ok := false;
  begin
    update public.users u set role = 'ADMIN'
    where not exists (select 1 from public.food_posts p where p.donor_id = u.id);
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    perform public.admin_send_announcement('Safety check: nobody', 'Nobody should get this.', 'RECIPIENTS');
    perform set_config('role', 'postgres', true);
    raise exception 'it sent to an empty audience';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'No one is in this audience yet.';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'Everyone: each account gets exactly one alert with the title trimmed of spaces, tabs and line breaks and the whole 500-character text, even with alert switches off';
  v_ok := false;
  begin
    insert into public.notification_preferences (user_id, message_alerts, reservation_alerts)
    values (v_rec.id, false, false)
    on conflict (user_id) do update set message_alerts = false, reservation_alerts = false;
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_send_announcement(E'\n  Safety check: everyone \t', v_msg, 'ALL');
    perform set_config('role', 'postgres', true);
    v_id_all := (v_j ->> 'id')::uuid;
    select count(*), count(distinct user_id) into n, n2 from public.notifications
    where type = 'ANNOUNCEMENT' and title = 'Safety check: everyone' and body = v_msg;
    v_ok := (v_j ->> 'reach')::bigint = n_all and n = n_all and n2 = n_all
        and exists (select 1 from public.notifications where user_id = v_rec.id
                    and type = 'ANNOUNCEMENT' and title = 'Safety check: everyone');
    v_note := format('reach %s, alerts %s for %s accounts, expected %s', v_j ->> 'reach', n, n2, n_all);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'Donors: exactly the accounts that have posted get it';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_send_announcement('Safety check: donors', 'For donors.', 'DONORS');
    perform set_config('role', 'postgres', true);
    select count(*) into n from (
      (select user_id from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: donors')
      except (select donor_id from public.food_posts)) x;
    select count(*) into n2 from (
      (select donor_id from public.food_posts)
      except (select user_id from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: donors')) x;
    v_ok := n = 0 and n2 = 0 and (v_j ->> 'reach')::bigint = n_donors;
    v_note := format('reach %s (expected %s), extra %s, missing %s', v_j ->> 'reach', n_donors, n, n2);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'Recipients: exactly the accounts that never posted and are not admins get it';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_send_announcement('Safety check: recipients', 'For recipients.', 'RECIPIENTS');
    perform set_config('role', 'postgres', true);
    select count(*) into n from (
      (select user_id from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: recipients')
      except (select u.id from public.users u where u.role <> 'ADMIN'
              and not exists (select 1 from public.food_posts p where p.donor_id = u.id))) x;
    select count(*) into n2 from (
      (select u.id from public.users u where u.role <> 'ADMIN'
       and not exists (select 1 from public.food_posts p where p.donor_id = u.id))
      except (select user_id from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: recipients')) x;
    v_ok := n = 0 and n2 = 0 and (v_j ->> 'reach')::bigint = n_recips;
    v_note := format('reach %s (expected %s), extra %s, missing %s', v_j ->> 'reach', n_recips, n, n2);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    -- A project where every account has posted has no recipients: then refusing is right.
    v_ok := n_recips = 0 and sqlerrm = 'No one is in this audience yet.';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'Wrong input is refused with the dashboard''s texts';
  v_ok := false; v_bad := '';
  t_title := array[null, repeat('t', 81), 'Safety check', 'Safety check', 'Safety check', E'\t \n', 'Safety check'];
  t_msg   := array['x', 'x', '   ', repeat('m', 501), 'x', 'x', E'\n\r\n\t'];
  t_aud   := array['ALL', 'ALL', 'ALL', 'ALL', 'ADMINS', 'ALL', 'ALL'];
  t_want  := array['Add a title.', 'The title can be at most 80 characters.', 'Write the message.',
                   'The message can be at most 500 characters.', 'Choose who gets it.',
                   'Add a title.', 'Write the message.'];
  perform set_config('request.jwt.claims', c_admin, true);
  perform set_config('role', 'authenticated', true);
  for i in 1 .. array_length(t_want, 1) loop
    begin
      perform public.admin_send_announcement(t_title[i], t_msg[i], t_aud[i]);
      v_bad := v_bad || i || ' sent; ';
    exception when others then
      if sqlerrm <> t_want[i] then v_bad := v_bad || i || ': ' || sqlerrm || '; '; end if;
    end;
  end loop;
  perform set_config('role', 'postgres', true);
  v_ok := v_bad = '';
  v_note := case when v_ok then format('all %s refused (tabs and line breaks count as blank)', array_length(t_want, 1)) else v_bad end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'A fourth announcement within 10 minutes is refused';
  v_ok := false;
  begin
    select count(*) into v_sent from public.announcements
    where admin_id = v_admin.id and created_at > now() - interval '10 minutes';
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    while v_sent < 3 loop
      perform public.admin_send_announcement('Safety check: filler', 'Filler.', 'ALL');
      v_sent := v_sent + 1;
    end loop;
    perform public.admin_send_announcement('Safety check: one too many', 'Too many.', 'ALL');
    perform set_config('role', 'postgres', true);
    v_note := 'the fourth was sent';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'You can send up to 3 announcements every 10 minutes. Please wait a few minutes.';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'The history, the announcement by ID and the audit log show what was sent';
  v_ok := null; v_note := 'check 3 did not pass';
  if a_ok[3] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_announcements(1, 50);
      v_one := public.admin_announcement(v_id_all);
      v_audit := public.admin_audit(null, null, 'ANNOUNCEMENT_SENT', 'ANY', 1, 50);
      n := (select count(*) from jsonb_array_elements(v_j -> 'rows') r where r ->> 'id' = v_id_all::text);
      n2 := (select count(*) from jsonb_array_elements(v_audit -> 'rows') r
             where r ->> 'targetId' = v_id_all::text and r ->> 'targetType' = 'ANNOUNCEMENT'
               and r ->> 'targetName' = 'Safety check: everyone'
               and r ->> 'details' = format('To %s %s (everyone).', n_all, case when n_all = 1 then 'person' else 'people' end));
      v_ok := n = 1 and n2 = 1
          and v_one ->> 'title' = 'Safety check: everyone' and v_one ->> 'message' = v_msg
          and v_one ->> 'audience' = 'ALL' and (v_one ->> 'reach')::bigint = n_all
          and v_one -> 'admin' ->> 'name' = v_admin.name
          and public.admin_announcement(gen_random_uuid()) is null;
      perform set_config('role', 'postgres', true);
      v_note := format('in history %s, audit rows %s, history total %s', n, n2, v_j ->> 'total');
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'The recipient''s app reads its own announcement (and only its own)';
  v_ok := null; v_note := 'check 3 did not pass';
  if a_ok[3] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: everyone';
      perform set_config('role', 'postgres', true);
      v_ok := n = 1;
      v_note := format('sees %s', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'A signed-in account that is not an admin is refused by all four functions';
  v_bad := '';
  perform set_config('request.jwt.claims', c_donor, true);
  perform set_config('role', 'authenticated', true);
  begin perform public.admin_audience_sizes(); v_bad := v_bad || 'sizes answered; ';
  exception when others then if sqlerrm <> 'Admin access required' then v_bad := v_bad || 'sizes: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_send_announcement('Safety check: donor', 'x', 'ALL'); v_bad := v_bad || 'send answered; ';
  exception when others then if sqlerrm <> 'Admin access required' then v_bad := v_bad || 'send: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_announcements(1, 10); v_bad := v_bad || 'history answered; ';
  exception when others then if sqlerrm <> 'Admin access required' then v_bad := v_bad || 'history: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_announcement(gen_random_uuid()); v_bad := v_bad || 'one answered; ';
  exception when others then if sqlerrm <> 'Admin access required' then v_bad := v_bad || 'one: ' || sqlerrm || '; '; end if; end;
  perform set_config('role', 'postgres', true);
  v_ok := v_bad = '';
  v_note := case when v_ok then 'all four refused' else v_bad end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 11
  v_label := 'No one signed out (anon) can run any of them';
  v_bad := '';
  perform set_config('request.jwt.claims', '{}', true);
  perform set_config('role', 'anon', true);
  begin perform public.admin_audience_sizes(); v_bad := v_bad || 'sizes answered; ';
  exception when others then if sqlerrm not like 'permission denied for function admin_%' then v_bad := v_bad || 'sizes: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_send_announcement('Safety check: anon', 'x', 'ALL'); v_bad := v_bad || 'send answered; ';
  exception when others then if sqlerrm not like 'permission denied for function admin_%' then v_bad := v_bad || 'send: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_announcements(1, 10); v_bad := v_bad || 'history answered; ';
  exception when others then if sqlerrm not like 'permission denied for function admin_%' then v_bad := v_bad || 'history: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_announcement(gen_random_uuid()); v_bad := v_bad || 'one answered; ';
  exception when others then if sqlerrm not like 'permission denied for function admin_%' then v_bad := v_bad || 'one: ' || sqlerrm || '; '; end if; end;
  perform set_config('role', 'postgres', true);
  v_ok := v_bad = '';
  v_note := case when v_ok then 'all four refused' else v_bad end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 12
  v_label := 'Clients have no privileges on the announcements table (RLS on)';
  v_ok := false;
  begin
    v_ok := (select relrowsecurity from pg_class where oid = 'public.announcements'::regclass)
        and not has_table_privilege('authenticated', 'public.announcements', 'SELECT, INSERT, UPDATE, DELETE')
        and not has_table_privilege('anon', 'public.announcements', 'SELECT, INSERT, UPDATE, DELETE');
    v_note := case when v_ok then 'as expected' else 'a client has a privilege, or RLS is off' end;
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 13
  v_label := 'Privileges: authenticated may run the four functions, anon may not, and no client may run the JSON helper';
  v_ok := to_regprocedure('public.admin_announcement_json(public.announcements)') is not null
      and not has_function_privilege('authenticated', 'public.admin_announcement_json(public.announcements)', 'execute')
      and not has_function_privilege('anon', 'public.admin_announcement_json(public.announcements)', 'execute')
      and (select count(*) from unnest(array['public.admin_audience_sizes()', 'public.admin_send_announcement(text,text,text)',
                                             'public.admin_announcements(integer,integer)', 'public.admin_announcement(uuid)']) f(sig)
           where to_regprocedure(f.sig) is not null
             and has_function_privilege('authenticated', f.sig, 'execute')
             and not has_function_privilege('anon', f.sig, 'execute')) = 4;
  v_note := case when v_ok then 'as expected' else 'a privilege is wrong or a function is missing' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 14
  v_label := 'Alerts still accept every earlier type and ANNOUNCEMENT, and refuse an unknown one';
  v_ok := false;
  begin
    insert into public.notifications (user_id, type, title, body)
    select v_rec.id, t, 'Safety check: type', 'x'
    from unnest(array['MESSAGE', 'RESERVATION_CREATED', 'RESERVATION_CANCELLED', 'RESERVATION_CLAIMED',
                      'RESERVATION_REMOVED', 'ANNOUNCEMENT']) t;
    begin
      insert into public.notifications (user_id, type, title, body) values (v_rec.id, 'SOMETHING_NEW', 'Safety check: type', 'x');
      v_note := 'SOMETHING_NEW was accepted';
    exception when check_violation then
      v_ok := true;
      v_note := 'six accepted, SOMETHING_NEW refused';
    end;
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s. Admin: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'), coalesce(v_admin.name, 'none'), v_report);
end $$;
