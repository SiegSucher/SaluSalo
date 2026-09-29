-- Run after 20261006_enforce_receiver_limit.sql.
-- Adds pickup duration and deadline storage.
-- Automatic expiry is NOT implemented by this migration.

begin;

alter table public.reservations
    add column if not exists pickup_minutes integer,
    add column if not exists pickup_deadline timestamptz;

alter table public.reservations
    add constraint reservations_pickup_duration_valid
    check (
        (
            pickup_minutes is null
            and pickup_deadline is null
        )
        or
        (
            pickup_minutes is not null
            and pickup_minutes in (15, 30, 60, 120)
            and pickup_deadline is not null
        )
    );

create or replace function public.create_reservation_with_pickup(
    p_food_post_id uuid,
    p_quantity integer,
    p_pickup_minutes integer
)
returns public.reservations
language plpgsql
security definer
set search_path = public
as $$
declare
    v_post public.food_posts;
    v_reservation public.reservations;
    v_now timestamptz;
    v_deadline timestamptz;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;

    if p_pickup_minutes is null
       or p_pickup_minutes not in (15, 30, 60, 120) then
        raise exception 'Choose 15, 30, 60, or 120 minutes.';
    end if;

    -- Keep the listing stable while checking its pickup window
    -- and creating the reservation.
    select *
    into v_post
    from public.food_posts
    where id = p_food_post_id
    for update;

    if not found then
        raise exception 'Food post is no longer available';
    end if;

    -- Calculate from server time after acquiring the listing lock.
    v_now := clock_timestamp();
    v_deadline := v_now
        + make_interval(mins => p_pickup_minutes);

    if v_post.expiration_date <= v_now then
        raise exception 'Food post is no longer available';
    end if;

    if v_deadline > v_post.expiration_date then
        raise exception
            'Choose a shorter pickup duration. This food expires before that deadline.';
    end if;

    if v_post.pickup_end is not null
       and v_deadline > v_post.pickup_end then
        raise exception
            'Choose a shorter pickup duration. The pickup window ends before that deadline.';
    end if;

    if v_post.pickup_start is not null
       and v_deadline <= v_post.pickup_start then
        raise exception
            'This duration ends before pickup is available. Choose a longer duration or reserve later.';
    end if;

    -- Reuse the existing authentication, ownership, availability,
    -- cumulative receiver-limit, price and stock handling.
    select *
    into v_reservation
    from public.create_reservation(
        p_food_post_id,
        p_quantity
    );

    update public.reservations
    set pickup_minutes = p_pickup_minutes,
        pickup_deadline = v_deadline
    where id = v_reservation.id
    returning * into v_reservation;

    return v_reservation;
end;
$$;

revoke all on function
    public.create_reservation_with_pickup(uuid, integer, integer)
    from public;

revoke all on function
    public.create_reservation_with_pickup(uuid, integer, integer)
    from anon;

grant execute on function
    public.create_reservation_with_pickup(uuid, integer, integer)
    to authenticated;

commit;