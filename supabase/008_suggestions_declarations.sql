-- =====================================================================
-- Dzirent Android pilot: 008 photo suggestions, what can be listed, declarations
-- Run in Supabase > SQL Editor AFTER 007_search_styles.sql.
-- Safe to re-run: columns and tables are added only if missing, constraints and
-- policies are dropped and added again, and functions and triggers are replaced.
-- =====================================================================

-- 1) LISTINGS
-- garment_type: what the piece is (the app's GarmentKind), which decides the size picker
-- and measurement rows, and lets "Same as your …" find the lender's last one.
alter table public.listings add column if not exists garment_type text;
alter table public.listings drop constraint if exists listings_garment_type_check;
alter table public.listings add constraint listings_garment_type_check check (
  garment_type is null or garment_type in ('top','trousers','suit','dress','skirt','saree','one_size'));

-- ai_review_needed: the lender kept a photo the phone removed ("It's fashion, keep it") or
-- overruled a photo warning, so the operator checks the listing. Owners can set it but never
-- clear it; only the operator can (SQL editor or the service role).
alter table public.listings add column if not exists ai_review_needed boolean not null default false;
create index if not exists listings_ai_review_needed_idx on public.listings (created_at) where ai_review_needed;

-- Not security definer, so current_user is the caller's role ('authenticated' for the app).
create or replace function public.keep_ai_review_needed() returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if old.ai_review_needed and not new.ai_review_needed and current_user in ('authenticated', 'anon') then
    new.ai_review_needed := true;
  end if;
  return new;
end $$;

drop trigger if exists listings_keep_ai_review_needed on public.listings;
create trigger listings_keep_ai_review_needed before update on public.listings
  for each row execute function public.keep_ai_review_needed();

-- Accessories: the new item type "Costume jewelry" (same list as AccessoryTypes.ALL).
alter table public.listings drop constraint if exists listings_item_type_check;
alter table public.listings add constraint listings_item_type_check check (
  item_type is null
  or (category = 'Accessories'
      and item_type in ('Sunglasses','Handbag','Tie','Watch (non-smart)','Belt buckle','Costume jewelry')));

-- Accessories worth more than $300 can't be listed (the app blocks it too). NOT VALID: it
-- applies to every new or changed row and leaves older rows as they are until edited.
alter table public.listings drop constraint if exists listings_accessory_value_check;
alter table public.listings add constraint listings_accessory_value_check check (
  category <> 'Accessories' or retail_value is null or retail_value <= 300) not valid;

-- 2) LISTING PHOTOS: which slot each photo is (Front, Back, Close-up, Size tag, Extra).
alter table public.listing_photos add column if not exists slot text;
alter table public.listing_photos drop constraint if exists listing_photos_slot_check;
alter table public.listing_photos add constraint listing_photos_slot_check check (
  slot is null or slot in ('front','back','close_up','size_tag','extra_1','extra_2'));

-- 3) ACCESSORY DECLARATIONS: signed once per user (per declaration version) before listing
-- jewelry or accessories. A typed full legal name plus the ticked statements is the signature.
-- Kept if the account is deleted: user_id becomes null, the rest stays (see the privacy notes).
create table if not exists public.accessory_declarations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete set null,
  email text,
  full_legal_name text not null
    constraint accessory_declarations_name_check check (char_length(btrim(full_legal_name)) between 2 and 120),
  declaration_version int not null
    constraint accessory_declarations_version_check check (declaration_version > 0),
  app_version text,
  city text,
  ip text,
  user_agent text,
  session_id text,
  created_at timestamptz not null default now()
);
create index if not exists accessory_declarations_user_idx on public.accessory_declarations (user_id, declaration_version);

-- 4) LISTING DECLARATIONS: the per-listing tick on every accessory listing.
create table if not exists public.listing_declarations (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid references public.listings(id) on delete set null,
  user_id uuid references auth.users(id) on delete set null,
  declaration_version int not null
    constraint listing_declarations_version_check check (declaration_version > 0),
  ip text,
  session_id text,
  created_at timestamptz not null default now()
);
create index if not exists listing_declarations_listing_idx on public.listing_declarations (listing_id);
create index if not exists listing_declarations_user_idx on public.listing_declarations (user_id);

-- Users read their own rows; nobody writes them directly (only through the functions below),
-- and nobody can change or delete one.
alter table public.accessory_declarations enable row level security;
alter table public.listing_declarations enable row level security;

drop policy if exists "read own accessory declarations" on public.accessory_declarations;
create policy "read own accessory declarations" on public.accessory_declarations for select to authenticated
  using (user_id = auth.uid());
drop policy if exists "read own listing declarations" on public.listing_declarations;
create policy "read own listing declarations" on public.listing_declarations for select to authenticated
  using (user_id = auth.uid());

revoke all on public.accessory_declarations from anon, authenticated;
revoke all on public.listing_declarations from anon, authenticated;
grant select on public.accessory_declarations to authenticated;
grant select on public.listing_declarations to authenticated;

-- The caller's IP: the first address in x-forwarded-for (the client; later ones are proxies).
create or replace function public.declaration_request_ip() returns text
language sql stable set search_path = public, pg_temp as $$
  select nullif(btrim(split_part(
    coalesce(nullif(current_setting('request.headers', true), '')::json->>'x-forwarded-for', ''), ',', 1)), '');
$$;

-- Signs the accessory declaration. Who (auth.uid()), the email (auth), the session (the JWT's
-- session_id), the IP and user agent (request headers) and the city (the profile) all come
-- from the server, never from the app. Returns the new row's id.
create or replace function public.record_accessory_declaration(
  p_full_legal_name text,
  p_declaration_version int,
  p_app_version text default null
) returns uuid
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := auth.uid();
  v_headers json := nullif(current_setting('request.headers', true), '')::json;
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'Not signed in' using errcode = '42501';
  end if;
  if char_length(btrim(coalesce(p_full_legal_name, ''))) < 2 then
    raise exception 'Type your full legal name' using errcode = '22023';
  end if;
  if coalesce(p_declaration_version, 0) < 1 then
    raise exception 'Unknown declaration version' using errcode = '22023';
  end if;

  insert into public.accessory_declarations
    (user_id, email, full_legal_name, declaration_version, app_version, city, ip, user_agent, session_id)
  values (
    v_uid,
    coalesce(auth.jwt()->>'email', (select u.email from auth.users u where u.id = v_uid)),
    left(btrim(p_full_legal_name), 120),
    p_declaration_version,
    left(p_app_version, 40),
    (select nullif(p.city, '') from public.profiles p where p.id = v_uid),
    public.declaration_request_ip(),
    left(v_headers->>'user-agent', 400),
    auth.jwt()->>'session_id'
  )
  returning id into v_id;
  return v_id;
end $$;

-- Records the per-listing tick for one of the caller's own accessory listings.
create or replace function public.record_listing_declaration(p_listing_id uuid, p_declaration_version int)
returns uuid
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := auth.uid();
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'Not signed in' using errcode = '42501';
  end if;
  if not exists (select 1 from public.listings l where l.id = p_listing_id and l.lender_id = v_uid) then
    raise exception 'Not your listing' using errcode = '42501';
  end if;
  if coalesce(p_declaration_version, 0) < 1 then
    raise exception 'Unknown declaration version' using errcode = '22023';
  end if;

  insert into public.listing_declarations (listing_id, user_id, declaration_version, ip, session_id)
  values (p_listing_id, v_uid, p_declaration_version, public.declaration_request_ip(), auth.jwt()->>'session_id')
  returning id into v_id;
  return v_id;
end $$;

revoke all on function public.record_accessory_declaration(text, int, text) from public, anon;
revoke all on function public.record_listing_declaration(uuid, int) from public, anon;
revoke all on function public.declaration_request_ip() from public, anon;
grant execute on function public.record_accessory_declaration(text, int, text) to authenticated;
grant execute on function public.record_listing_declaration(uuid, int) to authenticated;

-- An accessory listing can only go live once its lender has signed the declaration (the app
-- asks first; this is the server-side backstop). Not security definer: it reads the
-- declarations as the caller, whose own rows RLS lets them see.
create or replace function public.require_accessory_declaration() returns trigger
language plpgsql set search_path = public, pg_temp as $$
begin
  if new.category = 'Accessories' and new.status = 'active'
     and current_user in ('authenticated', 'anon')
     and (tg_op = 'INSERT' or old.status is distinct from 'active' or old.category is distinct from new.category)
     and not exists (select 1 from public.accessory_declarations d where d.user_id = new.lender_id) then
    raise exception 'Sign the accessory declaration before listing accessories.' using errcode = 'P0001';
  end if;
  return new;
end $$;

drop trigger if exists listings_require_accessory_declaration on public.listings;
create trigger listings_require_accessory_declaration before insert or update on public.listings
  for each row execute function public.require_accessory_declaration();

-- 5) SUGGESTION EVENTS: what happened to the photo suggestions, for the monitoring dashboard
-- (see docs/MONITORING_IDEAS.md). Users insert their own rows and can't read any.
-- Removals and overrides are the demand signal for things Dzirent doesn't list (bikes...).
create table if not exists public.suggestion_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete set null,
  listing_id uuid references public.listings(id) on delete set null,
  field text not null
    constraint suggestion_events_field_check check (field in ('title','category','description','style','type_chip','tier')),
  label text not null
    constraint suggestion_events_label_check check (char_length(label) <= 80),
  action text not null
    constraint suggestion_events_action_check check (action in ('kept','cleared','edited','picked','removed','kept_override')),
  created_at timestamptz not null default now()
);
create index if not exists suggestion_events_created_idx on public.suggestion_events (created_at);
create index if not exists suggestion_events_field_label_idx on public.suggestion_events (field, label, action);

alter table public.suggestion_events enable row level security;
drop policy if exists "add own suggestion events" on public.suggestion_events;
create policy "add own suggestion events" on public.suggestion_events for insert to authenticated
  with check (
    user_id = auth.uid()
    and (listing_id is null
         or exists (select 1 from public.listings l where l.id = listing_id and l.lender_id = auth.uid())));
revoke all on public.suggestion_events from anon, authenticated;
grant insert on public.suggestion_events to authenticated;

-- =====================================================================
-- CHECK QUERIES (optional)
-- =====================================================================
-- What was added:
-- select column_name from information_schema.columns
--   where table_schema = 'public' and table_name = 'listings'
--     and column_name in ('garment_type','ai_review_needed');
-- select tablename, policyname, cmd from pg_policies
--   where tablename in ('accessory_declarations','listing_declarations','suggestion_events') order by 1, 2;
-- select grantee, table_name, privilege_type from information_schema.role_table_grants
--   where table_name in ('accessory_declarations','listing_declarations','suggestion_events')
--     and grantee in ('anon','authenticated') order by 2, 1, 3;
--   -- expected: authenticated SELECT on both declaration tables, INSERT on suggestion_events, nothing else.
--
-- Listings for the operator to check:
-- select id, title, category, created_at from public.listings where ai_review_needed order by created_at desc;
--
-- RLS checks (pgTAP-style, run as one block; everything is rolled back). Replace USER_A and
-- USER_B with the ids of two test accounts, USER_A having signed the declaration once in the app.
-- begin;
--   set local role authenticated;
--   select set_config('request.jwt.claims', json_build_object('sub', 'USER_B', 'role', 'authenticated')::text, true);
--   -- 1. B can't read A's declarations: expect 0.
--   select count(*) as others_rows from public.accessory_declarations where user_id = 'USER_A';
--   select count(*) as others_rows from public.listing_declarations where user_id = 'USER_A';
--   -- 2. B can't write a declaration directly: expect "permission denied for table accessory_declarations".
--   -- insert into public.accessory_declarations (user_id, full_legal_name, declaration_version) values ('USER_B', 'B', 1);
--   -- 3. B can't read suggestion events at all: expect "permission denied for table suggestion_events".
--   -- select count(*) from public.suggestion_events;
--   select set_config('request.jwt.claims', json_build_object('sub', 'USER_A', 'role', 'authenticated')::text, true);
--   -- 4. A reads their own: expect 1 or more.
--   select count(*) as own_rows from public.accessory_declarations where user_id = 'USER_A';
--   -- 5. A can't change or delete their own: expect "permission denied" for each.
--   -- update public.accessory_declarations set full_legal_name = 'Someone else' where user_id = 'USER_A';
--   -- delete from public.accessory_declarations where user_id = 'USER_A';
--   -- delete from public.listing_declarations where user_id = 'USER_A';
--   -- 6. A can't clear ai_review_needed on their listing: expect true afterwards.
--   -- update public.listings set ai_review_needed = false where lender_id = 'USER_A' and ai_review_needed;
--   -- select bool_and(ai_review_needed) from public.listings where lender_id = 'USER_A' and id = 'A_LISTING_ID';
-- rollback;
--
-- Declarations survive account deletion (user_id becomes null):
-- select count(*) from public.accessory_declarations where user_id is null;
