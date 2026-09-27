-- Salu-Salo Phase 4 demo fixes: the caller's own response rate for the
-- Profile page. Run after 20260930_phase3_demo_fixes.sql. Safe to run again.
--
-- Adds one function only. No table, column, policy, table grant, trigger or
-- existing function changes. Clients still never write conversations,
-- messages or notifications. It can run before or after installing the new
-- app build; older builds never call it.
--
-- Response rate over the last 30 days:
--   received = the caller's conversations whose first message was sent by the
--              other participant within the last 30 days;
--   replied  = those of them in which the caller sent at least one message
--              after that first message.
-- Messages are ordered by created_at, with ties broken by id: the first
-- message is the earliest in that order, and "after" means later in it. The
-- app shows round(replied / received * 100), or no rate when received is 0.
--
-- SECURITY INVOKER: it reads only rows the caller may already select, so it
-- needs no extra rights, and the conversations and messages RLS still applies
-- on top of the explicit participant filter below. The caller comes only from
-- the token; the function takes no arguments.

create or replace function public.get_my_response_rate()
returns table (received integer, replied integer, period_days integer)
language plpgsql
stable
security invoker
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

    return query
    with first_messages as (
        select distinct on (m.conversation_id)
               m.conversation_id, m.id, m.sender_id, m.created_at
        from public.messages m
        join public.conversations c on c.id = m.conversation_id
        where c.recipient_id = v_profile or c.donor_id = v_profile
        order by m.conversation_id, m.created_at, m.id
    )
    select count(*)::integer,
           (count(*) filter (where exists (
               select 1 from public.messages r
               where r.conversation_id = f.conversation_id
                 and r.sender_id = v_profile
                 and (r.created_at, r.id) > (f.created_at, f.id))))::integer,
           30
    from first_messages f
    where f.sender_id <> v_profile
      and f.created_at >= now() - interval '30 days';
end; $$;

revoke all on function public.get_my_response_rate() from public;
revoke all on function public.get_my_response_rate() from anon;
grant execute on function public.get_my_response_rate() to authenticated;
