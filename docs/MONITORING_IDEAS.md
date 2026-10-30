# Monitoring ideas

Notes for a future operator dashboard. Nothing here is built yet; the data is already being
collected (supabase/008_suggestions_declarations.sql).

## Dashboard: photo suggestions and what can be listed

**Dashboard: suggestion accept/clear rate per label; removals and overrides per tier and label
(e.g. how many people tried to list bikes); listings with ai_review_needed.**

### 1. Suggestion accept / clear rate per label

From `public.suggestion_events` where `field` is `title`, `category`, `description` or `style`.
Each is logged once at Publish, for fields the photos filled: `kept`, `edited` or `cleared`.

```sql
select label, field,
       count(*) filter (where action = 'kept')    as kept,
       count(*) filter (where action = 'edited')  as edited,
       count(*) filter (where action = 'cleared') as cleared,
       round(100.0 * count(*) filter (where action = 'kept') / count(*), 1) as kept_pct
  from public.suggestion_events
 where field in ('title', 'category', 'description', 'style')
 group by label, field
 order by count(*) desc;
```

A label that's often cleared is a sign the model misreads it (or its threshold in
`PhotoThresholds.kt` is too low). "Is it a: …?" chip picks are `field = 'type_chip'`,
`action = 'picked'`: labels picked often from the chips may deserve a lower suggestion cutoff.

### 2. Removals and overrides per tier and label

`field = 'tier'`: every automatic removal (`removed`), every "It's fashion, keep it" /
"That's wrong, keep it" (`kept_override`), and every "This is wrong" on the everyday-basics
warning (`kept_override` with a not-offered label).

```sql
select label,
       count(*) filter (where action = 'removed')       as removed,
       count(*) filter (where action = 'kept_override') as kept_anyway
  from public.suggestion_events
 where field = 'tier'
 group by label
 order by removed desc;
```

This is the demand signal for things Dzirent doesn't list: how many people tried to list bikes,
tents or laptops. A high `kept_anyway` share for a label means the model is removing real
clothing for it, so the removal threshold needs raising (or the label rewording in
`tools/ml/labels.py`).

### 3. Listings to check

```sql
select l.id, l.title, l.category, l.item_type, l.status, l.created_at
  from public.listings l
 where l.ai_review_needed
 order by l.created_at desc;
```

Set by the app when the lender kept a removed photo or overruled a photo warning. The owner
can't clear it; the operator does, after checking, from the SQL editor:
`update public.listings set ai_review_needed = false where id = '…';`

### 4. Accessory declarations (later)

Declarations per week, and accessory listings without a per-listing declaration (should be
none for listings published after 008):

```sql
select l.id, l.title, l.created_at
  from public.listings l
 where l.category = 'Accessories'
   and l.created_at > (select min(created_at) from public.accessory_declarations)
   and not exists (select 1 from public.listing_declarations d where d.listing_id = l.id);
```
