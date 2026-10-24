# Dzirent Android — GitHub + Supabase Deployment

Dzirent is a **native Android application**; the APK runs on the phone. GitHub
stores its source and publishes APK releases. Supabase hosts the remote services
(Auth, PostgreSQL/PostgREST, Storage, and Realtime). **GitHub Pages does not host
a native Android backend**, and no extra FastAPI server is needed for the current
Supabase SDK integration.

## Before building an installable, connected APK

1. In the intended Supabase project, open **Connect** or **Project Settings > API**.
   Copy the exact **Project URL**, ending in `.supabase.co`.
2. Copy the **publishable key** (or legacy public `anon` key). Never use a
   `service_role` or `sb_secret_` key in an Android app; APK contents can be inspected.
3. In GitHub, open
   `Vivek-k24/Dzirent_Android` → **Settings → Secrets and variables → Actions**.
   Add the following **repository secrets**:

   | Secret | Contents |
   | --- | --- |
   | `SUPABASE_URL` | Exact `https://project-ref.supabase.co` URL |
   | `SUPABASE_ANON_KEY` | Supabase publishable or legacy anon key |
   | `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64-encoded permanent release .jks keystore |
   | `ANDROID_RELEASE_STORE_PASSWORD` | Keystore password |
   | `ANDROID_RELEASE_KEY_ALIAS` | Keystore key alias |
   | `ANDROID_RELEASE_KEY_PASSWORD` | Private key password |

4. Before distributing, verify **email authentication**, the schema/migrations
   under `supabase/`, **Row Level Security** policies, relevant PostgREST RPCs,
   the `listing-photos` Storage bucket and access policies, and Realtime for
   messages. Commiting SQL files does **not** deploy them to Supabase.
5. In GitHub **Actions → Build and publish signed Android release APK → Run
   workflow**, select the `android03` branch.
6. When the workflow is green, download the release APK from **Releases**,
   install it, and test sign-up/login, listing browse/create, photo upload, and
   sending and receiving messages on actual devices.

## Permanent signing (required for future updates)

Create a keystore on a trusted local computer, and retain it securely offline.
For example, with the JDK `keytool` utility:

```sh
keytool -genkeypair -v -keystore dzirent-release.jks \
  -alias dzirent-release -keyalg RSA -keysize 3072 \
  -validity 10000
```

The tool asks for passwords and certificate identity details. Do not commit the
`.jks` file or passwords to this public repository.

Encode it for the GitHub secret:

- macOS: `base64 < dzirent-release.jks | tr -d '\\n'`
- Linux: `base64 -w0 dzirent-release.jks`

Store that output in `ANDROID_RELEASE_KEYSTORE_BASE64`; store the passwords and
alias in the corresponding secrets. Keep the original keystore backed up;
without the same certificate, Android cannot install a new APK as an update.

**Important:** The October 24, 2026 `android03-build-5` release was signed
using an ephemeral certificate. A first APK signed with your new permanent
certificate will have a different signature, so existing testers must
uninstall the older APK before installing the new one. Uninstalling deletes
that app's local data.

## Diagnosing "Can't reach the server"

The previous release was compiled with placeholder values because its GitHub
Actions Supabase secrets were empty. It cannot reach a real Supabase project.
A successful Gradle compile and APK signature are **not** backend connectivity
tests.

The publishing workflow now **fails safely** if Supabase configuration or
persistent release signing is missing. A failed build at the prerequisite
stage is expected until you finish this setup, and prevents another unusable
APK from being published.
