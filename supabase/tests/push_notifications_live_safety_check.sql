-- Salu-Salo push notifications: safety check for the live database
-- (PROJECT_STATUS.md §35).
--
-- Run it in the Supabase dashboard after migrations/20261016_push_notifications.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres".
--
-- It pretends to be the two newest accounts (A and B), registers made-up push
-- tokens and adds one test alert for A. The "Sanity" check must succeed: it
-- proves that the pretend sign-in works. A check that cannot run reports SKIP.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did, so no token or alert is kept and no phone gets a push (pg_net
-- sends only requests whose transaction committed). The error text is the
-- report; it starts with "SAFETY CHECK FINISHED". Any other error means the
-- check itself did not run.

do $$
declare
  v_a        public.users;
  v_b        public.users;
  c_iss      text := 'https://securetoken.google.com/salusalo';
  c_a        text;
  c_b        text;
  c_nobody   text;
  v_tok      text := 'safety-check:token-' || replace(gen_random_uuid()::text, '-', '');
  v_owner    uuid;
  v_bad      text;
  v_alert    uuid;
  v_max      bigint;
  v_vault    boolean;
  n          bigint;
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
  select * into v_a from public.users order by created_at desc limit 1;
  select * into v_b from public.users where id is distinct from v_a.id order by created_at desc limit 1;
  c_a := json_build_object('sub', v_a.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_b := json_build_object('sub', v_b.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_nobody := json_build_object('sub', 'safety-check-no-profile', 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;

  -- 1
  v_label := 'Sanity: account A registers a push token';
  v_ok := null; v_note := 'needs an account';
  if v_a.id is not null then
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.register_my_push_token(v_tok);
      perform set_config('role', 'postgres', true);
      select user_id into v_owner from public.push_tokens where token = v_tok;
      v_ok := v_owner is not distinct from v_a.id;
      v_note := case when v_ok then 'saved for A' else 'not saved for A' end;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'The same token registered by account B moves to B (one phone, another account)';
  v_ok := null; v_note := 'needs two accounts';
  if v_a.id is not null and v_b.id is not null then
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      perform public.register_my_push_token(v_tok);
      perform set_config('role', 'postgres', true);
      select user_id into v_owner from public.push_tokens where token = v_tok;
      select count(*) into n from public.push_tokens where token = v_tok;
      v_ok := v_owner is not distinct from v_b.id and n = 1;
      v_note := format('owner is %s, %s row(s)', case when v_owner = v_b.id then 'B' else 'not B' end, n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'An account keeps at most 5 tokens';
  v_ok := null; v_note := 'needs an account';
  if v_a.id is not null then
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      for i in 1 .. 7 loop
        perform public.register_my_push_token(v_tok || '-' || i);
      end loop;
      perform set_config('role', 'postgres', true);
      select count(*) into n from public.push_tokens where user_id = v_a.id;
      v_ok := n = 5;
      v_note := format('%s token(s) kept', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'Refused without a Salu-Salo sign-in';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', '{}', true);
    perform set_config('role', 'authenticated', true);
    perform public.register_my_push_token(v_tok || '-anon');
    perform set_config('role', 'postgres', true);
    v_note := 'it was allowed';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Not authenticated';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'Refused for a sign-in without a profile';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_nobody, true);
    perform set_config('role', 'authenticated', true);
    perform public.register_my_push_token(v_tok || '-nobody');
    perform set_config('role', 'postgres', true);
    v_note := 'it was allowed';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Profile not found';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'An empty or malformed token is refused';
  v_ok := null; v_note := 'needs an account';
  if v_a.id is not null then
    v_ok := true; v_note := 'both refused';
    foreach v_bad in array array['', 'bad token!'] loop
      begin
        perform set_config('request.jwt.claims', c_a, true);
        perform set_config('role', 'authenticated', true);
        perform public.register_my_push_token(v_bad);
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := format('"%s" was allowed', v_bad);
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'Invalid push token.' then v_ok := false; v_note := sqlerrm; end if;
      end;
    end loop;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'push_tokens: no privileges for anon or authenticated';
  v_ok := not has_table_privilege('authenticated', 'public.push_tokens', 'select,insert,update,delete')
    and not has_table_privilege('anon', 'public.push_tokens', 'select,insert,update,delete');
  v_note := case when v_ok then 'as expected' else 'a client has a privilege' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'register_my_push_token for signed-in callers only; the trigger function for no client';
  v_ok := has_function_privilege('authenticated', 'public.register_my_push_token(text)', 'execute')
    and not has_function_privilege('anon', 'public.register_my_push_token(text)', 'execute')
    and not has_function_privilege('authenticated', 'public.push_new_notification()', 'execute')
    and not has_function_privilege('anon', 'public.push_new_notification()', 'execute');
  v_note := case when v_ok then 'as expected' else 'wrong privileges' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'The trigger notifications_push is on notifications and enabled';
  select count(*) into n from pg_trigger
  where tgrelid = 'public.notifications'::regclass and tgname = 'notifications_push'
    and tgenabled <> 'D' and not tgisinternal;
  v_ok := n = 1;
  v_note := case when v_ok then 'found' else 'missing or disabled' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'A new alert still saves; one push request is queued only when both Vault secrets exist';
  v_ok := null; v_note := 'needs an account';
  if v_a.id is not null then
    begin
      v_vault := exists (select 1 from vault.decrypted_secrets where name = 'push_function_url')
        and exists (select 1 from vault.decrypted_secrets where name = 'push_webhook_secret');
      -- Only rows added after this point: pg_net's worker may delete older ones meanwhile.
      select coalesce(max(id), 0) into v_max from net.http_request_queue;
      insert into public.notifications(user_id, type, title, body)
      values (v_a.id, 'ANNOUNCEMENT', 'Safety check', 'Safety check alert')
      returning id into v_alert;
      select count(*) into n from net.http_request_queue where id > v_max;
      v_ok := v_alert is not null and n = case when v_vault then 1 else 0 end;
      v_note := format('Vault secrets %s, %s request(s) queued (undone below)',
        case when v_vault then 'present' else 'not added yet' end, n);
    exception when others then
      v_ok := false; v_note := sqlerrm;
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nA: %s. B: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_a.name, 'none'), coalesce(v_b.name, 'none'), v_report);
end $$;
