-- Salu-Salo bug fix pass, group D: restoring a profile after the Firebase
-- account was re-created (audit #15).
-- Run after 20261006_bugfix_reservations.sql. It can run before or after the
-- new build is installed (without it, the build's "Restore profile" fails with
-- an error and nothing changes). Safe to run again; it adds one function and
-- changes no table or row.
--
-- The problem: users.email is unique. When a Firebase account is deleted and
-- created again with the same email, it gets a new Firebase UID, so the app
-- finds no profile for it and cannot create one either (the email is taken):
-- the person is locked out of their own listings, reservations and chats.
--
-- The fix is an explicit, confirmed re-link, never an automatic merge: the app
-- asks "Restore your profile?", and relink_my_profile() moves the earlier
-- profile to the caller's Firebase UID only when
--   * the caller's token is a valid SaluSalo Firebase token,
--   * the caller has no profile yet,
--   * Firebase itself says in the signed token that the caller's email is
--     verified (email_verified = true; the app never sends a value), and
--   * exactly one profile has that email (trimmed, case-insensitive).
-- A verified email proves the caller controls the address the profile was
-- made with; that address could already reset the old account's password.
-- Only firebase_uid changes; role, email, name and everything that points at
-- the profile (listings, reservations, chats, reviews) stay as they are.

create or replace function public.relink_my_profile()
returns public.users
language plpgsql
security definer
set search_path = public
as $$
declare
    v_sub text := auth.jwt() ->> 'sub';
    v_email text := lower(btrim(coalesce(auth.jwt() ->> 'email', '')));
    v_user public.users;
    v_count integer;
begin
    if not public.is_salusalo_firebase_jwt() or coalesce(btrim(v_sub), '') = '' then
        raise exception 'Not authenticated';
    end if;
    if public.current_salusalo_profile_id() is not null then
        raise exception 'This account already has a profile.';
    end if;
    if not coalesce(auth.jwt() -> 'email_verified' = 'true'::jsonb, false) or v_email = '' then
        raise exception 'Confirm your email address first.';
    end if;

    select count(*) into v_count from public.users u where lower(btrim(u.email)) = v_email;
    if v_count = 0 then
        raise exception 'No earlier profile uses this email address.';
    end if;
    if v_count > 1 then
        raise exception 'More than one profile uses this email address. Contact Salu-Salo for help.';
    end if;

    select * into v_user from public.users u where lower(btrim(u.email)) = v_email for update;
    update public.users u set firebase_uid = v_sub where u.id = v_user.id returning u.* into v_user;
    return v_user;
end; $$;

revoke all on function public.relink_my_profile() from public;
revoke all on function public.relink_my_profile() from anon;
grant execute on function public.relink_my_profile() to authenticated;
