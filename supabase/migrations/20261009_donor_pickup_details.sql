-- Apply after 20261008_reservation_expiry.sql.
-- Separate RPC keeps the old donor query available to older callers.
begin;

create or replace function public.get_donor_reservations_with_pickup()
returns table (
    id uuid,
    food_post_id uuid,
    recipient_id uuid,
    quantity integer,
    status text,
    created_at timestamptz,
    updated_at timestamptz,
    recipient_name text,
    unit_price numeric,
    cancel_reason text,
    pickup_minutes integer,
    pickup_deadline timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
    select r.id, r.food_post_id, r.recipient_id, r.quantity, r.status,
           r.created_at, r.updated_at, u.name, r.unit_price,
           r.cancel_reason, r.pickup_minutes, r.pickup_deadline
    from public.reservations r
    join public.food_posts p on p.id = r.food_post_id
    join public.users u on u.id = r.recipient_id
    where public.is_salusalo_firebase_jwt()
      and p.donor_id = public.current_salusalo_profile_id()
    order by r.created_at desc, r.id;
$$;

revoke all on function public.get_donor_reservations_with_pickup() from public, anon;
grant execute on function public.get_donor_reservations_with_pickup() to authenticated;

notify pgrst, 'reload schema';
commit;
