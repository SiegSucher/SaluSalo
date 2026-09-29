-- Salu-Salo bug fix pass, round 2: read-only check for function privileges (audit #32).
--
-- Run it in the Supabase SQL Editor after migrations/20261010_bugfix_revoke_anon_execute.sql.
-- It only reads the catalog and returns one row per check (PASS or FAIL).

with fns as (
    select p.oid, p.proname, p.oid::regprocedure::text as signature
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and not exists (select 1 from pg_depend d
                      where d.classid = 'pg_proc'::regclass and d.objid = p.oid and d.deptype = 'e')
),
-- Every function the app calls, plus the helpers its RLS policies and the
-- Storage read policy run as the signed-in user.
app as (
    select unnest(array[
        'create_reservation_at_price', 'create_reservation', 'cancel_reservation', 'claim_reservation',
        'release_reservation', 'get_donor_reservations', 'start_listing_conversation',
        'open_reservation_conversation', 'send_message', 'mark_conversation_read', 'mark_notifications_read',
        'get_my_conversations', 'get_my_response_rate', 'update_my_food_post', 'update_my_food_post_with_pin',
        'remove_my_food_post', 'get_my_saved_listing_names', 'get_listing_donor_names',
        'get_listing_donor_profiles', 'get_listing_donor_reviews', 'submit_review', 'get_my_given_reviews',
        'get_my_rating_summary', 'get_my_received_reviews', 'submit_report', 'sync_my_email_verified',
        'update_my_profile_name', 'set_my_profile_image', 'update_my_profile_details', 'relink_my_profile',
        'can_read_food_image', 'is_salusalo_firebase_jwt', 'current_salusalo_profile_id',
        'is_reservation_recipient']) as proname
)
select 1 as check_no,
       'No function in public can be run without a login (anon)' as check_name,
       case when count(*) = 0 then 'PASS' else 'FAIL' end as result,
       coalesce(string_agg(signature, ', '), 'none') as details
from fns
where has_function_privilege('anon', oid, 'EXECUTE')
union all
select 2,
       'Signed-in users (authenticated) can still run every function the app and its policies use',
       case when count(*) filter (where not ok) = 0 and count(*) >= 34 then 'PASS' else 'FAIL' end,
       format('%s functions checked; missing: %s', count(*),
              coalesce(string_agg(signature, ', ') filter (where not ok), 'none'))
from (select f.signature, has_function_privilege('authenticated', f.oid, 'EXECUTE') as ok
      from fns f join app a on a.proname = f.proname) s
union all
select 3,
       'Internal functions stay closed to signed-in users (notify_user, caller_can_view_food_post, food_posts_check_values)',
       case when count(*) filter (where has_function_privilege('authenticated', oid, 'EXECUTE')) = 0
                 and count(*) >= 3 then 'PASS' else 'FAIL' end,
       coalesce(string_agg(signature, ', ') filter (where has_function_privilege('authenticated', oid, 'EXECUTE')), 'none open')
from fns
where proname in ('notify_user', 'caller_can_view_food_post', 'food_posts_check_values')
order by 1;
