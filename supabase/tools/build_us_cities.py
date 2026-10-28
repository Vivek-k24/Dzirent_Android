"""
Builds supabase/tools/us_cities.csv for the public.us_cities table (see 003_cities_and_accessories.sql).

Source: US Census Bureau Gazetteer "Places" file (public domain). It lists every incorporated
place (city, town, village, borough) and census-designated place (CDP) with its USPS state
code and an interior point. The newest year that is online is used.

Output columns: id, name, state, lat, lng
  id    Census GEOID (7 digits, kept as text so leading zeros survive)
  name  place name without the legal suffix ("Austin city" -> "Austin")
  state 2-letter USPS code
  lat, lng  interior point rounded to 2 decimals (about 1 km), like every location Dzirent saves

Usage (Python 3.9+, standard library only):
  python supabase/tools/build_us_cities.py              # writes supabase/tools/us_cities.csv
  python supabase/tools/build_us_cities.py --year 2025  # a specific Gazetteer year
  python supabase/tools/build_us_cities.py --out somewhere.csv
"""

import argparse
import csv
import datetime
import io
import re
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

URL = "https://www2.census.gov/geo/docs/maps-data/data/gazetteer/{year}_Gazetteer/{year}_Gaz_place_national.zip"
OLDEST_YEAR = 2020

# Legal/statistical suffixes the Census appends to place names, longest first so
# "city and borough" wins over "borough". Matched case-sensitively at the end of the name:
# the suffixes are lowercase (or "CDP"), so real names like "Carson City" stay intact.
SUFFIXES = sorted(
    [
        "city and borough",
        "consolidated government",
        "metropolitan government",
        "metro government",
        "unified government",
        "urban county",
        "zona urbana",
        "corporation",
        "comunidad",
        "municipality",
        "borough",
        "village",
        "city",
        "town",
        "CDP",
    ],
    key=len,
    reverse=True,
)
SUFFIX_RE = re.compile(r"\s+(?:" + "|".join(re.escape(s) for s in SUFFIXES) + r")$")
BALANCE_RE = re.compile(r"\s*\(balance\)$", re.IGNORECASE)


def clean_name(raw: str) -> str:
    """'Indianapolis city (balance)' -> 'Indianapolis', 'Nashville-Davidson metropolitan government (balance)' -> 'Nashville-Davidson'."""
    name = BALANCE_RE.sub("", raw.strip())
    name = SUFFIX_RE.sub("", name)
    return name.strip()


def download(year):
    url = URL.format(year=year)
    try:
        with urllib.request.urlopen(url, timeout=60) as resp:
            if "zip" not in resp.headers.get("Content-Type", ""):
                return None
            return resp.read()
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def read_rows(zip_bytes):
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        member = next(n for n in zf.namelist() if n.lower().endswith(".txt"))
        raw = zf.read(member)
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        text = raw.decode("latin-1")  # older Gazetteer files are Latin-1
    # Tab-delimited through 2025, pipe-delimited from 2026.
    header = text.split("\n", 1)[0]
    delimiter = "|" if "|" in header else "\t"
    reader = csv.DictReader(io.StringIO(text), delimiter=delimiter)
    # The last header ("INTPTLONG") carries trailing spaces in some years.
    reader.fieldnames = [f.strip() for f in reader.fieldnames]
    return list(reader)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--year", type=int, help="Gazetteer year (default: newest available)")
    parser.add_argument("--out", type=Path, default=Path(__file__).with_name("us_cities.csv"))
    args = parser.parse_args()

    years = [args.year] if args.year else range(datetime.date.today().year + 1, OLDEST_YEAR - 1, -1)
    for year in years:
        data = download(year)
        if data:
            break
        print(f"No Gazetteer places file for {year}", file=sys.stderr)
    else:
        sys.exit("Couldn't find a Gazetteer places file. Try --year with a year listed at "
                 "https://www.census.gov/geographies/reference-files/time-series/geo/gazetteer-files.html")

    rows = read_rows(data)
    seen = set()
    out_rows = []
    for row in rows:
        geoid = row["GEOID"].strip()
        state = row["USPS"].strip().upper()
        name = clean_name(row["NAME"])
        if not geoid or geoid in seen or len(state) != 2 or not name:
            continue
        seen.add(geoid)
        lat = round(float(row["INTPTLAT"]), 2)
        lng = round(float(row["INTPTLONG"]), 2)
        out_rows.append((geoid, name, state, f"{lat:.2f}", f"{lng:.2f}"))

    out_rows.sort(key=lambda r: (r[1].lower(), r[2]))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "name", "state", "lat", "lng"])
        writer.writerows(out_rows)

    print(f"Gazetteer {year}: wrote {len(out_rows)} places to {args.out}")


if __name__ == "__main__":
    main()
