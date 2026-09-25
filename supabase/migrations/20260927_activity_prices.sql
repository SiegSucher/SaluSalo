-- Salu-Salo: listing prices and the price each reservation was made at.
-- Run after the Phase 1-4 migrations (including the Phase 3 donor-names patch).
-- Safe to run again.
--
-- food_posts.price: the price per unit set by the donor. 0 means a free donation,
-- so every existing listing becomes a free donation.
-- reservations.unit_price: the listing's price at the moment of reserving. It is
-- set only by the database (trigger below), never by the app, and does not
-- change when the donor later edits the listing's price.

alter table public.food_posts add column if not exists price numeric(10,2) not null default 0;
alter table public.reservations add column if not exists unit_price numeric(10,2) not null default 0;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'food_posts_price_check') then
        alter table public.food_posts add constraint food_posts_price_check
            check (price >= 0 and price <= 99999.99);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'reservations_unit_price_check') then
        alter table public.reservations add constraint reservations_unit_price_check
            check (unit_price >= 0 and unit_price <= 99999.99);
    end if;
end $$;

-- Reservations are inserted only by create_reservation(), which already holds a
-- FOR UPDATE lock on the listing row, so the price read here is the one the
-- quantity check used. Any value supplied for unit_price is always overwritten.
create or replace function public.set_reservation_unit_price()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    new.unit_price := coalesce(
        (select p.price from public.food_posts p where p.id = new.food_post_id),
        0
    );
    return new;
end; $$;

revoke all on function public.set_reservation_unit_price() from public;
revoke all on function public.set_reservation_unit_price() from anon;
revoke all on function public.set_reservation_unit_price() from authenticated;

drop trigger if exists reservations_set_unit_price on public.reservations;
create trigger reservations_set_unit_price
before insert on public.reservations
for each row execute function public.set_reservation_unit_price();

-- Same as the Phase 3 donor-names version, plus unit_price. The return type
-- changes, so the function must be dropped first. Recipients' names only.
drop function if exists public.get_donor_reservations();
create function public.get_donor_reservations()
returns table (
    id uuid,
    food_post_id uuid,
    recipient_id uuid,
    quantity integer,
    status text,
    created_at timestamptz,
    updated_at timestamptz,
    recipient_name text,
    unit_price numeric
)
language sql
stable
security definer
set search_path = public
as 'select r.id, r.food_post_id, r.recipient_id, r.quantity, r.status,
           r.created_at, r.updated_at, u.name, r.unit_price
    from public.reservations r
    join public.food_posts p on p.id = r.food_post_id
    join public.users u on u.id = r.recipient_id
    where p.donor_id = public.current_salusalo_profile_id()
    order by r.created_at desc';

revoke all on function public.get_donor_reservations() from public;
revoke all on function public.get_donor_reservations() from anon;
grant execute on function public.get_donor_reservations() to authenticated;
