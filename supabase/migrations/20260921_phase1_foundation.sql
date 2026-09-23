-- Salu-Salo Phase 1: Firebase Auth + Supabase foundation.
-- Run this migration only after adding Firebase Auth as a Third-Party Auth
-- integration in the Supabase dashboard for Firebase project ID "salusalo".

create extension if not exists pgcrypto;

do $$
begin
    create type public.user_role as enum ('DONOR', 'RECIPIENT', 'ADMIN');
exception
    when duplicate_object then null;
end
$$;

create table if not exists public.users (
    id uuid primary key default gen_random_uuid(),
    firebase_uid text not null unique
        check (char_length(firebase_uid) between 1 and 128),
    name text not null check (char_length(trim(name)) between 1 and 120),
    email text not null unique
        check (char_length(trim(email)) between 3 and 320),
    role public.user_role not null default 'RECIPIENT',
    profile_image_url text,
    phone_number text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists users_set_updated_at on public.users;
create trigger users_set_updated_at
before update on public.users
for each row execute function public.set_updated_at();

-- Firebase tokens must be accepted only from the SaluSalo Firebase project and
-- must include the role claim required by Supabase Third-Party Auth.
create or replace function public.is_salusalo_firebase_jwt()
returns boolean
language sql
stable
set search_path = ''
as $$
    select coalesce(
        auth.jwt() ->> 'iss' = 'https://securetoken.google.com/salusalo'
        and auth.jwt() ->> 'aud' = 'salusalo'
        and auth.jwt() ->> 'role' = 'authenticated',
        false
    );
$$;

alter table public.users enable row level security;

revoke all on public.users from anon;
revoke all on public.users from authenticated;
grant select, insert on public.users to authenticated;

drop policy if exists "Users may read their own profile" on public.users;
create policy "Users may read their own profile"
on public.users
for select
to authenticated
using (
    firebase_uid = auth.jwt() ->> 'sub'
    and (select public.is_salusalo_firebase_jwt())
);

-- New users can create only their own profile. The role is fixed to the safe
-- default and the email must be the signed Firebase token's email claim.
drop policy if exists "Users may create their own recipient profile" on public.users;
create policy "Users may create their own recipient profile"
on public.users
for insert
to authenticated
with check (
    firebase_uid = auth.jwt() ->> 'sub'
    and email = coalesce(auth.jwt() ->> 'email', '')
    and role = 'RECIPIENT'
    and (select public.is_salusalo_firebase_jwt())
);

-- Keep both Phase 1 buckets private. Future upload UI must use the folder paths
-- defined in StorageRepository and a Firebase-authenticated Supabase client.
insert into storage.buckets (id, name, public)
values
    ('food-images', 'food-images', false),
    ('profile-images', 'profile-images', false)
on conflict (id) do update set public = excluded.public;

drop policy if exists "Users may access their own food images" on storage.objects;
create policy "Users may access their own food images"
on storage.objects
for all
to authenticated
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

drop policy if exists "Users may access their own profile image" on storage.objects;
create policy "Users may access their own profile image"
on storage.objects
for all
to authenticated
using (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and (select public.is_salusalo_firebase_jwt())
)
with check (
    bucket_id = 'profile-images'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and (select public.is_salusalo_firebase_jwt())
);