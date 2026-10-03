-- Salu-Salo reservation limits and pickup deadlines: read-only check
-- (PROJECT_STATUS.md §24). Changes nothing. Run in the SQL Editor after the
-- trial run (every row should say MISSING/false: nothing stayed) and after
-- applying migrations/20261011_reservation_limits_and_expiry.sql (every "ok"
-- should be true).

select 'food_posts.max_per_recipient exists' as check_name,
       exists (select 1 from information_schema.columns where table_schema = 'public'
               and table_name = 'food_posts' and column_name = 'max_per_recipient') as ok
union all
select 'reservations.pickup_deadline exists',
       exists (select 1 from information_schema.columns where table_schema = 'public'
               and table_name = 'reservations' and column_name = 'pickup_deadline')
union all
select 'cancel reasons include PICKUP_EXPIRED',
       coalesce((select pg_get_constraintdef(oid) like '%PICKUP_EXPIRED%' from pg_constraint
                 where conrelid = 'public.reservations'::regclass and conname = 'reservations_cancel_reason_check'), false)
union all
select 'create_reservation_with_hold: authenticated yes, anon no',
       to_regprocedure('public.create_reservation_with_hold(uuid,integer,numeric,integer)') is not null
       and has_function_privilege('authenticated', 'public.create_reservation_with_hold(uuid,integer,numeric,integer)', 'execute')
       and not has_function_privilege('anon', 'public.create_reservation_with_hold(uuid,integer,numeric,integer)', 'execute')
union all
select 'expire_overdue_reservations: no app may run it',
       to_regprocedure('public.expire_overdue_reservations()') is not null
       and not has_function_privilege('authenticated', 'public.expire_overdue_reservations()', 'execute')
       and not has_function_privilege('anon', 'public.expire_overdue_reservations()', 'execute')
union all
select 'create_reservation keeps the rate limit and pickup-end check, and has the limit',
       (select prosrc like '%salusalo-reserve:%' and prosrc like '%pickup_end%' and prosrc like '%max_per_recipient%'
        from pg_proc where oid = 'public.create_reservation(uuid,integer)'::regprocedure)
union all
select 'claim_reservation refuses after the deadline',
       (select prosrc like '%pickup_deadline%' from pg_proc where oid = 'public.claim_reservation(uuid)'::regprocedure)
union all
select 'get_donor_reservations returns pickup_deadline',
       (select pg_get_function_result(oid) like '%pickup_deadline%' from pg_proc
        where oid = 'public.get_donor_reservations()'::regprocedure)
union all
select 'pg_cron is installed',
       exists (select 1 from pg_extension where extname = 'pg_cron');

-- Only when the last row is true, run this too: the job and its latest runs.
-- select jobname, schedule, active from cron.job where jobname = 'salusalo-expire-reservations';
-- select status, return_message, start_time from cron.job_run_details d
-- join cron.job j on j.jobid = d.jobid where j.jobname = 'salusalo-expire-reservations'
-- order by start_time desc limit 5;
