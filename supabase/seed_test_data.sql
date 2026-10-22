-- =====================================================================
-- Dzirent Android pilot: TEST DATA (12 listings around Austin, TX)
-- Run in Supabase > SQL Editor AFTER 002_pilot_features.sql.
-- 1. Type the lender's email on the v_email line below.
-- 2. Run. The account must already exist and have role 'lender' or 'both'.
-- Every title starts with "[TEST] ". No photos are added, so the app shows its
-- "No images available · Test only" placeholder. Running it twice adds 12 more
-- listings; use the cleanup statement at the bottom first if you want a fresh set.
-- =====================================================================

do $$
declare
  v_email  text := 'TYPE-LENDER-EMAIL-HERE';   -- <== the lender account's email
  v_lender uuid;
  v_role   text;
begin
  select u.id into v_lender from auth.users u where lower(u.email) = lower(trim(v_email));
  if v_lender is null then
    raise exception 'No account found for %. Sign up in the app first, then re-run.', v_email;
  end if;

  select p.role into v_role from public.profiles p where p.id = v_lender;
  if v_role is null or v_role not in ('lender','both') then
    raise exception 'Account % has role %, expected lender or both.', v_email, coalesce(v_role, '(no profile)');
  end if;

  drop table if exists _seed;
  create temp table _seed (
    id           uuid primary key default gen_random_uuid(),
    title        text not null,
    category     text not null,
    size_label   text,
    description  text,
    price_note   text,
    lat          double precision not null,
    lng          double precision not null,
    retail_value numeric,
    deposit_note text,
    unit         text not null,
    labels       text[] not null,
    vals         numeric[] not null,
    a1_start int not null, a1_end int not null,   -- days from today
    a2_start int, a2_end int
  ) on commit drop;

  insert into _seed (title, category, size_label, description, price_note, lat, lng,
                     retail_value, deposit_note, unit, labels, vals,
                     a1_start, a1_end, a2_start, a2_end)
  values
    -- Career & Professional
    ('[TEST] Navy Two-Piece Interview Suit', 'Career & Professional', 'M / 40R',
     'Wool-blend navy suit, worn twice for career fair interviews. Dry cleaned. Pants have a 34 waist with 1 inch to let out.',
     '$20/day or $45/weekend', 30.29, -97.75, 220, 'Refundable $40 deposit, cash at pickup',
     'in', array['Shoulder','Chest','Sleeve','Length','Waist'], array[18, 40, 25, 30, 34],
     2, 9, 20, 30),           -- West Campus
    ('[TEST] Black Blazer and Pencil Skirt Set', 'Career & Professional', 'S / US 4',
     'Matching black blazer and knee-length pencil skirt. Good for presentations and internship interviews.',
     '$15/day', 30.31, -97.73, null, null,
     'cm', array['Shoulder','Chest','Sleeve','Waist'], array[38, 88, 60, 68],
     1, 14, null, null),      -- Hyde Park

    -- Occasion & Party
    ('[TEST] Emerald Satin Formal Gown', 'Occasion & Party', 'US 6',
     'Floor-length emerald satin gown with a side slit. Worn once to a spring formal. Fits true to size.',
     '$30 for the weekend', 30.27, -97.74, 180, 'Ask me about a $50 refundable deposit',
     'in', array['Bust','Waist','Hips','Total Length'], array[34, 27, 37, 58],
     5, 8, 26, 29),           -- Downtown
    ('[TEST] Silver Sequin Mini Party Dress', 'Occasion & Party', 'XS',
     'Sparkly silver sequin mini dress with thin straps. Great for birthdays and New Year''s parties.',
     '$12/day', 30.30, -97.70, null, null,
     'cm', array['Bust','Waist','Hips','Total Length'], array[80, 64, 88, 82],
     3, 17, null, null),      -- Mueller

    -- Cultural & Traditional
    ('[TEST] Maroon Silk Saree with Blouse', 'Cultural & Traditional', 'Blouse 36',
     'Maroon silk saree with gold border and a stitched blouse. Petticoat included. Ideal for Diwali events.',
     '$25 per event', 30.24, -97.73, 160, null,
     'in', array['Chest/Bust','Waist','Kurta/Top Length','Shoulder'], array[36, 30, 15, 14],
     4, 12, 35, 45),          -- Riverside
    ('[TEST] Cream Kurta Pajama Set', 'Cultural & Traditional', 'L',
     'Cream cotton kurta with light embroidery and a matching pajama. Comfortable for long events.',
     '$15/day, $35 for 3 days', 30.32, -97.72, null, null,
     'cm', array['Chest/Bust','Waist','Kurta/Top Length','Bottom Length','Shoulder'], array[106, 92, 104, 102, 46],
     1, 21, null, null),      -- North Loop

    -- Everyday
    ('[TEST] Vintage Light Wash Straight Jeans', 'Everyday', 'W30 L32',
     'Light wash straight-leg jeans from the 90s. Soft denim, no rips.',
     '$8/day', 30.28, -97.76, null, null,
     'in', array['Waist','Inseam','Length'], array[30, 32, 42],
     2, 30, null, null),      -- Clarksville
    ('[TEST] Oversized Linen Button-Down', 'Everyday', 'M',
     'White oversized linen shirt. Works as a beach cover-up or for a casual brunch.',
     '$5/day', 30.25, -97.75, null, null,
     'cm', array['Chest','Length','Waist'], array[116, 76, 112],
     6, 13, 40, 55),          -- South Congress

    -- Outerwear
    ('[TEST] Camel Wool Overcoat', 'Outerwear', 'M',
     'Knee-length camel wool coat. Warm enough for a winter trip up north.',
     '$15/day or $40/week', 30.26, -97.77, 250, '$60 refundable deposit, returned at drop-off',
     'in', array['Chest','Shoulder','Sleeve Length','Total Length'], array[42, 18, 25, 40],
     7, 21, null, null),      -- Zilker
    ('[TEST] Black Leather Moto Jacket', 'Outerwear', 'S',
     'Real leather moto jacket with silver zips. Broken in and soft.',
     '$12/day', 30.26, -97.72, 140, null,
     'cm', array['Chest','Shoulder','Sleeve Length','Total Length'], array[94, 42, 62, 56],
     1, 10, 25, 40),          -- East Austin

    -- Bags & Accessories
    ('[TEST] Tan Leather Crossbody Bag', 'Bags & Accessories', 'One size',
     'Small tan crossbody with an adjustable strap. Fits a phone, keys and a wallet.',
     '$6/day', 30.34, -97.72, null, null,
     'in', array['Width','Height','Depth','Strap Drop'], array[9, 6.5, 3, 22],
     3, 24, null, null),      -- Crestview
    ('[TEST] Gold Beaded Evening Clutch', 'Bags & Accessories', 'One size',
     'Gold beaded clutch with a detachable chain. Pairs well with formal wear.',
     '$5 per event', 30.34, -97.75, null, null,
     'cm', array['Width','Height','Depth'], array[22, 12, 4],
     5, 12, 30, 37);          -- Allandale

  insert into public.listings (id, lender_id, title, category, size_label, description, price_note,
                               city, approx_lat, approx_lng, status, retail_value, deposit_note)
  select s.id, v_lender, s.title, s.category, s.size_label, s.description, s.price_note,
         'Austin', round(s.lat::numeric, 2), round(s.lng::numeric, 2), 'active', s.retail_value, s.deposit_note
  from _seed s;

  insert into public.listing_measurements (listing_id, label, value, unit)
  select s.id, m.label, m.val, s.unit
  from _seed s cross join lateral unnest(s.labels, s.vals) as m(label, val);

  insert into public.availability (listing_id, start_date, end_date)
  select s.id, current_date + s.a1_start, current_date + s.a1_end from _seed s
  union all
  select s.id, current_date + s.a2_start, current_date + s.a2_end from _seed s where s.a2_start is not null;

  raise notice 'Added % test listings for %.', (select count(*) from _seed), v_email;
end $$;

-- =====================================================================
-- CLEANUP: uncomment and run to remove every test listing. Measurements,
-- availability, conversations, messages and rentals for them cascade.
-- =====================================================================
-- delete from public.listings where left(title, 7) = '[TEST] ';
