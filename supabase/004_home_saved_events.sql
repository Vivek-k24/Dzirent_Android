-- =====================================================================
-- Dzirent Android pilot: 004 home, saved items and events
-- Run in Supabase > SQL Editor AFTER 003_cities_and_accessories.sql.
-- Safe to re-run: every statement uses if not exists / or replace /
-- drop ... if exists.
-- =====================================================================

-- 1) PRICE: the lender's price per rental, in US dollars. Required by the app for new and
-- edited listings; NULL only on listings created before this column existed.
-- price_note stays as optional free-text details ("plus cleaning", "weekly discount").
alter table public.listings
  add column if not exists price_usd numeric
    constraint listings_price_usd_positive check (price_usd > 0);

-- 2) SAVED PIECES AND STUDIOS: private bookmarks. Each user sees and changes only their
-- own rows, and there is no function that counts other people's saves.
create table if not exists public.saved_listings (
  user_id uuid not null references public.profiles(id) on delete cascade,
  listing_id uuid not null references public.listings(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, listing_id)
);

create table if not exists public.saved_studios (
  user_id uuid not null references public.profiles(id) on delete cascade,
  lender_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, lender_id),
  constraint saved_studios_not_self check (user_id <> lender_id)
);

alter table public.saved_listings enable row level security;
alter table public.saved_studios enable row level security;

drop policy if exists "manage own saved listings" on public.saved_listings;
create policy "manage own saved listings" on public.saved_listings for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "manage own saved studios" on public.saved_studios;
create policy "manage own saved studios" on public.saved_studios for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Lookups go by the listing / lender when one is deleted (cascade).
create index if not exists saved_listings_listing_id_idx on public.saved_listings (listing_id);
create index if not exists saved_studios_lender_id_idx on public.saved_studios (lender_id);

-- 3) LISTING EVENTS: views, saves and chats started, kept for future personalization.
-- Users can only add their own events and can never read any back (no select policy).
create table if not exists public.listing_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  listing_id uuid not null references public.listings(id) on delete cascade,
  type text not null constraint listing_events_type_check check (type in ('view','save','message')),
  created_at timestamptz not null default now()
);

alter table public.listing_events enable row level security;

drop policy if exists "add own events" on public.listing_events;
create policy "add own events" on public.listing_events for insert to authenticated
  with check (user_id = auth.uid());

create index if not exists listing_events_user_created_idx on public.listing_events (user_id, created_at);
create index if not exists listing_events_listing_id_idx on public.listing_events (listing_id);

-- 4) PRIVATE PROFILE: data only its owner may read. Every signed-in user can read every
-- profiles row (001 "read profiles"), so this does not go on profiles.
-- Body measurements are optional, in centimeters (Profile > My measurements).
-- activity_seen_at is when the user last opened Inbox > Activity (section 5).
-- The app creates the row with an upsert the first time it saves something.
create table if not exists public.private_profiles (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  chest_cm numeric constraint private_profiles_chest_cm_range check (chest_cm > 0 and chest_cm < 300),
  waist_cm numeric constraint private_profiles_waist_cm_range check (waist_cm > 0 and waist_cm < 300),
  hips_cm numeric constraint private_profiles_hips_cm_range check (hips_cm > 0 and hips_cm < 300),
  shoulder_cm numeric constraint private_profiles_shoulder_cm_range check (shoulder_cm > 0 and shoulder_cm < 300),
  height_cm numeric constraint private_profiles_height_cm_range check (height_cm > 0 and height_cm < 300),
  inseam_cm numeric constraint private_profiles_inseam_cm_range check (inseam_cm > 0 and inseam_cm < 300),
  activity_seen_at timestamptz,
  updated_at timestamptz not null default now()
);

alter table public.private_profiles enable row level security;

drop policy if exists "manage own private profile" on public.private_profiles;
create policy "manage own private profile" on public.private_profiles for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Keep updated_at current on every update.
create or replace function public.set_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end; $$;

drop trigger if exists private_profiles_set_updated_at on public.private_profiles;
create trigger private_profiles_set_updated_at before update on public.private_profiles
for each row execute function public.set_updated_at();

-- 5) READ STATE for the Inbox dot: when each person last opened each conversation.
-- (When they last looked at the Activity tab is private_profiles.activity_seen_at.)
create table if not exists public.conversation_reads (
  user_id uuid not null references public.profiles(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  last_read_at timestamptz not null default now(),
  primary key (user_id, conversation_id)
);

alter table public.conversation_reads enable row level security;

-- Own rows only, and only for conversations the user takes part in.
drop policy if exists "manage own conversation reads" on public.conversation_reads;
create policy "manage own conversation reads" on public.conversation_reads for all to authenticated
  using (user_id = auth.uid())
  with check (
    user_id = auth.uid()
    and exists (select 1 from public.conversations c
                where c.id = conversation_reads.conversation_id
                  and auth.uid() in (c.renter_id, c.lender_id))
  );

create index if not exists conversation_reads_conversation_id_idx on public.conversation_reads (conversation_id);

-- 6) MARK AS READ / SEEN with the server's clock, so a phone with a wrong clock can't hide
-- or invent unread messages. Security invoker: the RLS policies above still apply.
create or replace function public.mark_conversation_read(p_conversation_id uuid) returns void
language sql security invoker set search_path = '' as $$
  insert into public.conversation_reads (user_id, conversation_id, last_read_at)
  values (auth.uid(), p_conversation_id, now())
  on conflict (user_id, conversation_id) do update set last_read_at = now();
$$;

create or replace function public.mark_activity_seen() returns void
language sql security invoker set search_path = '' as $$
  insert into public.private_profiles (user_id, activity_seen_at)
  values (auth.uid(), now())
  on conflict (user_id) do update set activity_seen_at = now();
$$;

revoke all on function public.mark_conversation_read(uuid) from public, anon;
grant execute on function public.mark_conversation_read(uuid) to authenticated;
revoke all on function public.mark_activity_seen() from public, anon;
grant execute on function public.mark_activity_seen() to authenticated;

-- =====================================================================
-- CHECK QUERIES (optional)
-- =====================================================================
-- select count(*) filter (where price_usd is null) as no_price, count(*) from public.listings;
-- select tablename, policyname, cmd from pg_policies
--   where tablename in ('saved_listings','saved_studios','listing_events','conversation_reads',
--                       'private_profiles')
--   order by 1, 2;
-- select type, count(*) from public.listing_events group by 1;   -- as the dashboard owner
