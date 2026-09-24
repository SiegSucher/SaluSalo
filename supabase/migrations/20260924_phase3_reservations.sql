-- Salu-Salo Phase 3: atomic reservations and claims.
-- Run after the Phase 1 and Phase 2 migrations.

create table if not exists public.reservations (
    id uuid primary key default gen_random_uuid(),
    food_post_id uuid not null references public.food_posts(id) on delete cascade,
    recipient_id uuid not null references public.users(id) on delete restrict,
    quantity integer not null check (quantity > 0),
    status text not null default 'RESERVED'
        check (status in ('RESERVED', 'CLAIMED', 'CANCELLED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Phase 2 required quantity > 0. A fully reserved listing must safely reach 0;
-- the reservation RPC remains the only operation that can reduce it.
alter table public.food_posts drop constraint if exists food_posts_quantity_check;
alter table public.food_posts add constraint food_posts_quantity_check check (quantity >= 0);
create index if not exists reservations_recipient_idx on public.reservations(recipient_id);
create index if not exists reservations_post_idx on public.reservations(food_post_id);
drop trigger if exists reservations_set_updated_at on public.reservations;
create trigger reservations_set_updated_at before update on public.reservations
for each row execute function public.set_updated_at();

alter table public.reservations enable row level security;
revoke all on public.reservations from anon;
revoke all on public.reservations from authenticated;
grant select on public.reservations to authenticated;

-- A recipient sees their reservations; the post donor sees reservations on their posts.
drop policy if exists "Recipients and donors may view legitimate reservations" on public.reservations;
create policy "Recipients and donors may view legitimate reservations"
on public.reservations for select to authenticated using (
  (select public.is_salusalo_firebase_jwt()) and (
    recipient_id = (select public.current_salusalo_profile_id()) or
    exists (select 1 from public.food_posts p where p.id = food_post_id
            and p.donor_id = (select public.current_salusalo_profile_id()))
  )
);

-- SECURITY DEFINER avoids circular RLS evaluation: food_posts policies must not
-- query reservations through a policy that itself queries food_posts.
create or replace function public.is_reservation_recipient(p_food_post_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as 'select exists (select 1 from public.reservations where food_post_id = p_food_post_id and recipient_id = public.current_salusalo_profile_id())';
revoke all on function public.is_reservation_recipient(uuid) from public;
grant execute on function public.is_reservation_recipient(uuid) to authenticated;
-- Let a recipient read a listing they have legitimately reserved, including one
-- that has since become fully reserved.
drop policy if exists "Authenticated users may view available or own food posts" on public.food_posts;
create policy "Authenticated users may view available or own food posts"
on public.food_posts for select to authenticated using (
  (select public.is_salusalo_firebase_jwt()) and (
    (status = 'AVAILABLE' and expiration_date > now()) or
    donor_id = (select public.current_salusalo_profile_id()) or
    (select public.is_reservation_recipient(id))
  )
);

-- Direct INSERT/UPDATE/DELETE remains denied. These SECURITY DEFINER functions
-- validate Firebase identity and ownership themselves, then perform one atomic
-- transaction while the food-post row is locked.
create or replace function public.create_reservation(p_food_post_id uuid, p_quantity integer)
returns public.reservations
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_post public.food_posts; v_reservation public.reservations;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  if p_quantity is null or p_quantity <= 0 then raise exception 'Choose a quantity greater than zero'; end if;
  select * into v_post from public.food_posts where id = p_food_post_id for update;
  if not found or v_post.expiration_date <= now() then raise exception 'Food post is no longer available'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  if v_post.donor_id = v_profile then raise exception 'You cannot reserve your own food post'; end if;
  if v_post.status <> 'AVAILABLE' or v_post.quantity < p_quantity then
    raise exception 'Not enough food available. Only % remains.', greatest(v_post.quantity, 0);
  end if;
  insert into public.reservations(food_post_id, recipient_id, quantity)
  values (p_food_post_id, v_profile, p_quantity) returning * into v_reservation;
  update public.food_posts set quantity = quantity - p_quantity,
    status = case when quantity - p_quantity = 0 then 'RESERVED' else 'AVAILABLE' end
  where id = p_food_post_id;
  return v_reservation;
end; $$;

create or replace function public.cancel_reservation(p_reservation_id uuid)
returns public.reservations
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_reservation public.reservations;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  select * into v_reservation from public.reservations where id = p_reservation_id for update;
  if not found then raise exception 'Reservation not found'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_reservation.recipient_id <> v_profile then raise exception 'You can cancel only your own reservation'; end if;
  if v_reservation.status = 'CLAIMED' then raise exception 'A claimed reservation cannot be cancelled'; end if;
  if v_reservation.status = 'CANCELLED' then raise exception 'This reservation was already cancelled'; end if;
  update public.reservations set status = 'CANCELLED' where id = p_reservation_id returning * into v_reservation;
  update public.food_posts set quantity = quantity + v_reservation.quantity, status = 'AVAILABLE'
  where id = v_reservation.food_post_id;
  return v_reservation;
end; $$;

create or replace function public.claim_reservation(p_reservation_id uuid)
returns public.reservations
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_reservation public.reservations; v_donor uuid;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  select * into v_reservation
  from public.reservations where id = p_reservation_id for update;
  if not found then raise exception 'Reservation not found'; end if;
  select donor_id into v_donor from public.food_posts
  where id = v_reservation.food_post_id for update;
  if not found then raise exception 'Food post not found'; end if;
  if v_donor <> public.current_salusalo_profile_id() then raise exception 'Only the food donor can mark this reservation as claimed'; end if;
  if v_reservation.status <> 'RESERVED' then raise exception 'Only an active reservation can be claimed'; end if;
  update public.reservations set status = 'CLAIMED' where id = p_reservation_id returning * into v_reservation;
  return v_reservation;
end; $$;

revoke all on function public.create_reservation(uuid, integer) from public;
revoke all on function public.cancel_reservation(uuid) from public;
revoke all on function public.claim_reservation(uuid) from public;
grant execute on function public.create_reservation(uuid, integer) to authenticated;
grant execute on function public.cancel_reservation(uuid) to authenticated;
grant execute on function public.claim_reservation(uuid) to authenticated;







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
