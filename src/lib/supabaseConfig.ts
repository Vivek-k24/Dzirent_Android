/**
 * Supabase Client Configuration for Dzirent
 * 
 * Replace the placeholders below with your Supabase project credentials.
 * Alternatively, supply VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY via .env or settings.
 */

export const SUPABASE_URL = "https://your-project-id.supabase.co";
export const SUPABASE_ANON_KEY = "your-anon-key-here";

export function getEffectiveSupabaseConfig() {
  const envUrl = typeof import.meta !== "undefined" && import.meta.env?.VITE_SUPABASE_URL;
  const envKey = typeof import.meta !== "undefined" && import.meta.env?.VITE_SUPABASE_ANON_KEY;
  const localUrl = typeof localStorage !== "undefined" ? localStorage.getItem("dzirent_supabase_url") : null;
  const localKey = typeof localStorage !== "undefined" ? localStorage.getItem("dzirent_supabase_key") : null;

  const url = (localUrl && localUrl.trim()) || (envUrl && envUrl.trim()) || SUPABASE_URL;
  const anonKey = (localKey && localKey.trim()) || (envKey && envKey.trim()) || SUPABASE_ANON_KEY;

  const isConfigured = Boolean(
    url &&
    anonKey &&
    !url.includes("your-project-id") &&
    !anonKey.includes("your-anon-key-here") &&
    url.startsWith("http")
  );

  return { url, anonKey, isConfigured };
}

export function saveCustomSupabaseConfig(url: string, key: string) {
  if (typeof localStorage !== "undefined") {
    localStorage.setItem("dzirent_supabase_url", url.trim());
    localStorage.setItem("dzirent_supabase_key", key.trim());
  }
}

export function clearCustomSupabaseConfig() {
  if (typeof localStorage !== "undefined") {
    localStorage.removeItem("dzirent_supabase_url");
    localStorage.removeItem("dzirent_supabase_key");
  }
}
