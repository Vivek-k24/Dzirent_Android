// Supabase Edge Function: delete-account
//
// Deletes the account of the person who calls it, and nobody else's:
//   1. Reads the caller's access token from the Authorization header and asks Supabase
//      Auth who it belongs to (an invalid or expired token is refused with 401).
//   2. Refuses with 409 while the caller has an active rental (as renter or lender).
//   3. Deletes the caller's listing photos from Storage (listing-photos/<user id>/...).
//      Database cascades can't reach Storage, and the photos have public URLs.
//   4. Deletes the caller's auth user with the service role. Every table references
//      profiles (or auth.users) with ON DELETE CASCADE, so their profile, listings,
//      chats, messages, rentals, saves, measurements and the rest go with it.
//
// The user id only ever comes from the verified token; the request body is ignored.
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase to every Edge
// Function; the service role key never leaves the server. Deploy: docs/DELETE_ACCOUNT.md.

import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const PHOTO_BUCKET = "listing-photos";
const PAGE = 1000;

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json(405, { error: "method_not_allowed", message: "Use POST." });
  }

  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
  if (!token) {
    return json(401, { error: "not_signed_in", message: "Please log in again." });
  }

  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false, autoRefreshToken: false } },
  );

  // 1. Who is calling? getUser checks the token with Supabase Auth.
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  const userId = userData?.user?.id;
  if (userError || !userId) {
    return json(401, { error: "not_signed_in", message: "Please log in again." });
  }

  // 2. No deleting in the middle of a rental.
  const { count, error: rentalError } = await admin
    .from("rentals")
    .select("id", { count: "exact", head: true })
    .eq("status", "active")
    .or(`renter_id.eq.${userId},lender_id.eq.${userId}`);
  if (rentalError) {
    console.error("rental check failed", rentalError.message);
    return json(500, { error: "server_error", message: "Couldn't delete your account. Please try again." });
  }
  if ((count ?? 0) > 0) {
    return json(409, {
      error: "active_rental",
      message:
        "You have an active rental. In its chat, mark it as returned (or that the deal didn't happen), then delete your account.",
    });
  }

  // 3. Photos: <user id>/<listing id>/<photo>.jpg. Stop before deleting the account if
  //    this fails, so no public photo outlives it.
  try {
    const paths: string[] = [];
    const { data: folders, error: listError } = await admin.storage.from(PHOTO_BUCKET).list(userId, { limit: PAGE });
    if (listError) throw listError;
    for (const entry of folders ?? []) {
      if (entry.id) {
        paths.push(`${userId}/${entry.name}`); // a file directly in the user's folder
        continue;
      }
      const folder = `${userId}/${entry.name}`;
      for (let offset = 0; ; offset += PAGE) {
        const { data: files, error } = await admin.storage.from(PHOTO_BUCKET).list(folder, { limit: PAGE, offset });
        if (error) throw error;
        paths.push(...(files ?? []).filter((f) => f.id).map((f) => `${folder}/${f.name}`));
        if (!files || files.length < PAGE) break;
      }
    }
    for (let i = 0; i < paths.length; i += PAGE) {
      const { error } = await admin.storage.from(PHOTO_BUCKET).remove(paths.slice(i, i + PAGE));
      if (error) throw error;
    }
  } catch (e) {
    console.error("photo cleanup failed", e instanceof Error ? e.message : String(e));
    return json(500, { error: "server_error", message: "Couldn't delete your photos. Please try again." });
  }

  // 4. The account itself; the database cascades do the rest.
  const { error: deleteError } = await admin.auth.admin.deleteUser(userId);
  if (deleteError) {
    console.error("deleteUser failed", deleteError.message);
    return json(500, { error: "server_error", message: "Couldn't delete your account. Please try again." });
  }

  return json(200, { ok: true });
});
