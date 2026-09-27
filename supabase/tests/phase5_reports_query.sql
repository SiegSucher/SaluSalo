-- Salu-Salo: every report, newest first, for the developer (until the Phase 6
-- admin screen). Read-only: run it in the Supabase SQL Editor (role
-- "postgres"). It changes nothing. It shows names and sign-in emails so the
-- accounts can be told apart; this is for the developer only, never the app.
--
-- target_type: LISTING (a listing was reported) or USER (a person).
-- source: where it was sent from: LISTING (a listing's details), DONOR_PAGE
-- (the Donor/Seller page) or CHAT (a chat header).
-- listing: the listing's name when it was reported (the listing itself may be
-- gone). Reasons: UNSAFE_FOOD, MISLEADING, SPAM_FAKE, OTHER (listings);
-- ABUSIVE, NO_SHOW, SCAM, SPAM, OTHER (people).

select r.created_at,
       r.status,
       r.target_type,
       r.source,
       rep.name  as reporter,
       rep.email as reporter_email,
       tgt.name  as reported_user,
       tgt.email as reported_email,
       coalesce(r.listing_name, p.name) as listing,
       r.reason,
       r.details,
       r.food_post_id,
       r.conversation_id,
       r.id as report_id
from public.reports r
join public.users rep on rep.id = r.reporter_id
join public.users tgt on tgt.id = r.reported_user_id
left join public.food_posts p on p.id = r.food_post_id
order by r.created_at desc;
