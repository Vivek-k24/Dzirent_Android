# Google Play internal testing for Dzirent Android

Every run of the **Build and publish signed Android release (APK + AAB)**
workflow builds a signed app bundle named after its branch, for example
`Dzirent-android04-release.aab`. It attaches the bundle to the GitHub Release
next to the APK. Once `PLAY_SERVICE_ACCOUNT_JSON` is set, the workflow also
uploads the bundle to the Play **Internal testing** track. Without that secret,
the upload step is skipped and the build still passes.

The workflow runs on pushes to any branch whose name starts with `android`
(`android03`, `android04`, …) that touch `android/**` or the workflow file. It
also runs from **Actions → Run workflow**. Builds from every such branch go to
the same internal track. Each build's `versionCode` is the workflow run number,
so each upload is higher than the one before. Builds run one at a time.

Do the parts in this order. Play's API can only upload to an app that already
has a first build uploaded by hand, and you can only grant a service account
access to an app that exists.

1. [Create the app and do the first upload by hand](#1-create-the-app-and-do-the-first-upload-by-hand)
2. [Create the service account in Google Cloud](#2-create-the-service-account-in-google-cloud)
3. [Grant the service account release access in Play Console](#3-grant-the-service-account-release-access-in-play-console)
4. [Add the GitHub secret](#4-add-the-github-secret)
5. [Check the first automatic upload](#5-check-the-first-automatic-upload)

Before you start, you need:

- A Google Play developer account (one-time fee) with your identity
  verification finished.
- One green run of the workflow, so a GitHub Release has a `.aab` file.
- The permanent signing secrets (`ANDROID_RELEASE_*`) already set up, as
  described in [ANDROID_DEPLOYMENT.md](../ANDROID_DEPLOYMENT.md). Keep that
  keystore safe: on Google Play it becomes your **upload key**.

---

## 1. Create the app and do the first upload by hand

### 1a. Get the bundle

1. On GitHub, open **Vivek-k24/Dzirent_Android → Releases**.
2. Open the newest `<branch>-build-N` release, for example `android04-build-N`.
3. Under **Assets**, click **Dzirent-<branch>-release.aab** to download it.

### 1b. Create the app

1. Go to <https://play.google.com/console> and sign in with the developer account.
2. On **Home**, click **Create app**.
3. Fill in the form:
   - **App name**: `Dzirent`
   - **Default language**: English (United States) – en-US
   - **App or game**: App
   - **Free or paid**: Free
4. Tick both **Declarations** boxes (Developer Program Policies, US export laws).
5. Click **Create app**.

### 1c. Add internal testers

1. In the left menu, open **Test and release → Testing → Internal testing**.
2. Open the **Testers** tab.
3. Click **Create email list**.
4. Enter a **List name**, for example `Dzirent testers`.
5. Add each tester's Google account email, pressing Enter after each one.
   You can add up to 100 testers.
6. Click **Save changes**.
7. Back on the **Testers** tab, tick the new list.
8. Click **Save**.

### 1d. Create the first release

1. Still on **Internal testing**, click **Create new release** (top right).
2. Play asks how the app is signed (Play App Signing). Choose one:
   - **Use Google-generated key** (the default; recommended). Google holds
     the key that signs what users install. Your keystore only signs uploads.
     If your keystore is lost or leaked, Play support can reset the upload key.
     **Trade-off:** apps installed from Play are signed differently from the
     APKs on GitHub Releases. A phone that has the GitHub APK must uninstall
     it before installing from Play, and the reverse is also true.
     Uninstalling deletes the app's local data. The Supabase account and
     listings are not affected.
   - **Use my own key → Export and upload a key from Java keystore**. Follow
     Play's on-screen PEPK steps with the same `dzirent-release.jks`. Play
     then signs with your key, so GitHub APKs and Play installs can update
     each other. If that keystore is ever lost, you can never update the app
     again.

   Click **Next** or **Save** (the wording depends on the option).
3. Under **App bundles**, click **Upload**. Choose
   the `.aab` from step 1a.
4. Wait for the upload to finish. The bundle row shows the version code
   (the build number N) and version name `1.0.0`.
5. **Release name** is filled in automatically, for example `N (1.0.0)`.
   Leave it as it is.
6. Under **Release notes**, replace the placeholder text with a short note,
   for example `First internal test build.` (keep the `<en-US>` tags).
7. Click **Next**.
8. Read the summary page. Errors block the rollout; warnings do not.
   - For each error, click its link, fix it, and come back to
     **Internal testing → Edit release**.
   - Errors are usually **App content** items under **Policy and programs**,
     such as Privacy policy, App access or Data safety.
9. Click **Save and publish**. If the button reads **Start rollout to
   Internal testing**, click it, then click **Rollout** to confirm.

Internal testing releases skip review. The build usually reaches testers
within minutes.

### 1e. Install on a test phone

1. Go back to **Internal testing → Testers**.
2. Under **How testers join your test**, click **Copy link**.
3. Send the link to the testers.
4. Each tester opens the link on their Android phone, signed in with an email
   from the list.
5. They tap **Accept invite**, then **Download it on Google Play**.

---

## 2. Create the service account in Google Cloud

The service account is a robot Google account. GitHub Actions uses it to
upload to Play.

### 2a. Create a project

1. Go to <https://console.cloud.google.com> and sign in, ideally with the same
   Google account as the Play developer account.
2. Click the project picker in the top bar (next to "Google Cloud").
3. Click **New project**.
4. Set **Project name** to `dzirent-play`. Leave **Location** as it is.
5. Click **Create**.
6. When it is created, select **dzirent-play** in the project picker.

### 2b. Enable the Play API

1. Open the menu (☰) → **APIs & Services → Library**.
2. Search for **Google Play Android Developer API** and click it.
3. Click **Enable**.

### 2c. Create the service account

1. Open the menu (☰) → **IAM & Admin → Service Accounts**.
2. Click **+ Create service account**.
3. Fill in the form:
   - **Service account name**: `github-play-upload`
   - **Service account ID**: filled in for you.
4. Click **Create and continue**.
5. Under **Permissions (optional)**, add no role and click **Continue**.
   Play access is granted in Play Console, not here.
6. Under **Principals with access (optional)**, leave it empty and click
   **Done**.
7. In the list, copy the account's **Email**. It looks like
   `github-play-upload@dzirent-play.iam.gserviceaccount.com`. You need it in
   step 3.

### 2d. Create its JSON key

1. Click the service account's email to open it.
2. Open the **Keys** tab.
3. Click **Add key → Create new key**.
4. Choose **JSON**, then click **Create**.

Your browser downloads a file like `dzirent-play-1a2b3c4d5e6f.json`. That file
is a password. Do not commit it, email it or paste it into chat. You will
paste it into GitHub in step 4, then delete it.

If Google Cloud shows **"Service account key creation is disabled"**, an
organization policy is blocking key creation. That happens with Google
Workspace accounts. Use a personal Google account's project, or ask the
Workspace admin to allow keys for this project
(`iam.disableServiceAccountKeyCreation`).

---

## 3. Grant the service account release access in Play Console

1. Go to <https://play.google.com/console>. Open the account level: click the
   Play Console logo, or **All apps**, so that no app is selected.
2. In the left menu, click **Users and permissions**.
3. Click **Invite new users**.
4. In **Email address**, paste the service account email from step 2c.
5. Open the **App permissions** tab.
6. Click **Add app**, tick **Dzirent**, and click **Apply**.
7. In the permissions panel that opens, tick only these:
   - **Releases → Release apps to testing tracks**
   - **App access → View app information (read-only)**. Play may tick
     this one for you.

   Leave everything else unticked. In particular, leave **Release to
   production** unticked, so the robot can never publish to the public.
8. Click **Apply**.
9. Click **Invite user**, then confirm with **Send invite**.

The service account does not need to accept the invite. Permissions usually
work within minutes. Sometimes they take up to 24 hours: until then, uploads
fail with "The caller does not have permission".

---

## 4. Add the GitHub secret

1. Open the downloaded JSON key file from step 2d in a plain text editor, for
   example Notepad.
2. Select all (Ctrl+A) and copy (Ctrl+C). Include the opening `{` and the
   closing `}`.
3. On GitHub, open **Vivek-k24/Dzirent_Android → Settings → Secrets and
   variables → Actions**.
4. On the **Secrets** tab, click **New repository secret**.
5. Set **Name** to `PLAY_SERVICE_ACCOUNT_JSON`.
6. Paste the whole file into **Secret**.
7. Click **Add secret**.
8. Delete the JSON file from your computer and empty the Recycle Bin. If you
   ever need a new key, create one in step 2d and delete the old one there.

---

## 5. Check the first automatic upload

1. Start a build: push a change under `android/` to an `android*` branch, or go to
   **Actions → Build and publish signed Android release (APK + AAB) → Run
   workflow**.
   - That build's number must be higher than the one you uploaded by hand.
     Any new run's number is.
   - Don't re-run an old run: a re-run keeps its number, and Play rejects a
     versionCode it already has.
2. When the run is green, open it.
   - **Upload app bundle to Google Play internal testing** should show a
     green check, not a skipped icon.
   - If it was skipped, the secret's name is wrong or the secret is empty.
3. In Play Console, open **Dzirent → Test and release → Testing → Internal
   testing**. The newest release should show the new version code.
4. On a test phone, open the Play Store → profile icon → **Manage apps &
   device → Updates available** and update Dzirent. Updates can take a few
   minutes to appear.

### Troubleshooting

| Message in the workflow log | Fix |
| --- | --- |
| `Only releases with status draft may be created on draft app.` | The app has not been rolled out yet. Finish step 1d (click **Start rollout**). Or set **Settings → Secrets and variables → Actions → Variables → New repository variable** with `PLAY_RELEASE_STATUS` = `draft`, then roll out each upload by hand in Play Console. Delete the variable once rollout works. |
| `The caller does not have permission` | Step 3 is missing, or the permissions are still taking effect (up to 24 hours). Check that the email under **Users and permissions** matches the `client_email` in the JSON. |
| `Package not found: com.dzirent.app` | Play has no hand-uploaded build yet. Do step 1. |
| `Version code N has already been used.` | That build number was already uploaded, either by hand or by a re-run. Start a new run. |
| `Google Play Android Developer API has not been used in project … or it is disabled` | Do step 2b in the project that owns the service account. |
| `The Android App Bundle was signed with the wrong key` | The `ANDROID_RELEASE_*` secrets changed to a different keystore. Restore the original. If it is lost, Play App Signing users can request an upload key reset under **Test and release → App integrity**. |

### Notes

- **Play target API:** Play requires new apps and updates to target a recent
  Android version. The app now targets API 36 (Android 16).
- **New personal developer accounts:** Play requires a closed test with at
  least 12 testers for 14 days before the first production release. Internal
  testing does not count toward this and has no such requirement.
- **Version codes:** they come from `GITHUB_RUN_NUMBER`, which belongs to this
  workflow. If the workflow file is renamed or recreated, numbering restarts
  at 1 and Play rejects the uploads. Then add a fixed offset to
  `appVersionCode` in `android/app/build.gradle.kts`.
- **Local builds:** these use versionCode 1, so they can never be uploaded
  over a CI build by mistake.
