# Dzirent — Peer-to-Peer Clothing Rental

Dzirent is a peer-to-peer clothing rental listing app connecting renters and lenders nearby. People list clothes they own; others find them nearby and message the owner directly. The app does NOT handle payments, bookings, shipping, or verification — it connects community members.

## Architecture & Codebase

This repository contains both:
1. **Interactive Material 3 Web App (Vite + React + TypeScript + Tailwind CSS)**:
   - Optimized for live preview in AI Studio running on port 3000.
   - Built to simulate an authentic native Android Google Pixel frame with Material 3 design, bottom navigation, smooth gestures, and light/dark modes.
   - Seamlessly connects to **Supabase** via the official JS SDK with live Realtime chat, Auth, Storage, and Postgrest queries.
2. **Native Android App (`android/`)**:
   - Built with **Kotlin, Jetpack Compose, and Material 3**.
   - Uses the official `supabase-kt` client (Auth, Postgrest, Storage, and Realtime modules).
   - `play-services-location` for Fused Location Provider.
   - `Coil` for image loading from Supabase Storage public URLs.
   - Follows MVVM architecture with StateFlow and Kotlin coroutines.

---

## Supabase Backend Specification

Placeholders for `SUPABASE_URL` and `SUPABASE_ANON_KEY` are located in:
- Web: `src/lib/supabaseConfig.ts` and `.env.example`
- Android: `android/app/src/main/java/com/dzirent/app/data/SupabaseConfig.kt`

### Database Schema
- `profiles(id, display_name, role ['renter'|'lender'|'both'], city, approx_lat, approx_lng, adult_confirmed, created_at)`
- `listings(id, lender_id, title, category, size_label, description, price_note, city, approx_lat, approx_lng, status ['active'|'paused'], created_at)`
- `listing_photos(id, listing_id, storage_path, position)`
- `listing_measurements(id, listing_id, label, value, unit ['cm'|'in'])`
- `availability(id, listing_id, start_date, end_date)`
- `conversations(id, listing_id, renter_id, lender_id, created_at)`
- `messages(id, conversation_id, sender_id, body, created_at)`
- `blocks(blocker_id, blocked_id)`
- `reports(id, reporter_id, target_type ['listing'|'user'|'message'], target_id, reason)`
- `rental_outcomes(conversation_id, user_id, happened, answered_at)`

### Nearby Search RPC
- `listings_nearby(p_lat, p_lng, p_radius_km, p_category)`

### Storage Bucket
- `listing-photos`: Upload path `{userId}/{listingId}/{uuid}.jpg` (max 1600px long side, compressed to JPEG ~80%).

### Fixed Categories
1. Career & Professional
2. Occasion & Party
3. Cultural & Traditional
4. Everyday
5. Outerwear
6. Bags & Accessories

---

## Supported Flows
1. **Welcome**: One-line pitch "Rent outfits from closets near you", Sign up and Log in.
2. **Sign up**: Display name, email, password, role selection (Renter, Lender, Both), 18+ adult confirmation checkbox, location permission prompt.
3. **Log in**: Credentials authentication + password reset email via Supabase.
4. **Location**: Fused location provider with coordinates rounded to 2 decimals (~1 km) before saving for privacy; reverse geocoding city; manual city fallback.
5. **Browse**: Search bar (matches title & description), horizontal category chips, distance selector (2, 5, 10, 25 km), 2-column grid cards, pull-to-refresh, 20-item pagination.
6. **Listing detail**: Swipeable photo carousel, title, size, price note, description, measurements table, availability calendar, lender info, "Message lender" button, "Report listing" menu.
7. **Create/Edit listing**: 1-6 photos from camera or gallery with reorder/delete, title, category, size label, description, price note, measurements dynamic list with category suggestions (e.g. Shoulder, Chest, Sleeve, Length, Waist), availability date ranges with date picker.
8. **My Closet**: Lender's own listings with Active/Paused toggle, and Delete with storage photo cleanup confirmation.
9. **Inbox**: Conversations sorted by latest message with thumbnail, other person's name, and snippet.
10. **Chat**: Real-time Supabase messaging, sent/received bubbles, timestamps, pinned listing, quick chips ("Is this available on these dates?", "Can I see more photos?"), overflow menu ("Block user", "Report"), safety tip banner.
11. **Rental follow-up**: 3 days after a conversation starts, prompts "Did this rental happen? Yes / No", saving to `rental_outcomes`.
12. **Profile**: Display name, city, role switch, location update, Supabase settings, and sign out.
