-- Apply after 20261009. Read-only snapshot; creation still validates under lock.
begin;
create or replace function public.get_my_reservation_allowance(p_food_post_id uuid)
returns table (
    food_post_id uuid, available_quantity integer, max_per_receiver integer,
    used_quantity bigint, remaining_allowance integer, can_reserve integer,
    pickup_start timestamptz, pickup_end timestamptz,
    expiration_date timestamptz, server_now timestamptz
)
language plpgsql stable security definer set search_path = public as $$
declare v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then raise exception 'Profile not found'; end if;
    if not public.caller_can_view_food_post(p_food_post_id) then
        raise exception 'This listing is no longer available to you.';
    end if;
    return query
    with usage as (
        select coalesce(sum(r.quantity), 0)::bigint as used
        from public.reservations r
        where r.food_post_id = p_food_post_id and r.recipient_id = v_profile
          and r.status in ('RESERVED', 'CLAIMED')
    ), limits as (
        select p.*, usage.used,
            case when p.max_per_receiver is null then null::integer
                 else greatest(p.max_per_receiver::bigint - usage.used, 0)::integer end as remaining
        from public.food_posts p cross join usage where p.id = p_food_post_id
    )
    select l.id, l.quantity, l.max_per_receiver, l.used, l.remaining,
        case when l.donor_id = v_profile or l.status <> 'AVAILABLE'
                   or l.expiration_date <= statement_timestamp()
                   or (l.pickup_end is not null and l.pickup_end <= statement_timestamp())
             then 0 else least(l.quantity, coalesce(l.remaining, l.quantity)) end,
        l.pickup_start, l.pickup_end, l.expiration_date, statement_timestamp()
    from limits l;
end;
$$;
revoke all on function public.get_my_reservation_allowance(uuid) from public, anon;
grant execute on function public.get_my_reservation_allowance(uuid) to authenticated;
notify pgrst, 'reload schema';
commit;
