-- Salu-Salo Phase 4: safety check for the live database.
--
-- Run it in the Supabase dashboard: SQL Editor -> New query -> paste this whole
-- file -> Run. Leave the role on "postgres".
--
-- It picks the most recent chat that has messages, that chat's donor and
-- recipient, and the newest other account (the "outsider"). It pretends to be
-- each of them, the way the app's Firebase token does, and tries things the app
-- must never allow. The "Sanity" checks must succeed: they prove that the
-- pretend sign-in works, so the refusals are real.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did, so nothing is saved and no phone gets a live update. The error
-- text is the report; it starts with "SAFETY CHECK FINISHED". Any other error
-- means the check itself did not run.

do $$
declare
  v_conv       public.conversations;
  v_donor      public.users;
  v_recipient  public.users;
  v_out        public.users;
  v_food_name  text;
  v_res_id     uuid;
  v_alert_id   uuid;
  v_pref_user  uuid;
  c_donor      text;
  c_recipient  text;
  c_out        text;
  c_foreign    text;
  v_table      text;
  v_refused    integer;
  v_id         uuid;
  n            bigint;
  m            bigint;
  t1           bigint;
  t2           bigint;
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
  -- Who and what to test with.
  select * into v_conv from public.conversations
  where last_message_at is not null
  order by last_message_at desc
  limit 1;
  if v_conv.id is null then
    raise exception 'SAFETY CHECK NOT RUN: no chat with messages was found. Send one chat message between two accounts, then run this again.';
  end if;
  select * into v_donor from public.users where id = v_conv.donor_id;
  select * into v_recipient from public.users where id = v_conv.recipient_id;
  select * into v_out from public.users
  where id <> v_conv.donor_id and id <> v_conv.recipient_id
  order by created_at desc
  limit 1;
  select name into v_food_name from public.food_posts where id = v_conv.food_post_id;
  if v_out.id is not null then
    select r.id into v_res_id
    from public.reservations r
    join public.food_posts p on p.id = r.food_post_id
    where r.recipient_id <> v_out.id and p.donor_id <> v_out.id
    order by r.created_at desc
    limit 1;
    select id into v_alert_id from public.notifications
    where user_id <> v_out.id
    order by created_at desc
    limit 1;
    select user_id into v_pref_user from public.notification_preferences
    where user_id <> v_out.id
    limit 1;
  end if;

  -- Token claims as the app sends them (Firebase project "salusalo").
  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo')::text;
  c_recipient := json_build_object('sub', v_recipient.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo')::text;
  c_out := json_build_object('sub', v_out.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/salusalo', 'aud', 'salusalo')::text;
  -- The recipient's user ID, but in a token issued for another Firebase project.
  c_foreign := json_build_object('sub', v_recipient.firebase_uid, 'role', 'authenticated',
    'iss', 'https://securetoken.google.com/another-project', 'aud', 'another-project')::text;

  -- ---------------------------------------------------------------------------
  -- Sanity checks: the pretend sign-in works.
  -- ---------------------------------------------------------------------------

  v_label := 'Sanity: the recipient can read their own chat';
  v_ok := false; v_note := null; v_id := null; n := null; m := null;
  begin
    perform set_config('request.jwt.claims', c_recipient, true);
    perform set_config('role', 'authenticated', true);
    v_id := public.current_salusalo_profile_id();
    select count(*) into n from public.conversations where id = v_conv.id;
    select count(*) into m from public.messages where conversation_id = v_conv.id;
    perform set_config('role', 'postgres', true);
    v_ok := coalesce(v_id = v_recipient.id and n = 1 and m > 0, false);
    v_note := format('signed in: %s, chats seen: %s, messages seen: %s',
      case when v_id is null then 'no' else 'yes' end, n, m);
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the donor sees the chat through get_my_conversations';
  v_ok := false; v_note := null; v_id := null; n := null;
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    v_id := public.current_salusalo_profile_id();
    select count(*) into n from public.get_my_conversations(v_conv.id);
    perform set_config('role', 'postgres', true);
    v_ok := coalesce(v_id = v_donor.id and n = 1, false);
    v_note := format('signed in: %s, chats returned: %s',
      case when v_id is null then 'no' else 'yes' end, n);
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Sanity: the outsider''s pretend sign-in works';
  v_ok := null; v_note := 'no third account found'; v_id := null;
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

  -- ---------------------------------------------------------------------------
  -- The outsider (not in the chat).
  -- ---------------------------------------------------------------------------

  v_label := 'The outsider cannot read the chat or its messages';
  v_ok := null; v_note := 'no third account found'; n := null; m := null;
  if v_out.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.conversations where id = v_conv.id;
      select count(*) into m from public.messages where conversation_id = v_conv.id;
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(n = 0 and m = 0, false);
      v_note := format('saw %s chat(s) and %s message(s)', n, m);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider gets nothing from get_my_conversations for that chat';
  v_ok := null; v_note := 'no third account found'; n := null;
  if v_out.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.get_my_conversations(v_conv.id);
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(n = 0, false);
      v_note := format('%s chat(s) returned', n);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot send a message in the chat';
  v_ok := null; v_note := 'no third account found';
  if v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.send_message(v_conv.id, 'Safety check: message from an outsider');
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the message was accepted';
    exception when others then
      v_ok := sqlerrm like '%not part of this conversation%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot mark the chat as read';
  v_ok := null; v_note := 'no third account found';
  if v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.mark_conversation_read(v_conv.id);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the chat was marked read';
    exception when others then
      v_ok := sqlerrm like '%not part of this conversation%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot open a chat from someone else''s reservation';
  v_ok := null;
  v_note := case when v_out.id is null then 'no third account found' else 'no reservation found' end;
  if v_out.id is not null and v_res_id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      perform public.open_reservation_conversation(v_res_id);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the chat was opened';
    exception when others then
      v_ok := sqlerrm like '%not part of this reservation%';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot see other people''s alerts or alert settings';
  v_ok := null; v_note := 'no third account found'; n := null; m := null;
  if v_out.id is not null then
    v_ok := false;
    select count(*) into t1 from public.notifications where user_id <> v_out.id;
    select count(*) into t2 from public.notification_preferences where user_id <> v_out.id;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.notifications where user_id <> v_out.id;
      select count(*) into m from public.notification_preferences where user_id <> v_out.id;
      perform set_config('role', 'postgres', true);
      v_ok := coalesce(n = 0 and m = 0, false);
      v_note := format('saw %s of %s alerts and %s of %s settings rows', n, t1, m, t2);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot mark someone else''s alert as read';
  v_ok := null;
  v_note := case when v_out.id is null then 'no third account found' else 'no alert found' end;
  n := null; m := null;
  if v_out.id is not null and v_alert_id is not null then
    v_ok := false;
    -- Make the alert unread for this test; like everything else, undone at the end.
    update public.notifications set read_at = null where id = v_alert_id;
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      select public.mark_notifications_read(array[v_alert_id]) into n;
      perform set_config('role', 'postgres', true);
      select count(*) into m from public.notifications where id = v_alert_id and read_at is null;
      v_ok := coalesce(n = 0 and m = 1, false);
      v_note := format('alerts marked read: %s, the alert is still unread: %s',
        n, case when m = 1 then 'yes' else 'no' end);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot change someone else''s alert settings';
  v_ok := null;
  v_note := case when v_out.id is null then 'no third account found' else 'no one else has saved alert settings' end;
  n := null;
  if v_out.id is not null and v_pref_user is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      update public.notification_preferences set message_alerts = false where user_id = v_pref_user;
      get diagnostics n = row_count;
      perform set_config('role', 'postgres', true);
      v_ok := n = 0;
      v_note := format('%s row(s) changed', n);
    exception when others then
      v_ok := sqlstate = '42501';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The outsider cannot create alert settings for someone else';
  v_ok := null; v_note := 'no third account found';
  if v_out.id is not null then
    begin
      perform set_config('request.jwt.claims', c_out, true);
      perform set_config('role', 'authenticated', true);
      insert into public.notification_preferences(user_id, message_alerts) values (v_recipient.id, false);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'the row was accepted';
    exception when others then
      v_ok := sqlstate = '42501';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- A chat member trying to go around the app.
  -- ---------------------------------------------------------------------------

  v_label := 'A chat member cannot write a message directly (only through send_message)';
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    insert into public.messages(conversation_id, sender_id, body)
    values (v_conv.id, v_donor.id, 'Safety check: direct insert');
    perform set_config('role', 'postgres', true);
    v_ok := false; v_note := 'the message was accepted';
  exception when others then
    v_ok := sqlstate = '42501';
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A chat member cannot edit messages directly';
  n := null;
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    update public.messages set body = 'Safety check: edited' where conversation_id = v_conv.id;
    get diagnostics n = row_count;
    perform set_config('role', 'postgres', true);
    v_ok := n = 0;
    v_note := format('%s message(s) changed', n);
  exception when others then
    v_ok := sqlstate = '42501';
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Nobody can create alerts directly';
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    insert into public.notifications(user_id, type, title, body)
    values (v_recipient.id, 'MESSAGE', 'Safety check', 'Safety check: fake alert');
    perform set_config('role', 'postgres', true);
    v_ok := false; v_note := 'the alert was created';
  exception when others then
    v_ok := sqlstate = '42501';
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'The internal notify_user function cannot be called from the app';
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    perform public.notify_user(v_recipient.id, 'MESSAGE', 'Safety check', 'Safety check: fake alert');
    perform set_config('role', 'postgres', true);
    v_ok := false; v_note := 'the call worked and created an alert';
  exception when others then
    v_ok := sqlstate = '42501';
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A donor cannot start a chat about their own listing';
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    perform public.start_listing_conversation(v_conv.food_post_id);
    perform set_config('role', 'postgres', true);
    v_ok := false; v_note := 'the chat was started';
  exception when others then
    v_ok := sqlerrm like '%cannot message yourself%';
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'A chat member cannot read the other person''s profile row (email, phone)';
  v_ok := false; v_note := null; n := null;
  begin
    perform set_config('request.jwt.claims', c_recipient, true);
    perform set_config('role', 'authenticated', true);
    select count(*) into n from public.users where id = v_donor.id;
    perform set_config('role', 'postgres', true);
    v_ok := coalesce(n = 0, false);
    v_note := format('%s row(s) visible', n);
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Bad or missing login.
  -- ---------------------------------------------------------------------------

  v_label := 'A token from another Firebase project sees nothing and cannot send';
  v_ok := false; v_note := null; n := null; m := null;
  begin
    perform set_config('request.jwt.claims', c_foreign, true);
    perform set_config('role', 'authenticated', true);
    select count(*) into n from public.conversations;
    select count(*) into m from public.messages;
    begin
      perform public.send_message(v_conv.id, 'Safety check: token from another project');
      v_note := 'the message was accepted';
    exception when others then
      v_note := sqlerrm;
    end;
    perform set_config('role', 'postgres', true);
    v_ok := coalesce(n = 0 and m = 0 and v_note like '%Not authenticated%', false);
    v_note := format('saw %s chat(s) and %s message(s); sending: %s', n, m, v_note);
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login, the chat and alert tables cannot be read';
  v_ok := true; v_note := null; v_refused := 0;
  foreach v_table in array array['conversations', 'messages', 'notifications', 'notification_preferences'] loop
    begin
      perform set_config('request.jwt.claims', '', true);
      perform set_config('role', 'anon', true);
      execute format('select count(*) from public.%I', v_table) into n;
      perform set_config('role', 'postgres', true);
      if n > 0 then
        v_ok := false;
        v_note := concat_ws('; ', v_note, format('%s row(s) readable in %s', n, v_table));
      end if;
    exception
      when insufficient_privilege then
        v_refused := v_refused + 1;
      when others then
        v_ok := false;
        v_note := concat_ws('; ', v_note, v_table || ': ' || sqlerrm);
    end;
  end loop;
  if v_ok and v_refused = 4 then
    v_note := 'all 4 tables refused';
  elsif v_ok then
    v_note := format('%s of 4 tables refused, the rest returned no rows', v_refused);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Without a login, send_message is refused';
  begin
    perform set_config('request.jwt.claims', '', true);
    perform set_config('role', 'anon', true);
    perform public.send_message(v_conv.id, 'Safety check: no login');
    perform set_config('role', 'postgres', true);
    v_ok := false; v_note := 'the message was accepted';
  exception when others then
    v_ok := sqlstate = '42501' or sqlerrm like '%Not authenticated%';
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
    v_report := v_report || format(E'\n%s %s. %s: %s',
      case when a_ok[i] is null then 'SKIP' when a_ok[i] then 'PASS' else 'FAIL' end,
      i, a_label[i], coalesce(a_note[i], '-'));
  end loop;

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nChat checked: "%s" between %s (donor) and %s (recipient). Outsider: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_food_name, '?'), v_donor.name, v_recipient.name,
    coalesce(v_out.name, 'none'), v_report);
end $$;
