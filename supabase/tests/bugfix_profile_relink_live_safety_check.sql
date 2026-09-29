-- Salu-Salo bug fix pass, group D: safety check for relink_my_profile().
--
-- Run it in the Supabase dashboard after migrations/20261007_bugfix_profile_relink.sql:
-- SQL Editor -> New query -> paste this whole file -> Run. Leave the role on
-- "postgres".
--
-- It takes the newest account and pretends to be a re-created Firebase
-- account with the same email (a made-up new Firebase UID), the way the app's
-- Firebase token does. It checks that the earlier profile moves to the new
-- account only with a verified email, and that nothing else changes.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did, so nothing is saved. The error text is the report; it starts
-- with "SAFETY CHECK FINISHED". Any other error means the check itself did
-- not run.

do $$
declare
  v_old        public.users;
  v_row        public.users;
  c_iss        text := 'https://securetoken.google.com/salusalo';
  v_new_sub    text := 'safety-check-new-uid-' || md5(random()::text);
  v_id         uuid;
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
  select * into v_old from public.users order by created_at desc limit 1;

  -- Refusals. Each case: the token's claims, and the start of the expected error.
  for i in 1 .. 6 loop
    v_label := case i
      when 1 then 'A re-created account whose email is not verified is refused'
      when 2 then 'A token without the email_verified claim is refused'
      when 3 then 'A verified account whose email no profile uses is refused'
      when 4 then 'An account that already has a profile is refused'
      when 5 then 'A token from another Firebase project is refused'
      else 'Without a login, relink_my_profile is refused' end;
    v_ok := null; v_note := 'no account found';
    if v_old.id is not null then
      begin
        perform set_config('request.jwt.claims', case i
          when 1 then json_build_object('sub', v_new_sub, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo',
                                        'email', v_old.email, 'email_verified', false)::text
          when 2 then json_build_object('sub', v_new_sub, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo',
                                        'email', v_old.email)::text
          when 3 then json_build_object('sub', v_new_sub, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo',
                                        'email', 'nobody-' || md5(random()::text) || '@example.invalid', 'email_verified', true)::text
          when 4 then json_build_object('sub', v_old.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo',
                                        'email', v_old.email, 'email_verified', true)::text
          when 5 then json_build_object('sub', v_new_sub, 'role', 'authenticated',
                                        'iss', 'https://securetoken.google.com/another-project', 'aud', 'another-project',
                                        'email', v_old.email, 'email_verified', true)::text
          else '' end, true);
        perform set_config('role', case when i = 6 then 'anon' else 'authenticated' end, true);
        perform public.relink_my_profile();
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := 'the profile was moved';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := case i
          when 1 then sqlerrm = 'Confirm your email address first.'
          when 2 then sqlerrm = 'Confirm your email address first.'
          when 3 then sqlerrm = 'No earlier profile uses this email address.'
          when 4 then sqlerrm = 'This account already has a profile.'
          when 5 then sqlerrm = 'Not authenticated'
          else sqlstate = '42501' or sqlerrm like '%Not authenticated%' end;
      end;
      select * into v_row from public.users where id = v_old.id;
      if v_row.firebase_uid is distinct from v_old.firebase_uid then
        v_ok := false; v_note := v_note || ' (but the profile moved)';
      end if;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  v_label := 'A client still cannot change firebase_uid directly';
  v_ok := null; v_note := 'no account found';
  if v_old.id is not null then
    begin
      perform set_config('request.jwt.claims', json_build_object('sub', v_old.firebase_uid, 'role', 'authenticated',
        'iss', c_iss, 'aud', 'salusalo')::text, true);
      perform set_config('role', 'authenticated', true);
      update public.users set firebase_uid = v_new_sub where id = v_old.id;
      perform set_config('role', 'postgres', true);
      select * into v_row from public.users where id = v_old.id;
      v_ok := v_row.firebase_uid = v_old.firebase_uid;
      v_note := case when v_ok then 'no row changed' else 'it changed' end;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := sqlstate = '42501';
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'With a verified email (other case, spaces) the profile moves to the new account, and nothing else changes';
  v_ok := null; v_note := 'no account found';
  if v_old.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', json_build_object('sub', v_new_sub, 'role', 'authenticated',
        'iss', c_iss, 'aud', 'salusalo', 'email', ' ' || upper(v_old.email) || ' ', 'email_verified', true)::text, true);
      perform set_config('role', 'authenticated', true);
      select * into v_row from public.relink_my_profile();
      v_id := public.current_salusalo_profile_id();
      perform set_config('role', 'postgres', true);
      v_ok := v_row.id = v_old.id and v_row.firebase_uid = v_new_sub and v_id = v_old.id
        and v_row.email = v_old.email and v_row.role = v_old.role and v_row.name = v_old.name;
      v_note := format('profile %s now signed in by the new account: %s; role, email and name kept: %s',
        case when v_row.id = v_old.id then 'moved' else 'not moved' end,
        case when v_id = v_old.id then 'yes' else 'no' end,
        case when v_row.email = v_old.email and v_row.role = v_old.role and v_row.name = v_old.name then 'yes' else 'no' end);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'After the move, the old Firebase account no longer reaches the profile';
  v_ok := null; v_note := 'the move above did not happen';
  if v_row.firebase_uid = v_new_sub then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', json_build_object('sub', v_old.firebase_uid, 'role', 'authenticated',
        'iss', c_iss, 'aud', 'salusalo')::text, true);
      perform set_config('role', 'authenticated', true);
      v_id := public.current_salusalo_profile_id();
      perform set_config('role', 'postgres', true);
      v_ok := v_id is null;
      v_note := case when v_ok then 'no profile' else 'it still has the profile' end;
    exception when others then
      perform set_config('role', 'postgres', true);
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nAccount: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_old.name, 'none'), v_report);
end $$;
