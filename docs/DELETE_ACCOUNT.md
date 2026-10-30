# Delete account: deploying the `delete-account` Edge Function

**Settings → Account → Delete account** in the app explains what gets deleted and
asks the person to type `DELETE`. The app then calls the Supabase Edge Function
`delete-account`. Until the function is deployed, the app shows "Account deletion
isn't available yet". Google Play requires in-app account deletion, so deploy the
function before you submit a build.

What the function does (source: `supabase/functions/delete-account/index.ts`):

1. It reads the caller's access token and asks Supabase Auth who it belongs to.
   The user id only ever comes from that token, so a caller can only delete their
   own account.
2. It refuses (HTTP 409) while the caller has an **active** rental as renter or
   lender. The app shows: "You have an active rental. In its chat, mark it as
   returned (or that the deal didn't happen), then delete your account."
3. It deletes the caller's photos from the `listing-photos` bucket
   (`<user id>/…`). If that fails, it stops before deleting the account, so no
   public photo URL outlives it.
4. It deletes the auth user with the service role. Every table references the
   user with `ON DELETE CASCADE`, so the profile, studio, listings, chats and
   messages (for both people), rentals, saves, measurements, blocks and reads
   are deleted with it. Reports someone *else* filed about this person stay for
   moderation.
   Accessory declarations (`accessory_declarations`, `listing_declarations`, 008)
   and suggestion events stay, with `user_id` set to null: the declarations are
   the record of what the person signed (see `docs/PRIVACY_POLICY_NOTES.md`,
   flagged for lawyer review).

The service role key stays on Supabase's servers. It is never in the app or in
this repository.

---

## 1. Deploy the function (Supabase dashboard)

1. Go to <https://supabase.com/dashboard> and open the Dzirent project.
2. In the left sidebar, click **Edge Functions**.
3. Click **Deploy a new function**, then choose **Via Editor**.
4. The editor opens with a sample function. Select all of it (Ctrl+A) and
   delete it.
5. In this repository, open `supabase/functions/delete-account/index.ts`. Copy
   the whole file and paste it into the editor.
6. At the bottom, set **Function name** to exactly `delete-account`. The app
   calls it by this name.
7. Click **Deploy function**. Wait for the "Successfully deployed" message. The
   page then shows the function's details and its URL, which ends in
   `/functions/v1/delete-account`.

You don't need to add any secrets. Supabase gives every Edge Function
`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`. To see them, open **Edge
Functions → Secrets**: they are listed under the default secrets.

## 2. Check the JWT setting

1. Open **Edge Functions → delete-account → Details**.
2. Find the JWT verification toggle. Depending on the dashboard version it is
   called **Enforce JWT Verification** or **Verify JWT with legacy secret**.
   - Leave it **on** (the default) if your project uses the legacy JWT secret.
   - If your project uses the newer **JWT signing keys** (**Project Settings →
     JWT Keys**), and the app gets "Your session may have expired" when it
     deletes an account, turn the toggle **off** and click **Save**. That is
     safe: the function verifies the token itself (step 1 above) and refuses
     anything that isn't a valid, signed-in user.

## 3. Test it with a throwaway account

Don't test with a real account: deletion can't be undone.

1. In the app, sign up with a new email address you control.
2. Optionally list a piece with a photo, so you can check the photo cleanup.
3. Go to **Settings → Account → Delete account**, type `DELETE` and tap
   **Delete account**.
4. The app says "Your account was deleted" and returns to the welcome screen.
5. In the dashboard, check that it's gone:
   - **Authentication → Users**: the email is no longer listed.
   - **Table Editor → profiles**: no row with that user's id.
   - **Storage → listing-photos**: no folder named after that user's id.
6. To test the rental block, use two throwaway accounts. Mark a rental as rented
   in their chat, then try to delete either account. The app should show the
   active-rental message. After **Mark as returned**, deletion works.

If something fails, open **Edge Functions → delete-account → Logs**. The function
logs a short reason (for example `photo cleanup failed …`), never a token.

## 4. Google Play: the deletion web link

In Play Console, **Policy and programs → App content → Data safety** asks for a
**Delete account URL**. It must be a web page where people can ask for deletion
without reinstalling the app. A simple page is enough. It should explain the
in-app steps above and offer an email request to the `SUPPORT_EMAIL` set in
`android/app/src/main/java/com/dzirent/app/AppLinks.kt`. Requests that arrive by
email can be handled in **Authentication → Users**: open the user's row menu and
choose **Delete user**. Delete their `listing-photos/<user id>` folder in
**Storage** as well.

## Updating the function later

Open **Edge Functions → delete-account → Code**, paste the new version of
`index.ts`, and click **Deploy updates**.
