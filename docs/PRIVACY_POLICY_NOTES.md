# Privacy policy: draft notes

Working notes for whoever writes the privacy policy (the app links to `AppLinks.PRIVACY_URL`).
**Not legal advice. Items marked "Lawyer review" need a lawyer's sign-off before launch.**

## Photos and item recognition (on the phone)

- Listing photos are checked on the phone (sharpness, light, framing) and the item is recognized
  on the phone by a small open-source model (TinyCLIP, MIT). No photo is sent anywhere for
  recognition; no paid AI or LLM is used.
- Photos are uploaded only when the lender publishes. Until then the app keeps its own copies
  in the app's private storage (per user) so a draft survives a restart; they're deleted after
  publishing or "Start over".
- The phone may remove a photo from the form if it looks like something Dzirent doesn't list.
  If the lender keeps it anyway, the listing is flagged (`ai_review_needed`) for a person at
  Dzirent to look at.

## Suggestion events (008)

- Table `suggestion_events`: which suggestion a lender kept, edited or cleared, which "Is it a"
  chip they picked, and photos removed or kept anyway (with the label, e.g. "bicycle").
- Linked to the user id and, at publish, the listing id. No photo, no text the user typed.
- Used to improve the suggestions and to see demand for things Dzirent doesn't list.
- On account deletion: user id and listing id are set to null; the anonymous counts stay.

## Accessory declarations (008)

What's recorded when a lender signs the accessory declaration (once, before listing jewelry or
accessories):

- user id, email, the typed full legal name, the declaration version, the app version,
- the city from their profile (never precise GPS),
- the IP address and user agent of the request, and the login session id,
- the time.

Each accessory listing also records a per-listing tick (listing id, user id, version, IP,
session id, time).

Purpose: evidence that the lender declared the item isn't fine jewelry or precious metal and is
worth under $300, in case of a dispute, a claim or a legal request.

**Lawyer review: declarations are kept after account deletion.** When an account is deleted
(Settings > Account > Delete account), `accessory_declarations` and `listing_declarations` rows
are *not* deleted: their `user_id` (and `listing_id`) become null, and the email, full legal
name, city, IP, user agent and session id stay. This is deliberate (the record has to outlive
the account to be useful), but it means:

- The privacy policy must say these records are kept after deletion, why, and for how long.
- A retention period should be chosen (none is enforced in the database yet).
- Check this against deletion and access rights (GDPR art. 17 / CCPA / Play's account deletion
  policy, which asks apps to say what data is kept after deletion and why).
- The in-app "Delete account" text now ends with a placeholder line, "Accessory declarations you
  signed are kept as a record, without your account." (SettingsScreen.kt), and
  `docs/DELETE_ACCOUNT.md` says the same. Both need the final wording.

## Other things to mention

- Users can read their own declarations (by RLS) but can't change or delete them.
- Chat now recommends washing anything worn against the skin before wearing.
