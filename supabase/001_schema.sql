-- =====================================================================
-- Dzirent Android pilot: base schema (REFERENCE COPY)
-- This is the setup script that was run once in the Supabase SQL Editor,
-- with handle_new_user() shown in its current form (from update_trigger.sql).
-- Do NOT re-run on the existing database. New changes go in 002+ files.
-- To confirm the live database matches, run the check queries at the bottom.
-- =====================================================================

create extension if not exists pgcrypto;

-- PROFILES (auto-created at sign-up by the trigger below)
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  role text not null default 'renter' check (role in ('renter','lender','both')),
  city text,
  approx_lat double precision,
  approx_lng double precision,
  adult_confirmed boolean not null default false,
  created_at timestamptz not null default now()
);

-- Current version (city added by supabase/update_trigger.sql)
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, display_name, role, adult_confirmed, city)
  values (new.id,
          coalesce(new.raw_user_meta_data->>'display_name',''),
          coalesce(new.raw_user_meta_data->>'role','renter'),
          coalesce((new.raw_user_meta_data->>'adult_confirmed')::boolean, false),
          nullif(new.raw_user_meta_data->>'city',''));
  return new;
end; $$;

create trigger on_auth_user_created after insert on auth.users
for each row execute function public.handle_new_user();

-- LISTINGS
create table public.listings (
  id uuid primary key default gen_random_uuid(),
  lender_id uuid not null references public.profiles(id) on delete cascade,
  title text not null check (char_length(title) between 3 and 80),
  category text not null check (category in
    ('Career & Professional','Occasion & Party','Cultural & Traditional',
     'Everyday','Outerwear','Bags & Accessories')),
  size_label text,
  description text check (char_length(description) <= 1000),
  price_note text check (char_length(price_note) <= 60),
  city text,
  approx_lat double precision not null,
  approx_lng double precision not null,
  status text not null default 'active' check (status in ('active','paused')),
  created_at timestamptz not null default now()
);

create table public.listing_photos (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.listings(id) on delete cascade,
  storage_path text not null,
  position int not null default 0
);

create table public.listing_measurements (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.listings(id) on delete cascade,
  label text not null check (char_length(label) between 1 and 40),
  value numeric not null check (value > 0 and value < 1000),
  unit text not null check (unit in ('cm','in'))
);

create table public.availability (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.listings(id) on delete cascade,
  start_date date not null,
  end_date date not null check (end_date >= start_date)
);

-- MESSAGING
create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.listings(id) on delete cascade,
  renter_id uuid not null references public.profiles(id) on delete cascade,
  lender_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (listing_id, renter_id)
);

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now()
);

-- SAFETY + PILOT TRACKING
create table public.blocks (
  blocker_id uuid not null references public.profiles(id) on delete cascade,
  blocked_id uuid not null references public.profiles(id) on delete cascade,
  primary key (blocker_id, blocked_id)
);

create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  target_type text not null check (target_type in ('listing','user','message')),
  target_id uuid not null,
  reason text not null check (char_length(reason) <= 500),
  created_at timestamptz not null default now()
);

create table public.rental_outcomes (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  happened boolean not null,
  answered_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

-- Either direction counts as blocked. SECURITY DEFINER so it can see the other
-- person's block rows, which RLS hides from the caller.
create or replace function public.is_blocked(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.blocks
    where (blocker_id = a and blocked_id = b) or (blocker_id = b and blocked_id = a));
$$;

-- ROW LEVEL SECURITY
alter table public.profiles enable row level security;
alter table public.listings enable row level security;
alter table public.listing_photos enable row level security;
alter table public.listing_measurements enable row level security;
alter table public.availability enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.blocks enable row level security;
alter table public.reports enable row level security;
alter table public.rental_outcomes enable row level security;

-- profiles: everyone signed in can read; users update only their own row.
-- No insert policy on purpose (the trigger creates rows). No delete policy.
create policy "read profiles" on public.profiles for select to authenticated using (true);
create policy "update own profile" on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- listings
create policy "read active listings" on public.listings for select to authenticated
  using (status = 'active' or lender_id = auth.uid());
create policy "create own listing" on public.listings for insert to authenticated
  with check (lender_id = auth.uid());
create policy "update own listing" on public.listings for update to authenticated
  using (lender_id = auth.uid()) with check (lender_id = auth.uid());
create policy "delete own listing" on public.listings for delete to authenticated
  using (lender_id = auth.uid());

-- child tables: anyone signed in can read; the listing owner can do everything
-- ("for all" covers insert, update AND delete).
create policy "read photos" on public.listing_photos for select to authenticated using (true);
create policy "write own photos" on public.listing_photos for all to authenticated
  using (exists (select 1 from public.listings l where l.id = listing_id and l.lender_id = auth.uid()))
  with check (exists (select 1 from public.listings l where l.id = listing_id and l.lender_id = auth.uid()));

create policy "read measurements" on public.listing_measurements for select to authenticated using (true);
create policy "write own measurements" on public.listing_measurements for all to authenticated
  using (exists (select 1 from public.listings l where l.id = listing_id and l.lender_id = auth.uid()))
  with check (exists (select 1 from public.listings l where l.id = listing_id and l.lender_id = auth.uid()));

create policy "read availability" on public.availability for select to authenticated using (true);
create policy "write own availability" on public.availability for all to authenticated
  using (exists (select 1 from public.listings l where l.id = listing_id and l.lender_id = auth.uid()))
  with check (exists (select 1 from public.listings l where l.id = listing_id and l.lender_id = auth.uid()));

-- conversations: participants read; only the renter starts one, never with
-- themselves, the lender must own the listing, and not if either side blocked.
create policy "read own conversations" on public.conversations for select to authenticated
  using (auth.uid() in (renter_id, lender_id));
create policy "start conversation as renter" on public.conversations for insert to authenticated
  with check (
    renter_id = auth.uid()
    and renter_id <> lender_id
    and lender_id = (select l.lender_id from public.listings l where l.id = listing_id)
    and not public.is_blocked(renter_id, lender_id)
  );

-- messages: participants read; participants send as themselves unless blocked.
create policy "read own messages" on public.messages for select to authenticated
  using (exists (select 1 from public.conversations c
                 where c.id = conversation_id and auth.uid() in (c.renter_id, c.lender_id)));
create policy "send in own conversations" on public.messages for insert to authenticated
  with check (
    sender_id = auth.uid()
    and exists (select 1 from public.conversations c
                where c.id = conversation_id
                  and auth.uid() in (c.renter_id, c.lender_id)
                  and not public.is_blocked(c.renter_id, c.lender_id))
  );

-- blocks: "for all" = the blocker can select, insert, update and delete their own rows.
create policy "manage own blocks" on public.blocks for all to authenticated
  using (blocker_id = auth.uid()) with check (blocker_id = auth.uid());

-- reports: insert only (reviewed in the Supabase dashboard).
create policy "file reports" on public.reports for insert to authenticated
  with check (reporter_id = auth.uid());

-- rental_outcomes: each user manages only their own answers.
create policy "own outcomes" on public.rental_outcomes for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- NEARBY SEARCH (km, optional category, hides blocked lenders in both directions)
create or replace function public.listings_nearby(
  p_lat double precision, p_lng double precision,
  p_radius_km double precision, p_category text default null)
returns setof public.listings language sql stable as $$
  select l.* from public.listings l
  where l.status = 'active'
    and (p_category is null or l.category = p_category)
    and not public.is_blocked(auth.uid(), l.lender_id)
    and 6371 * 2 * asin(sqrt(
          power(sin(radians(l.approx_lat - p_lat) / 2), 2) +
          cos(radians(p_lat)) * cos(radians(l.approx_lat)) *
          power(sin(radians(l.approx_lng - p_lng) / 2), 2))) <= p_radius_km
  order by l.created_at desc;
$$;

-- PHOTO STORAGE (public read; users write/delete only inside their own folder)
insert into storage.buckets (id, name, public)
values ('listing-photos', 'listing-photos', true) on conflict (id) do nothing;

create policy "upload own photos" on storage.objects for insert to authenticated
  with check (bucket_id = 'listing-photos' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "delete own photos" on storage.objects for delete to authenticated
  using (bucket_id = 'listing-photos' and (storage.foldername(name))[1] = auth.uid()::text);

-- LIVE CHAT
alter publication supabase_realtime add table public.messages;

-- =====================================================================
-- CHECK QUERIES: run these in the SQL Editor to confirm the live
-- database matches this file (paste the results to Claude Code).
-- =====================================================================
-- select tablename, policyname, cmd from pg_policies
--   where schemaname in ('public','storage') order by tablename, policyname;
-- select tablename from pg_publication_tables where pubname = 'supabase_realtime';
-- select proname from pg_proc
--   where pronamespace = 'public'::regnamespace
--   and proname in ('handle_new_user','is_blocked','listings_nearby');
