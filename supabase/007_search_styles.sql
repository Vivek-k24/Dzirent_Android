-- =====================================================================
-- Dzirent Android pilot: 007 search suggestions and styles
-- Run in Supabase > SQL Editor AFTER 006_chat_dates.sql.
-- Safe to re-run: everything is added only if missing, and functions are replaced.
-- =====================================================================

-- 1) TYPO-TOLERANT SEARCH: when a search finds nothing, the app asks for listings whose
--    title or description is close to what was typed ("lehnga" finds "lehenga").
create schema if not exists extensions;
create extension if not exists pg_trgm with schema extensions;
-- Signed-in users need the schema to use its operators (Supabase grants this already).
grant usage on schema extensions to authenticated;

-- Trigram indexes on title and description. The operator class lives in whichever schema
-- pg_trgm was installed in (extensions on Supabase), so look it up.
do $$
declare
  trgm_schema text;
begin
  select n.nspname into trgm_schema
    from pg_extension e join pg_namespace n on n.oid = e.extnamespace
   where e.extname = 'pg_trgm';
  execute format('create index if not exists listings_title_trgm_idx on public.listings using gin (title %I.gin_trgm_ops)', trgm_schema);
  execute format('create index if not exists listings_description_trgm_idx on public.listings using gin (description %I.gin_trgm_ops)', trgm_schema);
end $$;

-- Active listings whose title or description contains a word close to p_query, best match
-- first. Security invoker, so the listings policies apply; it also leaves out the caller's
-- own listings and anyone either side has blocked. Queries under 3 characters return nothing.
create or replace function public.search_listings_similar(p_query text, p_limit int default 12)
returns setof public.listings
language plpgsql stable security invoker
set search_path = public, extensions, pg_temp as $$
declare
  q text := lower(trim(coalesce(p_query, '')));
begin
  if char_length(q) < 3 then
    return;
  end if;
  -- How close a word must be (0..1). 0.3 lets one or two letters be wrong.
  perform set_config('pg_trgm.word_similarity_threshold', '0.3', true);
  return query
    select l.*
      from public.listings l
     where l.status = 'active'
       and l.lender_id <> auth.uid()
       and not public.is_blocked(auth.uid(), l.lender_id)
       and (q <% l.title or q <% coalesce(l.description, ''))
     order by greatest(word_similarity(q, l.title), word_similarity(q, coalesce(l.description, ''))) desc,
              l.created_at desc
     limit least(greatest(coalesce(p_limit, 12), 1), 50);
end $$;

revoke all on function public.search_listings_similar(text, int) from public, anon;
grant execute on function public.search_listings_similar(text, int) to authenticated;

-- =====================================================================
-- CHECK QUERIES (optional)
-- =====================================================================
-- select extname, extnamespace::regnamespace from pg_extension where extname = 'pg_trgm';
-- select indexname from pg_indexes where tablename = 'listings' and indexname like '%trgm%';
