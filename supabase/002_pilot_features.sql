-- =====================================================================
-- Dzirent Android pilot: 002 pilot features
-- Run in Supabase > SQL Editor AFTER 001_schema.sql (already applied).
-- Safe to re-run: every statement uses if not exists / or replace /
-- drop ... if exists, or checks the catalog first.
-- =====================================================================

-- 1) ROLES: copy the role chosen at sign-up into profiles.
-- NOTE: once the in-app "I want to: Rent / Lend / Both" switch is live, re-running this
-- would overwrite a role a user changed in the app with their original sign-up choice.
-- Run it once now; on later re-runs you can skip or comment out this statement.
update public.profiles p
set role = u.raw_user_meta_data->>'role'
from auth.users u
where u.id = p.id
  and u.raw_user_meta_data->>'role' in ('renter','lender','both')
  and p.role is distinct from u.raw_user_meta_data->>'role';

-- 2) LISTINGS: optional retail value and deposit note.
alter table public.listings
  add column if not exists retail_value numeric
    constraint listings_retail_value_positive check (retail_value > 0);
alter table public.listings
  add column if not exists deposit_note text
    constraint listings_deposit_note_length check (char_length(deposit_note) <= 120);

-- 3) ONLY LENDERS CAN LIST: replace the insert policy so it also checks the role.
drop policy if exists "create own listing" on public.listings;
create policy "create own listing" on public.listings for insert to authenticated
  with check (
    lender_id = auth.uid()
    and exists (select 1 from public.profiles p
                where p.id = auth.uid() and p.role in ('lender','both'))
  );

-- 4) RENTALS: one row per conversation, updated manually by either participant.
create table if not exists public.rentals (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null unique references public.conversations(id) on delete cascade,
  listing_id uuid not null references public.listings(id) on delete cascade,
  renter_id uuid not null references public.profiles(id) on delete cascade,
  lender_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'active'
    constraint rentals_status_check check (status in ('active','returned','cancelled')),
  start_date date,
  end_date date,
  updated_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint rentals_dates_check check (start_date is null or end_date is null or end_date >= start_date)
);

alter table public.rentals enable row level security;

-- Keep updated_at current on every update.
create or replace function public.rentals_set_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end; $$;

drop trigger if exists rentals_set_updated_at on public.rentals;
create trigger rentals_set_updated_at before update on public.rentals
for each row execute function public.rentals_set_updated_at();

-- Participants only. Insert and update must match the conversation's listing, renter
-- and lender, so a participant can't point the row at someone else. updated_by must be
-- the caller. Column refs are qualified with "rentals." because conversations has
-- columns with the same names. No delete policy on purpose.
drop policy if exists "read own rentals" on public.rentals;
create policy "read own rentals" on public.rentals for select to authenticated
  using (auth.uid() in (renter_id, lender_id));

drop policy if exists "create rental in own conversation" on public.rentals;
create policy "create rental in own conversation" on public.rentals for insert to authenticated
  with check (
    auth.uid() in (rentals.renter_id, rentals.lender_id)
    and rentals.updated_by = auth.uid()
    and exists (select 1 from public.conversations c
                where c.id = rentals.conversation_id
                  and c.listing_id = rentals.listing_id
                  and c.renter_id = rentals.renter_id
                  and c.lender_id = rentals.lender_id)
  );

drop policy if exists "update own rentals" on public.rentals;
create policy "update own rentals" on public.rentals for update to authenticated
  using (auth.uid() in (renter_id, lender_id))
  with check (
    auth.uid() in (rentals.renter_id, rentals.lender_id)
    and rentals.updated_by = auth.uid()
    and exists (select 1 from public.conversations c
                where c.id = rentals.conversation_id
                  and c.listing_id = rentals.listing_id
                  and c.renter_id = rentals.renter_id
                  and c.lender_id = rentals.lender_id)
  );

-- 5) INDEXES
create index if not exists listing_photos_listing_id_idx on public.listing_photos (listing_id);
create index if not exists listing_measurements_listing_id_idx on public.listing_measurements (listing_id);
create index if not exists availability_listing_id_idx on public.availability (listing_id);
create index if not exists messages_conversation_created_idx on public.messages (conversation_id, created_at);
create index if not exists conversations_renter_id_idx on public.conversations (renter_id);
create index if not exists conversations_lender_id_idx on public.conversations (lender_id);
create index if not exists rentals_renter_id_idx on public.rentals (renter_id);
create index if not exists rentals_lender_id_idx on public.rentals (lender_id);

-- 6) REPORTS: optional free-text details next to the reason.
alter table public.reports
  add column if not exists details text
    constraint reports_details_length check (char_length(details) <= 500);

-- 7) BLOCK PEERS: ids I blocked plus ids who blocked me, for hiding listings and
-- disabling chat. SECURITY DEFINER because RLS hides other people's block rows.
-- Returns ids only, not which direction the block goes.
create or replace function public.my_block_peer_ids() returns setof uuid
language sql stable security definer set search_path = public as $$
  select blocked_id from public.blocks where blocker_id = auth.uid()
  union
  select blocker_id from public.blocks where blocked_id = auth.uid();
$$;

revoke all on function public.my_block_peer_ids() from public, anon;
grant execute on function public.my_block_peer_ids() to authenticated;

-- 8) LIVE UPDATES: make sure messages and rentals are in the realtime publication.
do $$
begin
  if not exists (select 1 from pg_publication_tables
                 where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'messages') then
    alter publication supabase_realtime add table public.messages;
  end if;
  if not exists (select 1 from pg_publication_tables
                 where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'rentals') then
    alter publication supabase_realtime add table public.rentals;
  end if;
end $$;

-- =====================================================================
-- CHECK QUERIES (optional)
-- =====================================================================
-- select role, count(*) from public.profiles group by role;
-- select policyname, cmd from pg_policies where tablename in ('listings','rentals') order by 1;
-- select tablename from pg_publication_tables where pubname = 'supabase_realtime';
-- select * from public.my_block_peer_ids();
