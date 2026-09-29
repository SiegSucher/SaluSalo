-- Run after 20261005_listing_receiver_limit.sql.
-- Active reservations and completed pickups count toward the limit.
-- Cancelled reservations do not count.

begin;

create or replace function public.create_reservation(
    p_food_post_id uuid,
    p_quantity integer
)
returns public.reservations
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_post public.food_posts;
    v_reservation public.reservations;
    v_used bigint;
    v_remaining bigint;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;

    if p_quantity is null or p_quantity <= 0 then
        raise exception 'Choose a quantity greater than zero';
    end if;

    -- Serialize reservations for this listing so simultaneous requests
    -- cannot both use the same stock or receiver allowance.
    select *
    into v_post
    from public.food_posts
    where id = p_food_post_id
    for update;

    if not found then
        raise exception 'Food post is no longer available';
    end if;

    if v_post.expiration_date <= now() then
        raise exception 'Food post is no longer available';
    end if;

    if v_post.status = 'REMOVED' then
        raise exception 'The donor removed this listing.';
    end if;

    v_profile := public.current_salusalo_profile_id();

    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    if v_post.donor_id = v_profile then
        raise exception 'You cannot reserve your own food post';
    end if;

    if v_post.status <> 'AVAILABLE'
       or v_post.quantity < p_quantity then
        raise exception
            'Not enough food available. Only % remains.',
            greatest(v_post.quantity, 0);
    end if;

    if v_post.max_per_receiver is not null then
        select coalesce(sum(r.quantity), 0)
        into v_used
        from public.reservations r
        where r.food_post_id = p_food_post_id
          and r.recipient_id = v_profile
          and r.status in ('RESERVED', 'CLAIMED');

        v_remaining := greatest(
            v_post.max_per_receiver::bigint - v_used,
            0::bigint
        );

        if p_quantity > v_remaining then
            raise exception
                'This listing allows a maximum of % per receiver. You can reserve % more.',
                v_post.max_per_receiver,
                v_remaining;
        end if;
    end if;

    insert into public.reservations(
        food_post_id,
        recipient_id,
        quantity
    )
    values (
        p_food_post_id,
        v_profile,
        p_quantity
    )
    returning * into v_reservation;

    update public.food_posts
    set quantity = quantity - p_quantity,
        status = case
            when quantity - p_quantity = 0 then 'RESERVED'
            else 'AVAILABLE'
        end
    where id = p_food_post_id;

    return v_reservation;
end;
$$;

revoke all on function public.create_reservation(uuid, integer)
    from public;

revoke all on function public.create_reservation(uuid, integer)
    from anon;

grant execute on function public.create_reservation(uuid, integer)
    to authenticated;

commit;