# supabase/tools

## US cities for the "City, ST" picker

`build_us_cities.py` downloads the newest US Census Bureau Gazetteer **Places** file
(public domain: cities, towns, villages, boroughs and census-designated places) and writes
`us_cities.csv` with the columns `id, name, state, lat, lng`:

- `id`: Census GEOID, a 7-digit **text** value (leading zeros matter)
- `name`: place name without the Census suffix (`Austin city` becomes `Austin`, `Abanda CDP` becomes `Abanda`)
- `state`: 2-letter USPS code (50 states, DC and PR)
- `lat`, `lng`: interior point rounded to 2 decimals (about 1 km)

The CSV is generated, not committed (it is in `.gitignore`).

### 1. Build the CSV

From the repository root (Python 3.9+, no packages needed):

```
python supabase/tools/build_us_cities.py
```

It prints e.g. `Gazetteer 2026: wrote 32363 places to supabase/tools/us_cities.csv`.
Use `--year 2025` to pin a year.

### 2. Create the table

Run `supabase/003_cities_and_accessories.sql` in Supabase > SQL Editor (safe to re-run).

### 3. Import the CSV

Option A, psql (most reliable; keeps the `id` leading zeros for sure). In Supabase >
Project Settings > Database > Connection string, copy the **Session pooler** URI, then from
the repository root:

```
psql "postgresql://postgres.<project-ref>:<db-password>@<pooler-host>:5432/postgres" -c "\copy public.us_cities (id, name, state, lat, lng) from 'supabase/tools/us_cities.csv' with (format csv, header true)"
```

It prints `COPY 32363` (or similar).

Option B, dashboard: Supabase > Table Editor > `us_cities` > Insert > Import data from CSV,
choose `us_cities.csv`, check that the five columns map to the same names, and import.

### Refreshing the list later

The import fails on duplicate ids, so empty the table first (SQL Editor):

```
truncate public.us_cities;
```

then repeat steps 1 and 3.

### Check

```
select count(*) from public.us_cities;                     -- about 32,000
select * from public.search_cities('springfield', 20);     -- every Springfield, one per state row
```
