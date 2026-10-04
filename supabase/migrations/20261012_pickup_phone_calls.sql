-- Run AFTER 20261011_reservation_limits_and_expiry.sql, before the phone-call app build.
-- Additive and rerunnable. Existing accounts default to NOT sharing their number.
-- No changes to profile RLS, reservations, stock, messaging RPCs or public profiles.
begin;

alter table public.users add column if not exists share_phone_for_pickup boolean not null default false;

create or replace function public.get_my_phone_contact()
returns table(phone_number text, share_phone_for_pickup boolean)
language plpgsql stable security definer set search_path = ''
as $$
declare v_me uuid;
begin
    if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
    v_me := public.current_salusalo_profile_id();
    if v_me is null then raise exception 'Profile not found'; end if;
    return query select u.phone_number, u.share_phone_for_pickup from public.users u where u.id = v_me;
end;
$$;

create or replace function public.update_my_phone_contact(p_phone_number text, p_share_phone_for_pickup boolean)
returns table(phone_number text, share_phone_for_pickup boolean)
language plpgsql security definer set search_path = ''
as $$
declare
    v_me uuid;
    v_number text := btrim(coalesce(p_phone_number, ''));
begin
    if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
    v_me := public.current_salusalo_profile_id();
    if v_me is null then raise exception 'Profile not found'; end if;
    if p_share_phone_for_pickup is null then raise exception 'Choose whether to share your phone number.'; end if;
    if v_number <> '' then
        if length(v_number) > 64 or v_number !~ '^[+0-9 ()-]+$' then
            raise exception 'Enter a valid phone number.';
        end if;
        v_number := regexp_replace(v_number, '[ ()-]', '', 'g');
        if v_number ~ '^09[0-9]{9}$' then v_number := '+63' || substr(v_number, 2); end if;
        if v_number !~ '^\+[1-9][0-9]{7,14}$' then
            raise exception 'Use a Philippine mobile number starting with 09, or + and the country code.';
        end if;
    end if;
    if v_number = '' and p_share_phone_for_pickup then
        raise exception 'Enter a phone number before enabling sharing.';
    end if;
    update public.users u set
        phone_number = nullif(v_number, ''),
        share_phone_for_pickup = p_share_phone_for_pickup
    where u.id = v_me;
    return query select u.phone_number, u.share_phone_for_pickup from public.users u where u.id = v_me;
end;
$$;

create or replace function public.get_pickup_call_contact(p_conversation_id uuid)
returns table(status text, phone_number text)
language plpgsql stable security definer set search_path = ''
as $$
declare
    v_me uuid;
    v_chat public.conversations;
    v_other uuid;
    v_phone text;
    v_shared boolean;
begin
    if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
    v_me := public.current_salusalo_profile_id();
    if v_me is null then raise exception 'Profile not found'; end if;
    select c.* into v_chat from public.conversations c
    where c.id = p_conversation_id and v_me in (c.donor_id, c.recipient_id);
    if not found then raise exception 'Conversation not available.'; end if;

    -- A matching ACTIVE reservation in THIS listing and THIS conversation pair.
    -- Check the actual deadline: do not wait for the scheduled expiry cleanup.
    if not exists (
        select 1 from public.reservations r
        join public.food_posts p on p.id = r.food_post_id
        where r.food_post_id = v_chat.food_post_id
          and r.recipient_id = v_chat.recipient_id
          and p.donor_id = v_chat.donor_id
          and r.status = 'RESERVED'
          and (r.pickup_deadline is null or r.pickup_deadline > statement_timestamp())
          and p.status in ('AVAILABLE', 'RESERVED')
          and p.expiration_date > statement_timestamp()
          and (p.pickup_end is null or p.pickup_end > statement_timestamp())
    ) then
        return query select 'NO_ACTIVE_RESERVATION'::text, null::text;
        return;
    end if;

    v_other := case when v_me = v_chat.donor_id then v_chat.recipient_id else v_chat.donor_id end;
    select u.phone_number, u.share_phone_for_pickup into v_phone, v_shared
    from public.users u where u.id = v_other;
    -- Do not expose pre-existing malformed numbers or numbers that were not opted in.
    if not coalesce(v_shared, false) or v_phone is null or v_phone !~ '^\+[1-9][0-9]{7,14}$' then
        return query select 'NOT_SHARED'::text, null::text;
        return;
    end if;
    return query select 'AVAILABLE'::text, v_phone;
end;
$$;

revoke all on function public.get_my_phone_contact() from public, anon;
revoke all on function public.update_my_phone_contact(text, boolean) from public, anon;
revoke all on function public.get_pickup_call_contact(uuid) from public, anon;
grant execute on function public.get_my_phone_contact() to authenticated;
grant execute on function public.update_my_phone_contact(text, boolean) to authenticated;
grant execute on function public.get_pickup_call_contact(uuid) to authenticated;

notify pgrst, 'reload schema';
commit;
