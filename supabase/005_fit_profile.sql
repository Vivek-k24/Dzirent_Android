-- =====================================================================
-- Dzirent Android pilot: 005 fit profile
-- Run in Supabase > SQL Editor AFTER 004_home_saved_events.sql.
-- Safe to re-run: it only adds the column if it is missing.
-- =====================================================================

-- "I'm a" on Profile > My measurements: 'man' or 'woman', optional. It sits next to the
-- body measurements in private_profiles, so only the owner can read it (the existing
-- "manage own private profile" policy covers it). Used only for fit: the Chest / Bust
-- label and the ease allowances. Never used to hide or filter listings.
alter table public.private_profiles
  add column if not exists fit_profile text
    constraint private_profiles_fit_profile_check check (fit_profile in ('man', 'woman'));

-- =====================================================================
-- CHECK QUERY (optional)
-- =====================================================================
-- select fit_profile, count(*) from public.private_profiles group by 1;   -- as the dashboard owner
