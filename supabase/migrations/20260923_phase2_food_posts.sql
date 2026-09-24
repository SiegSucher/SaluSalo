-- Salu-Salo Phase 2: Supabase food listings.
-- Run this file in the Supabase SQL Editor after the Phase 1 migration.

create table if not exists public.food_posts (
    id uuid primary key,
    donor_id uuid not null references public.users(id) on delete cascade,
    name text not null check (char_length(trim(name)) between 1 and 160),
    description text,
    category text not null check (char_length(trim(category)) between 1 and 80),
    quantity integer not null check (quantity > 0),
    unit text not null check (char_length(trim(unit)) between 1 and 30),
    image_url text,
    pickup_location text not null check (char_length(trim(pickup_location)) between 1 and 300),
    expiration_date timestamptz not null,
    status text not null default 'AVAILABLE'
        check (status in ('AVAILABLE', 'RESERVED', 'CLAIMED', 'EXPIRED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists food_posts_available_expiry_idx
    on public.food_posts (status, expiration_date);
create index if not exists food_posts_donor_idx on public.food_posts (donor_id);

drop trigger if exists food_posts_set_updated_at on public.food_posts;
create trigger food_posts_set_updated_at
before update on public.food_posts
for each row execute function public.set_updated_at();

create or replace function public.current_salusalo_profile_id()
returns uuid
language sql
stable
security invoker
set search_path = ''
as $$
    select id
    from public.users
    where firebase_uid = auth.jwt() ->> 'sub'
    limit 1;
$$;

alter table public.food_posts enable row level security;
revoke all on public.food_posts from anon;
revoke all on public.food_posts from authenticated;
grant select, insert, update, delete on public.food_posts to authenticated;

drop policy if exists "Authenticated users may view available or own food posts" on public.food_posts;
create policy "Authenticated users may view available or own food posts"
on public.food_posts for select to authenticated
using (
    (select public.is_salusalo_firebase_jwt())
    and (
        (status = 'AVAILABLE' and expiration_date > now())
        or donor_id = (select public.current_salusalo_profile_id())
    )
);

drop policy if exists "Users may create their own food posts" on public.food_posts;
create policy "Users may create their own food posts"
on public.food_posts for insert to authenticated
with check (
    (select public.is_salusalo_firebase_jwt())
    and donor_id = (select public.current_salusalo_profile_id())
    and status = 'AVAILABLE'
);

drop policy if exists "Users may update their own food posts" on public.food_posts;
create policy "Users may update their own food posts"
on public.food_posts for update to authenticated
using (
    (select public.is_salusalo_firebase_jwt())
    and donor_id = (select public.current_salusalo_profile_id())
)
with check (
    (select public.is_salusalo_firebase_jwt())
    and donor_id = (select public.current_salusalo_profile_id())
);

drop policy if exists "Users may delete their own food posts" on public.food_posts;
create policy "Users may delete their own food posts"
on public.food_posts for delete to authenticated
using (
    (select public.is_salusalo_firebase_jwt())
    and donor_id = (select public.current_salusalo_profile_id())
);

-- food-images remains private. Every authenticated Salu-Salo user can read a
-- listing image and create a short-lived signed URL; only its uploader can
-- upload, change, or delete it under their Firebase UID folder.
drop policy if exists "Users may access their own food images" on storage.objects;
drop policy if exists "Authenticated users may read food listing images" on storage.objects;
drop policy if exists "Users may upload their own food images" on storage.objects;
drop policy if exists "Users may update their own food images" on storage.objects;
drop policy if exists "Users may delete their own food images" on storage.objects;

create policy "Authenticated users may read food listing images"
on storage.objects for select to authenticated
using (
    bucket_id = 'food-images'
    and (select public.is_salusalo_firebase_jwt())
);

create policy "Users may upload their own food images"
on storage.objects for insert to authenticated
with check (
    bucket_id = 'food-images'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and (select public.is_salusalo_firebase_jwt())
);

create policy "Users may update their own food images"
on storage.objects for update to authenticated
using (
    bucket_id = 'food-images'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and (select public.is_salusalo_firebase_jwt())
)
with check (
    bucket_id = 'food-images'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and (select public.is_salusalo_firebase_jwt())
);

create policy "Users may delete their own food images"
on storage.objects for delete to authenticated
using (
    bucket_id = 'food-images'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and (select public.is_salusalo_firebase_jwt())
);