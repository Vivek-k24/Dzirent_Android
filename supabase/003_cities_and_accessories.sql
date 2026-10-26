-- =====================================================================
-- Dzirent Android pilot: 003 cities and accessories
-- Run in Supabase > SQL Editor AFTER 002_pilot_features.sql.
-- Safe to re-run: every statement uses if not exists / or replace /
-- drop ... if exists.
-- City rows are not in this file: import supabase/tools/us_cities.csv
-- into public.us_cities afterwards (see supabase/tools/README.md).
-- =====================================================================

-- 1) US CITIES: the list behind the "City, ST" picker (Census Gazetteer places).
create table if not exists public.us_cities (
  id text primary key,                       -- Census GEOID
  name text not null,                        -- "Springfield" (no " city" / " CDP" suffix)
  state text not null
    constraint us_cities_state_check check (state ~ '^[A-Z]{2}$'),
  lat double precision not null,             -- interior point, rounded to 2 decimals
  lng double precision not null
);

-- Read-only for everyone, including anon: sign-up picks a city before there is a session.
alter table public.us_cities enable row level security;
drop policy if exists "read cities" on public.us_cities;
create policy "read cities" on public.us_cities for select to anon, authenticated using (true);
grant select on public.us_cities to anon, authenticated;
revoke insert, update, delete, truncate on public.us_cities from anon, authenticated;

-- Case-insensitive prefix search. text_pattern_ops makes the range below an index scan.
create index if not exists us_cities_lower_name_idx
  on public.us_cities (lower(name) text_pattern_ops);

-- Cities whose name starts with p_query (case-insensitive), ordered by name then state.
-- Every exact name match is returned even past p_limit, so all 20+ Springfields are listed.
-- The prefix test is a byte range (~>=~ / ~<~) rather than LIKE, so % and _ in the query
-- are plain characters and the index is used with a run-time parameter.
create or replace function public.search_cities(p_query text, p_limit int default 20)
returns setof public.us_cities
language sql stable
set search_path = ''
as $$
  select c.*
  from public.us_cities c
  where char_length(btrim(p_query)) >= 2
    and lower(c.name) ~>=~ lower(btrim(p_query))
    and lower(c.name) ~<~ (lower(btrim(p_query)) || chr(1114111))
  order by c.name, c.state
  limit greatest(
    least(greatest(coalesce(p_limit, 20), 1), 100),
    (select count(*) from public.us_cities e where lower(e.name) = lower(btrim(p_query))));
$$;

revoke all on function public.search_cities(text, int) from public;
grant execute on function public.search_cities(text, int) to anon, authenticated;

-- 2) SIGN-UP LOCATION: the trigger also saves the approximate location of the city picked
-- at sign-up (sent as approx_lat / approx_lng metadata), so it is stored even when email
-- confirmation means the app has no session to update the profile itself.
-- Values are re-rounded to 2 decimals here; anything malformed or out of range is ignored.
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  v_lat double precision;
  v_lng double precision;
begin
  if new.raw_user_meta_data->>'approx_lat' ~ '^-?[0-9]{1,3}(\.[0-9]+)?$'
     and new.raw_user_meta_data->>'approx_lng' ~ '^-?[0-9]{1,3}(\.[0-9]+)?$' then
    v_lat := round((new.raw_user_meta_data->>'approx_lat')::numeric, 2);
    v_lng := round((new.raw_user_meta_data->>'approx_lng')::numeric, 2);
    if v_lat not between -90 and 90 or v_lng not between -180 and 180 then
      v_lat := null;
      v_lng := null;
    end if;
  end if;

  insert into public.profiles (id, display_name, role, adult_confirmed, city, approx_lat, approx_lng)
  values (new.id,
          coalesce(new.raw_user_meta_data->>'display_name',''),
          coalesce(new.raw_user_meta_data->>'role','renter'),
          coalesce((new.raw_user_meta_data->>'adult_confirmed')::boolean, false),
          nullif(new.raw_user_meta_data->>'city',''),
          v_lat,
          v_lng);
  return new;
end; $$;

-- 3) ACCESSORIES: "Bags & Accessories" becomes "Accessories", with a required item type
-- (required by the app; the database only checks the allowed values).
-- The old category check was declared inline in 001, so its name was generated. Drop every
-- check on listings that mentions category or item_type, then add both back by name.
do $$
declare
  r record;
begin
  for r in
    select conname from pg_constraint
    where conrelid = 'public.listings'::regclass
      and contype = 'c'
      and (pg_get_constraintdef(oid) like '%category%' or pg_get_constraintdef(oid) like '%item_type%')
  loop
    execute format('alter table public.listings drop constraint %I', r.conname);
  end loop;
end $$;

update public.listings set category = 'Accessories' where category = 'Bags & Accessories';

alter table public.listings
  add constraint listings_category_check check (category in
    ('Career & Professional','Occasion & Party','Cultural & Traditional',
     'Everyday','Outerwear','Accessories'));

alter table public.listings add column if not exists item_type text;
alter table public.listings
  add constraint listings_item_type_check check (
    item_type is null
    or (category = 'Accessories'
        and item_type in ('Sunglasses','Handbag','Tie','Watch (non-smart)','Belt buckle')));

-- =====================================================================
-- CHECK QUERIES (optional)
-- =====================================================================
-- select count(*) from public.us_cities;                      -- about 32,000 after the import
-- select * from public.search_cities('springfield', 20);      -- every Springfield, by state
-- select * from public.search_cities('aus');                  -- Austin, Austintown, ...
-- select category, item_type, count(*) from public.listings group by 1, 2 order by 1, 2;
-- select conname, pg_get_constraintdef(oid) from pg_constraint
--   where conrelid = 'public.listings'::regclass and contype = 'c';
