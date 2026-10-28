-- =====================================================================
-- Dzirent Android pilot: 006 chat dates
-- Run in Supabase > SQL Editor AFTER 005_fit_profile.sql.
-- Safe to re-run: columns and constraints are added only if missing, and the
-- function is replaced.
-- =====================================================================

-- The dates the renter asked about, shown under the pinned listing in chat
-- ("Your dates" for the renter, "Their dates" for the lender). Set when the renter
-- sends a dates question; a later question replaces them. Both or neither.
alter table public.conversations add column if not exists requested_start date;
alter table public.conversations add column if not exists requested_end date;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'conversations_requested_dates_check') then
    alter table public.conversations add constraint conversations_requested_dates_check check (
      (requested_start is null and requested_end is null)
      or (requested_start is not null and requested_end is not null and requested_end >= requested_start)
    );
  end if;
end $$;

-- conversations has no update policy, so the dates are set only through this function.
-- Only the renter of that conversation can set them; nothing else on the row changes.
create or replace function public.set_conversation_dates(p_conversation_id uuid, p_start date, p_end date)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if p_start is null or p_end is null or p_end < p_start then
    raise exception 'Invalid dates' using errcode = '22023';
  end if;
  update public.conversations
     set requested_start = p_start, requested_end = p_end
   where id = p_conversation_id and renter_id = auth.uid();
  if not found then
    raise exception 'Only the renter can set dates for this chat' using errcode = '42501';
  end if;
end $$;

revoke all on function public.set_conversation_dates(uuid, date, date) from public, anon;
grant execute on function public.set_conversation_dates(uuid, date, date) to authenticated;

-- =====================================================================
-- CHECK QUERY (optional, as the dashboard owner)
-- =====================================================================
-- select id, requested_start, requested_end from public.conversations
--  where requested_start is not null order by created_at desc limit 10;
