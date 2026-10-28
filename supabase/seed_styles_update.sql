-- =====================================================================
-- Dzirent Android pilot: TEST DATA for styles ("For your style" row)
-- Run in Supabase > SQL Editor AFTER 007_search_styles.sql (and after the test listings
-- and the test12-test19 accounts exist). Safe to re-run: it sets the same values again.
--
-- 1. Sets listing_styles on test listings by matching words in the title (a listing can
--    match up to 3 styles, in list order). "Test listings" = titles starting with
--    "[TEST]" or listings owned by an account named test<number>. To style every listing
--    instead, remove the "and (...)" scope condition in step 1.
-- 2. Sets style_interests for test12, test13 and test15-test19 (matched by the part of
--    the email before the @, or by display name), so "For your style" has pieces to show.
-- The NOTICE lines in the Messages tab say how many rows were changed.
-- =====================================================================

do $$
declare
  n int;
begin
  -- 1) LISTING STYLES, by title words (case-insensitive).
  create temp table _style_words (style text, ord int, pattern text) on commit drop;
  insert into _style_words (style, ord, pattern) values
    ('South Asian', 1, '%saree%'), ('South Asian', 1, '%sari %'), ('South Asian', 1, '%lehenga%'),
    ('South Asian', 1, '%lehnga%'), ('South Asian', 1, '%anarkali%'), ('South Asian', 1, '%kurta%'),
    ('South Asian', 1, '%kurti%'), ('South Asian', 1, '%potli%'), ('South Asian', 1, '%sherwani%'),
    ('South Asian', 1, '%salwar%'), ('South Asian', 1, '%dupatta%'), ('South Asian', 1, '%ghagra%'),
    ('South Asian', 1, '%choli%'), ('South Asian', 1, '%bandhgala%'), ('South Asian', 1, '%nehru%'),
    ('East Asian', 2, '%kimono%'), ('East Asian', 2, '%hanbok%'), ('East Asian', 2, '%qipao%'),
    ('East Asian', 2, '%cheongsam%'), ('East Asian', 2, '%yukata%'),
    ('Southeast Asian', 3, '%kebaya%'), ('Southeast Asian', 3, '%ao dai%'), ('Southeast Asian', 3, '%barong%'),
    ('Southeast Asian', 3, '%batik%'), ('Southeast Asian', 3, '%sarong%'),
    ('Middle Eastern', 4, '%abaya%'), ('Middle Eastern', 4, '%kaftan%'), ('Middle Eastern', 4, '%caftan%'),
    ('Middle Eastern', 4, '%thobe%'),
    ('African', 5, '%dashiki%'), ('African', 5, '%kente%'), ('African', 5, '%ankara%'), ('African', 5, '%boubou%'),
    ('Mexican & Latin American', 6, '%huipil%'), ('Mexican & Latin American', 6, '%quince%'),
    ('Mexican & Latin American', 6, '%charro%'), ('Mexican & Latin American', 6, '%rebozo%'),
    ('Mexican & Latin American', 6, '%guayabera%'), ('Mexican & Latin American', 6, '%poncho%'),
    ('Country & Western', 7, '%western shirt%'), ('Country & Western', 7, '%pearl snap%'),
    ('Country & Western', 7, '%belt buckle%'), ('Country & Western', 7, '%cowboy%'),
    ('Country & Western', 7, '%bolo%'), ('Country & Western', 7, '%rodeo%'), ('Country & Western', 7, '%fringe%'),
    ('Western formal', 8, '%suit%'), ('Western formal', 8, '%blazer%'), ('Western formal', 8, '%tux%'),
    ('Western formal', 8, '%gown%'), ('Western formal', 8, '%cocktail%'), ('Western formal', 8, '%pencil skirt%'),
    ('Western formal', 8, '%evening%'),
    ('Western casual', 9, '%jeans%'), ('Western casual', 9, '%denim%'), ('Western casual', 9, '%button-down%'),
    ('Western casual', 9, '%t-shirt%'), ('Western casual', 9, '%hoodie%'), ('Western casual', 9, '%sweater%'),
    ('Western casual', 9, '%cardigan%'), ('Western casual', 9, '%moto jacket%'), ('Western casual', 9, '%linen%'),
    ('Western casual', 9, '%crossbody%'), ('Western casual', 9, '%overcoat%'), ('Western casual', 9, '%chinos%');

  with matched as (
    select l.id, w.style, min(w.ord) as ord
      from public.listings l
      join _style_words w on l.title ilike w.pattern
      left join auth.users u on u.id = l.lender_id
     where (l.title like '[TEST]%' or split_part(lower(coalesce(u.email, '')), '@', 1) ~ '^test[0-9]+$')
     group by l.id, w.style
  ), picked as (
    select id, (array_agg(style order by ord))[1:3] as styles
      from matched
     group by id
  )
  update public.listings l
     set listing_styles = p.styles
    from picked p
   where l.id = p.id;
  get diagnostics n = row_count;
  raise notice 'Listings given styles: %', n;

  -- 2) STYLE INTERESTS for the test accounts (creates their private_profiles row if needed).
  with wanted (name, styles) as (
    values
      ('test12', array['South Asian', 'Western formal']),
      ('test13', array['Country & Western', 'Western casual']),
      ('test15', array['South Asian', 'Western casual']),
      ('test16', array['Western formal', 'Western casual']),
      ('test17', array['South Asian', 'Country & Western']),
      ('test18', array['Western casual', 'South Asian', 'Western formal']),
      ('test19', array['Western formal', 'Country & Western', 'South Asian'])
  ), accounts as (
    select distinct on (w.name) p.id, w.styles
      from wanted w
      join public.profiles p on lower(p.display_name) = w.name
         or p.id in (select u.id from auth.users u where split_part(lower(u.email), '@', 1) = w.name)
     order by w.name, p.id
  )
  insert into public.private_profiles (user_id, style_interests)
  select id, styles from accounts
  on conflict (user_id) do update set style_interests = excluded.style_interests;
  get diagnostics n = row_count;
  raise notice 'Test accounts given style interests: % (of 7)', n;
end $$;

-- =====================================================================
-- CHECK QUERIES (optional)
-- =====================================================================
-- select title, listing_styles from public.listings where cardinality(listing_styles) > 0 order by title;
-- select p.display_name, pp.style_interests from public.private_profiles pp
--   join public.profiles p on p.id = pp.user_id where cardinality(pp.style_interests) > 0;
