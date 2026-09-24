-- Phase 3 patch: donor-only recipient names for reservation management.
-- Run this after 20260924_phase3_reservations.sql.
create or replace function public.get_donor_reservations()
returns table (
    id uuid,
    food_post_id uuid,
    recipient_id uuid,
    quantity integer,
    status text,
    created_at timestamptz,
    updated_at timestamptz,
    recipient_name text
)
language sql
stable
security definer
set search_path = public
as 'select r.id, r.food_post_id, r.recipient_id, r.quantity, r.status,
           r.created_at, r.updated_at, u.name
    from public.reservations r
    join public.food_posts p on p.id = r.food_post_id
    join public.users u on u.id = r.recipient_id
    where p.donor_id = public.current_salusalo_profile_id()
    order by r.created_at desc';

revoke all on function public.get_donor_reservations() from public;
grant execute on function public.get_donor_reservations() to authenticated;
