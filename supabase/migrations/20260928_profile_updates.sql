-- Salu-Salo: let a signed-in user change their own display name and profile
-- photo path. Run after 20260927_activity_prices.sql. Safe to run again.
--
-- users keeps SELECT/INSERT only for clients; there is still no UPDATE grant or
-- policy. These SECURITY DEFINER functions are the only way to change a users
-- row from the app. They find the caller's row from the verified token
-- (auth.jwt() ->> 'sub'), never from an argument, and never change role,
-- email or firebase_uid.

create or replace function public.update_my_profile_name(p_name text)
returns public.users
language plpgsql
security definer
set search_path = public
as $$
declare
    v_sub text;
    v_name text;
    v_user public.users;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_sub := auth.jwt() ->> 'sub';
    if coalesce(v_sub, '') = '' then
        raise exception 'Not authenticated';
    end if;

    v_name := btrim(coalesce(p_name, ''), E' \t\r\n');
    if char_length(v_name) = 0 then
        raise exception 'Enter your name.';
    end if;
    if char_length(v_name) > 120 then
        raise exception 'Your name must be 120 characters or fewer.';
    end if;

    update public.users set name = v_name
    where firebase_uid = v_sub
    returning * into v_user;
    if not found then
        raise exception 'Profile not found';
    end if;
    return v_user;
end; $$;

create or replace function public.set_my_profile_image(p_path text)
returns public.users
language plpgsql
security definer
set search_path = public
as $$
declare
    v_sub text;
    v_user public.users;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_sub := auth.jwt() ->> 'sub';
    if coalesce(v_sub, '') = '' then
        raise exception 'Not authenticated';
    end if;

    -- The photo must be in the caller's own profile-images folder and exist.
    if p_path is null
       or char_length(p_path) > 512
       or left(p_path, char_length(v_sub) + 1) <> v_sub || '/'
       or position('..' in p_path) > 0 then
        raise exception 'The photo must be in your own folder';
    end if;
    if not exists (
        select 1 from storage.objects
        where bucket_id = 'profile-images' and name = p_path
    ) then
        raise exception 'The photo was not uploaded';
    end if;

    update public.users set profile_image_url = p_path
    where firebase_uid = v_sub
    returning * into v_user;
    if not found then
        raise exception 'Profile not found';
    end if;
    return v_user;
end; $$;

revoke all on function public.update_my_profile_name(text) from public;
revoke all on function public.update_my_profile_name(text) from anon;
grant execute on function public.update_my_profile_name(text) to authenticated;
revoke all on function public.set_my_profile_image(text) from public;
revoke all on function public.set_my_profile_image(text) from anon;
grant execute on function public.set_my_profile_image(text) to authenticated;
