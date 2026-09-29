begin;

alter table public.food_posts
    add column if not exists max_per_receiver integer;

alter table public.food_posts
    add constraint food_posts_max_per_receiver_positive
    check (
        max_per_receiver is null
        or max_per_receiver > 0
    );

comment on column public.food_posts.max_per_receiver is
    'Maximum quantity per receiver across active reservations and completed pickups for this listing. NULL means no additional limit.';

commit;