-- Additive migration for the Profile redesign. Apply once before using Save changes.
-- Uses the project's existing verified Firebase JWT predicate and owner lookup.
-- Run after 20261003_phase5_maps.sql and BEFORE installing the build with the new
-- Profile editor: its Save changes calls update_my_profile_details(). Safe to run again.
-- Older builds are unaffected: they ignore the new column and still rename through
-- update_my_profile_name(), which this file does not change. The bio is visible only
-- to its owner, because users rows are readable only by their owner (RLS).
begin;
alter table public.users add column if not exists bio text;
-- The same 160-character limit as the function, also for a profile's first insert.
do $$
begin
    if not exists (select 1 from pg_constraint
                   where conrelid = 'public.users'::regclass
                     and conname = 'users_bio_length_check') then
        alter table public.users add constraint users_bio_length_check
            check (bio is null or char_length(bio) <= 160);
    end if;
end $$;
create or replace function public.update_my_profile_details(p_name text, p_bio text)
returns public.users
language plpgsql security definer set search_path = public
as $$
declare
    v_sub text;
    v_name text := btrim(coalesce(p_name, ''), E' \t\r\n');
    v_bio text := btrim(coalesce(p_bio, ''), E' \t\r\n');
    v_user public.users;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_sub := auth.jwt() ->> 'sub';
    if coalesce(v_sub, '') = '' then raise exception 'Not authenticated'; end if;
    if char_length(v_name) = 0 or char_length(v_name) > 120 then
        raise exception 'Your name must contain 1 to 120 characters.';
    end if;
    if char_length(v_bio) > 160 then raise exception 'Your bio must be 160 characters or fewer.'; end if;
    update public.users set name = v_name, bio = nullif(v_bio, '')
    where firebase_uid = v_sub returning * into v_user;
    if not found then raise exception 'Profile not found'; end if;
    return v_user;
end; $$;
revoke all on function public.update_my_profile_details(text, text) from public, anon;
grant execute on function public.update_my_profile_details(text, text) to authenticated;
commit;
