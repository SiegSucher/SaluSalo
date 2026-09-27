-- Salu-Salo Phase 5, part 1: ratings and reviews, reports on listings and
-- people, and the "Email verified" badge.
-- Run after 20261001_phase4_demo_fixes.sql and BEFORE installing the app build
-- that uses it: that build loads donor names through
-- get_listing_donor_profiles(), so without this file it shows "Donor name
-- unavailable" and its review, report and verification calls fail.
-- Safe to run again.
--
-- Older builds keep working after it: supabase-kt ignores the new users column,
-- they never call the new functions, and get_listing_donor_names() is unchanged.
--
-- No RLS is widened, and no Phase 3 or Phase 4 RPC, trigger or policy changes.
-- Clients get no privileges at all on the two new tables (not even SELECT):
-- reviews and reports change only through submit_review() and submit_report(),
-- and reviews are read only through the functions below. They return the
-- reviewer's current name, the stars, the comment and the dates, never an
-- email, Firebase UID, role, phone number, photo path or user ID.
--
-- Every new function callable by the app is SECURITY DEFINER with
-- search_path = public, checks is_salusalo_firebase_jwt() first ("Not
-- authenticated"), takes the caller only from current_salusalo_profile_id()
-- ("Profile not found" when null), and is executable by authenticated only.
-- The helper and the trigger function are executable by no client.

-- ---------------------------------------------------------------------------
-- 1. Email verified
-- ---------------------------------------------------------------------------

alter table public.users add column if not exists email_verified boolean not null default false;

-- A profile always starts unverified, whatever an insert sends. Only
-- sync_my_email_verified() changes it afterwards; users still has no UPDATE
-- grant or policy for clients, and the insert policy is unchanged.
-- This trigger function has no token check: it takes no input, returns no
-- data and only overwrites this one column of the row being inserted (the
-- insert policy already checks the token), and a check would also block
-- inserts made in the SQL Editor.
create or replace function public.users_start_unverified()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    new.email_verified := false;
    return new;
end; $$;

revoke all on function public.users_start_unverified() from public;
revoke all on function public.users_start_unverified() from anon;
revoke all on function public.users_start_unverified() from authenticated;

drop trigger if exists users_start_unverified on public.users;
create trigger users_start_unverified
before insert on public.users
for each row execute function public.users_start_unverified();

-- Copies the email_verified claim of the caller's verified Firebase token into
-- the caller's own row: true only when the claim is JSON true and the token's
-- email equals the profile's email (trimmed, ignoring case). Takes no value
-- from the app. Changes only email_verified (and updated_at, through the
-- existing trigger, only when the value changes). Returns the stored value.
create or replace function public.sync_my_email_verified()
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_user public.users;
    v_verified boolean;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    select * into v_user from public.users u where u.id = v_profile for update;

    v_verified := coalesce(auth.jwt() -> 'email_verified' = 'true'::jsonb, false)
        and lower(btrim(coalesce(auth.jwt() ->> 'email', ''))) = lower(btrim(v_user.email));

    if v_user.email_verified is distinct from v_verified then
        update public.users u set email_verified = v_verified where u.id = v_profile;
    end if;
    return v_verified;
end; $$;

revoke all on function public.sync_my_email_verified() from public;
revoke all on function public.sync_my_email_verified() from anon;
grant execute on function public.sync_my_email_verified() to authenticated;

-- ---------------------------------------------------------------------------
-- 2. Who may see a listing (internal helper)
-- ---------------------------------------------------------------------------

-- Exactly the food_posts SELECT policy ("Authenticated users may view available
-- or own food posts", 20260924_phase3_reservations.sql), which is also the rule
-- of get_listing_donor_names(): available and unexpired, the caller's own, or
-- one the caller reserved (also after it was fully reserved, expired or
-- removed). If that policy changes, change this function and
-- get_listing_donor_names() with it. Executable by no client; the functions
-- below call it after their own token and profile checks.
create or replace function public.caller_can_view_food_post(p_food_post_id uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    return exists (
        select 1 from public.food_posts p
        where p.id = p_food_post_id
          and ((p.status = 'AVAILABLE' and p.expiration_date > now())
               or p.donor_id = v_profile
               or public.is_reservation_recipient(p.id))
    );
end; $$;

revoke all on function public.caller_can_view_food_post(uuid) from public;
revoke all on function public.caller_can_view_food_post(uuid) from anon;
revoke all on function public.caller_can_view_food_post(uuid) from authenticated;

-- ---------------------------------------------------------------------------
-- 3. Reviews: the recipient of a claimed reservation rates the listing's donor
-- ---------------------------------------------------------------------------

create table if not exists public.reviews (
    id uuid primary key default gen_random_uuid(),
    -- One review per reservation; sending again changes it (no delete).
    reservation_id uuid not null unique references public.reservations(id) on delete cascade,
    reviewer_id uuid not null references public.users(id) on delete cascade,
    -- The listing's donor, taken by submit_review() from the reservation's listing.
    reviewee_id uuid not null references public.users(id) on delete cascade,
    rating integer not null check (rating between 1 and 5),
    comment text check (comment is null or char_length(comment) between 1 and 500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (reviewer_id <> reviewee_id)
);
create index if not exists reviews_reviewee_idx on public.reviews (reviewee_id, updated_at desc);
create index if not exists reviews_reviewer_idx on public.reviews (reviewer_id);

drop trigger if exists reviews_set_updated_at on public.reviews;
create trigger reviews_set_updated_at
before update on public.reviews
for each row execute function public.set_updated_at();

alter table public.reviews enable row level security;
revoke all on public.reviews from anon;
revoke all on public.reviews from authenticated;

-- Rates the donor or seller of a claimed reservation. The caller must be its
-- recipient; the person rated is the listing's donor_id. A claimed reservation
-- of a removed listing can still be rated. Creates the review, or changes the
-- existing one; "created" says which.
create or replace function public.submit_review(p_reservation_id uuid, p_rating integer, p_comment text)
returns table (
    reservation_id uuid,
    rating integer,
    comment text,
    created_at timestamptz,
    updated_at timestamptz,
    created boolean
)
language plpgsql
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
    v_reservation public.reservations;
    v_donor uuid;
    v_comment text := nullif(btrim(coalesce(p_comment, ''), E' \t\r\n'), '');
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    if p_rating is null or p_rating < 1 or p_rating > 5 then
        raise exception 'Choose from 1 to 5 stars.';
    end if;
    if char_length(coalesce(v_comment, '')) > 500 then
        raise exception 'Your comment must be 500 characters or fewer.';
    end if;

    select * into v_reservation from public.reservations r where r.id = p_reservation_id;
    if not found or v_reservation.recipient_id <> v_profile then
        raise exception 'You can rate only your own pickups.';
    end if;
    if v_reservation.status <> 'CLAIMED' then
        raise exception 'You can rate only after the pickup is marked as claimed.';
    end if;

    select p.donor_id into v_donor from public.food_posts p where p.id = v_reservation.food_post_id;
    if v_donor is null then
        raise exception 'You can rate only your own pickups.';
    end if;
    if v_donor = v_profile then
        raise exception 'You cannot review yourself.';
    end if;

    -- xmax is 0 only for a row this statement inserted.
    return query
    insert into public.reviews as rv (reservation_id, reviewer_id, reviewee_id, rating, comment)
    values (v_reservation.id, v_profile, v_donor, p_rating, v_comment)
    on conflict (reservation_id) do update
        set rating = excluded.rating, comment = excluded.comment
    returning rv.reservation_id, rv.rating, rv.comment, rv.created_at, rv.updated_at, (rv.xmax = 0);
end; $$;

-- The caller's own reviews (as reviewer), for "Change your review".
create or replace function public.get_my_given_reviews()
returns table (
    reservation_id uuid,
    rating integer,
    comment text,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    return query
    select r.reservation_id, r.rating, r.comment, r.created_at, r.updated_at
    from public.reviews r
    where r.reviewer_id = v_profile
    order by r.updated_at desc, r.id;
end; $$;

-- For a list of listings, in one call: the donor's name, "Email verified", and
-- the donor's rating (average rounded to one decimal, null without reviews,
-- and the count). Only for listings the caller may see; nothing else about
-- the donor. The new app build uses it instead of get_listing_donor_names().
create or replace function public.get_listing_donor_profiles(p_food_post_ids uuid[])
returns table (
    food_post_id uuid,
    donor_name text,
    email_verified boolean,
    rating_average numeric,
    rating_count integer
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    return query
    select p.id, u.name, u.email_verified, s.rating_average, s.rating_count
    from public.food_posts p
    join public.users u on u.id = p.donor_id
    cross join lateral (
        select round(avg(r.rating), 1) as rating_average, count(*)::integer as rating_count
        from public.reviews r
        where r.reviewee_id = p.donor_id
    ) s
    where p.id = any(p_food_post_ids)
      and public.caller_can_view_food_post(p.id);
end; $$;

-- The latest reviews (newest written or changed first) about the donor of a
-- listing the caller may see: the reviewer's current name, the stars, the
-- comment and the dates. p_limit is kept between 1 and 50.
create or replace function public.get_listing_donor_reviews(p_food_post_id uuid, p_limit integer default 20)
returns table (
    reviewer_name text,
    rating integer,
    comment text,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    if not public.caller_can_view_food_post(p_food_post_id) then
        return;
    end if;

    return query
    select u.name, r.rating, r.comment, r.created_at, r.updated_at
    from public.food_posts p
    join public.reviews r on r.reviewee_id = p.donor_id
    join public.users u on u.id = r.reviewer_id
    where p.id = p_food_post_id
    order by r.updated_at desc, r.id
    limit least(greatest(coalesce(p_limit, 20), 1), 50);
end; $$;

-- The caller's own rating: the average rounded to one decimal (null without
-- reviews) and the count. Always one row.
create or replace function public.get_my_rating_summary()
returns table (rating_average numeric, rating_count integer)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    return query
    select round(avg(r.rating), 1), count(*)::integer
    from public.reviews r
    where r.reviewee_id = v_profile;
end; $$;

-- The latest reviews about the caller: the reviewer's current name, the stars,
-- the comment and the dates. p_limit is kept between 1 and 100.
create or replace function public.get_my_received_reviews(p_limit integer default 50)
returns table (
    reviewer_name text,
    rating integer,
    comment text,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    return query
    select u.name, r.rating, r.comment, r.created_at, r.updated_at
    from public.reviews r
    join public.users u on u.id = r.reviewer_id
    where r.reviewee_id = v_profile
    order by r.updated_at desc, r.id
    limit least(greatest(coalesce(p_limit, 50), 1), 100);
end; $$;

revoke all on function public.submit_review(uuid, integer, text) from public;
revoke all on function public.submit_review(uuid, integer, text) from anon;
grant execute on function public.submit_review(uuid, integer, text) to authenticated;

revoke all on function public.get_my_given_reviews() from public;
revoke all on function public.get_my_given_reviews() from anon;
grant execute on function public.get_my_given_reviews() to authenticated;

revoke all on function public.get_listing_donor_profiles(uuid[]) from public;
revoke all on function public.get_listing_donor_profiles(uuid[]) from anon;
grant execute on function public.get_listing_donor_profiles(uuid[]) to authenticated;

revoke all on function public.get_listing_donor_reviews(uuid, integer) from public;
revoke all on function public.get_listing_donor_reviews(uuid, integer) from anon;
grant execute on function public.get_listing_donor_reviews(uuid, integer) to authenticated;

revoke all on function public.get_my_rating_summary() from public;
revoke all on function public.get_my_rating_summary() from anon;
grant execute on function public.get_my_rating_summary() to authenticated;

revoke all on function public.get_my_received_reviews(integer) from public;
revoke all on function public.get_my_received_reviews(integer) from anon;
grant execute on function public.get_my_received_reviews(integer) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. Reports on listings and people
-- ---------------------------------------------------------------------------

create table if not exists public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.users(id) on delete cascade,
    -- Found by submit_report(): the listing's donor, or the chat's other participant.
    reported_user_id uuid not null references public.users(id) on delete cascade,
    -- LISTING: a listing; USER: a person.
    target_type text not null check (target_type in ('LISTING', 'USER')),
    -- Where it was sent from: a listing's details (LISTING), the Donor/Seller
    -- page (DONOR_PAGE) or a chat (CHAT).
    source text not null check (source in ('LISTING', 'DONOR_PAGE', 'CHAT')),
    -- Context. Set to NULL if a never-reserved listing (and its chats) is
    -- deleted later; listing_name keeps the name it had when it was reported.
    food_post_id uuid references public.food_posts(id) on delete set null,
    conversation_id uuid references public.conversations(id) on delete set null,
    listing_name text,
    reason text not null,
    details text check (details is null or char_length(details) between 1 and 1000),
    -- Only OPEN for now; the Phase 6 admin screen will add the others.
    status text not null default 'OPEN' check (status in ('OPEN')),
    created_at timestamptz not null default now(),
    check (reporter_id <> reported_user_id),
    -- Each reason belongs to one kind of target.
    check ((target_type = 'LISTING' and source = 'LISTING'
            and reason in ('UNSAFE_FOOD', 'MISLEADING', 'SPAM_FAKE', 'OTHER'))
        or (target_type = 'USER' and source in ('DONOR_PAGE', 'CHAT')
            and reason in ('ABUSIVE', 'NO_SHOW', 'SCAM', 'SPAM', 'OTHER'))),
    check (reason <> 'OTHER' or details is not null)
);
create index if not exists reports_reporter_idx on public.reports (reporter_id, created_at desc);
create index if not exists reports_reported_user_idx on public.reports (reported_user_id);
-- One open report per reporter and listing, and per reporter and person.
create unique index if not exists reports_open_listing_key
    on public.reports (reporter_id, food_post_id)
    where status = 'OPEN' and target_type = 'LISTING';
create unique index if not exists reports_open_user_key
    on public.reports (reporter_id, reported_user_id)
    where status = 'OPEN' and target_type = 'USER';

-- Clients can't read, change or delete reports. The developer reads them in the
-- SQL Editor with supabase/tests/phase5_reports_query.sql.
alter table public.reports enable row level security;
revoke all on public.reports from anon;
revoke all on public.reports from authenticated;

-- p_target: LISTING (p_target_id = a listing), DONOR (p_target_id = a listing;
-- reports its donor) or CHAT (p_target_id = a conversation; reports its other
-- participant). The reporter comes from the token; the reported person is found
-- here, never taken from the app. The reported person is not told.
create or replace function public.submit_report(p_target text, p_target_id uuid, p_reason text, p_details text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_post public.food_posts;
    v_conversation public.conversations;
    v_type text;
    v_source text;
    v_reported uuid;
    v_post_id uuid;
    v_conversation_id uuid;
    v_listing_name text;
    v_details text := nullif(btrim(coalesce(p_details, ''), E' \t\r\n'), '');
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    -- One report at a time per reporter, so neither the daily limit nor the
    -- duplicate check can be raced. The limit counts every report the caller
    -- sent in the last 24 hours.
    perform pg_advisory_xact_lock(hashtextextended('salusalo-report:' || v_profile::text, 0));
    if (select count(*) from public.reports r
        where r.reporter_id = v_profile
          and r.created_at > now() - interval '24 hours') >= 10 then
        raise exception 'You can send up to 10 reports a day. Please try again later.';
    end if;

    -- The reason must belong to the kind of target.
    if p_target = 'LISTING' then
        v_type := 'LISTING';
        v_source := 'LISTING';
        if p_reason is null or p_reason not in ('UNSAFE_FOOD', 'MISLEADING', 'SPAM_FAKE', 'OTHER') then
            raise exception 'Choose a reason from the list.';
        end if;
    elsif p_target in ('DONOR', 'CHAT') then
        v_type := 'USER';
        v_source := case when p_target = 'DONOR' then 'DONOR_PAGE' else 'CHAT' end;
        if p_reason is null or p_reason not in ('ABUSIVE', 'NO_SHOW', 'SCAM', 'SPAM', 'OTHER') then
            raise exception 'Choose a reason from the list.';
        end if;
    else
        raise exception 'Unknown report type.';
    end if;
    if p_reason = 'OTHER' and v_details is null then
        raise exception 'Describe the problem in a few words.';
    end if;
    if char_length(coalesce(v_details, '')) > 1000 then
        raise exception 'Details must be 1000 characters or fewer.';
    end if;

    if p_target in ('LISTING', 'DONOR') then
        -- A listing the caller may see (or reserved), and not their own.
        select * into v_post from public.food_posts p where p.id = p_target_id;
        if not found or not public.caller_can_view_food_post(p_target_id) then
            raise exception 'This listing is no longer available.';
        end if;
        if v_post.donor_id = v_profile then
            if p_target = 'LISTING' then
                raise exception 'You cannot report your own listing.';
            end if;
            raise exception 'You cannot report yourself.';
        end if;
        v_reported := v_post.donor_id;
        v_post_id := v_post.id;
        v_listing_name := v_post.name;
    else
        -- One of the caller's own chats; the other participant is reported.
        select * into v_conversation from public.conversations c where c.id = p_target_id;
        if not found
           or (v_conversation.recipient_id <> v_profile and v_conversation.donor_id <> v_profile) then
            raise exception 'You can report only people in your own chats.';
        end if;
        v_reported := case when v_conversation.donor_id = v_profile
                           then v_conversation.recipient_id
                           else v_conversation.donor_id end;
        v_conversation_id := v_conversation.id;
        v_post_id := v_conversation.food_post_id;
        select p.name into v_listing_name from public.food_posts p where p.id = v_post_id;
    end if;

    if v_reported = v_profile then
        raise exception 'You cannot report yourself.';
    end if;

    if exists (
        select 1 from public.reports r
        where r.reporter_id = v_profile
          and r.status = 'OPEN'
          and ((v_type = 'LISTING' and r.target_type = 'LISTING' and r.food_post_id = v_post_id)
            or (v_type = 'USER' and r.target_type = 'USER' and r.reported_user_id = v_reported))
    ) then
        raise exception 'You already reported this.';
    end if;

    begin
        insert into public.reports (reporter_id, reported_user_id, target_type, source,
                                    food_post_id, conversation_id, listing_name, reason, details)
        values (v_profile, v_reported, v_type, v_source,
                v_post_id, v_conversation_id, v_listing_name, p_reason, v_details);
    exception when unique_violation then
        -- A report of the same target that got in first.
        raise exception 'You already reported this.';
    end;
end; $$;

revoke all on function public.submit_report(text, uuid, text, text) from public;
revoke all on function public.submit_report(text, uuid, text, text) from anon;
grant execute on function public.submit_report(text, uuid, text, text) to authenticated;
