import { createClient, SupabaseClient } from "@supabase/supabase-js";
import { getEffectiveSupabaseConfig } from "./supabaseConfig";
import {
  Profile,
  Listing,
  ListingPhoto,
  ListingMeasurement,
  Availability,
  Conversation,
  Message,
  Report,
  RentalOutcome,
  UserRole,
} from "../types/database";
import { calculateDistanceKm, roundToTwoDecimals } from "./locationUtils";
import { generateUUID } from "./imageUtils";

// Initialize client if valid credentials exist
let supabaseInstance: SupabaseClient | null = null;
let currentConfig = getEffectiveSupabaseConfig();

export function getSupabase(): SupabaseClient | null {
  const latestConfig = getEffectiveSupabaseConfig();
  if (!latestConfig.isConfigured) {
    supabaseInstance = null;
    return null;
  }

  if (
    !supabaseInstance ||
    latestConfig.url !== currentConfig.url ||
    latestConfig.anonKey !== currentConfig.anonKey
  ) {
    currentConfig = latestConfig;
    try {
      supabaseInstance = createClient(latestConfig.url, latestConfig.anonKey, {
        auth: {
          persistSession: true,
          autoRefreshToken: true,
        },
      });
    } catch (e) {
      console.warn("Failed to initialize Supabase client:", e);
      supabaseInstance = null;
    }
  }

  return supabaseInstance;
}

// ---------------------------------------------------------------------
// Local persistence engine for offline / placeholder testing
// (Empty by default: NO mock or seed data anywhere!)
// ---------------------------------------------------------------------
const STORAGE_PREFIX = "dzirent_db_";

function getLocalTable<T>(tableName: string): T[] {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + tableName);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function setLocalTable<T>(tableName: string, items: T[]): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + tableName, JSON.stringify(items));
  } catch (e) {
    console.error("Storage quota exceeded or error:", e);
  }
}

// Auth State Helper
export interface AuthUser {
  id: string;
  email: string;
  profile: Profile;
}

let activeUser: AuthUser | null = null;
const AUTH_USER_KEY = "dzirent_active_user";

export function getActiveUser(): AuthUser | null {
  if (activeUser) return activeUser;
  try {
    const raw = localStorage.getItem(AUTH_USER_KEY);
    if (raw) {
      activeUser = JSON.parse(raw);
    }
  } catch {
    activeUser = null;
  }
  return activeUser;
}

export function setActiveUser(user: AuthUser | null) {
  activeUser = user;
  if (user) {
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
  } else {
    localStorage.removeItem(AUTH_USER_KEY);
  }
}

// ---------------------------------------------------------------------
// API Service Methods
// ---------------------------------------------------------------------

export const DzirentService = {
  // --- AUTH ---
  async signUp(params: {
    email: string;
    password: string;
    displayName: string;
    role: UserRole;
    adultConfirmed: boolean;
    city: string;
    lat: number;
    lng: number;
  }): Promise<{ user: AuthUser; error?: string }> {
    const supabase = getSupabase();
    const cleanLat = roundToTwoDecimals(params.lat);
    const cleanLng = roundToTwoDecimals(params.lng);

    if (supabase) {
      const { data, error } = await supabase.auth.signUp({
        email: params.email,
        password: params.password,
        options: {
          data: {
            display_name: params.displayName,
            role: params.role,
            adult_confirmed: params.adultConfirmed,
          },
        },
      });

      if (error) {
        return { user: null as unknown as AuthUser, error: error.message };
      }

      const userId = data.user?.id || generateUUID();
      const profile: Profile = {
        id: userId,
        display_name: params.displayName,
        role: params.role,
        city: params.city,
        approx_lat: cleanLat,
        approx_lng: cleanLng,
        adult_confirmed: params.adultConfirmed,
        created_at: new Date().toISOString(),
      };

      // Upsert profile in Supabase
      const { error: profileError } = await supabase
        .from("profiles")
        .upsert(profile);

      if (profileError) {
        console.warn("Error upserting profile in Supabase:", profileError);
      }

      const authUser: AuthUser = {
        id: userId,
        email: params.email,
        profile,
      };
      setActiveUser(authUser);
      return { user: authUser };
    }

    // Local in-memory / local storage mode
    const userId = generateUUID();
    const profile: Profile = {
      id: userId,
      display_name: params.displayName,
      role: params.role,
      city: params.city,
      approx_lat: cleanLat,
      approx_lng: cleanLng,
      adult_confirmed: params.adultConfirmed,
      created_at: new Date().toISOString(),
    };

    const profiles = getLocalTable<Profile>("profiles");
    profiles.push(profile);
    setLocalTable("profiles", profiles);

    const authUser: AuthUser = {
      id: userId,
      email: params.email,
      profile,
    };
    setActiveUser(authUser);
    return { user: authUser };
  },

  async signIn(email: string, password: string): Promise<{ user: AuthUser; error?: string }> {
    const supabase = getSupabase();

    if (supabase) {
      const { data, error } = await supabase.auth.signInWithPassword({
        email,
        password,
      });

      if (error) {
        return { user: null as unknown as AuthUser, error: error.message };
      }

      if (!data.user) {
        return { user: null as unknown as AuthUser, error: "User not found" };
      }

      // Fetch profile
      const { data: profileData, error: profileErr } = await supabase
        .from("profiles")
        .select("*")
        .eq("id", data.user.id)
        .single();

      let profile: Profile;
      if (profileErr || !profileData) {
        profile = {
          id: data.user.id,
          display_name: data.user.user_metadata?.display_name || email.split("@")[0],
          role: data.user.user_metadata?.role || "renter",
          city: "Nearby City",
          approx_lat: 37.77,
          approx_lng: -122.42,
          adult_confirmed: Boolean(data.user.user_metadata?.adult_confirmed),
          created_at: new Date().toISOString(),
        };
      } else {
        profile = profileData as Profile;
      }

      const authUser: AuthUser = {
        id: data.user.id,
        email,
        profile,
      };
      setActiveUser(authUser);
      return { user: authUser };
    }

    // Local mode
    const profiles = getLocalTable<Profile>("profiles");
    const existing = profiles.find((p) => p.display_name.toLowerCase() === email.split("@")[0].toLowerCase());
    
    const userId = existing ? existing.id : generateUUID();
    const profile: Profile = existing || {
      id: userId,
      display_name: email.split("@")[0],
      role: "both",
      city: "Current Location",
      approx_lat: 37.77,
      approx_lng: -122.42,
      adult_confirmed: true,
      created_at: new Date().toISOString(),
    };

    if (!existing) {
      profiles.push(profile);
      setLocalTable("profiles", profiles);
    }

    const authUser: AuthUser = {
      id: userId,
      email,
      profile,
    };
    setActiveUser(authUser);
    return { user: authUser };
  },

  async signOut(): Promise<void> {
    const supabase = getSupabase();
    if (supabase) {
      await supabase.auth.signOut();
    }
    setActiveUser(null);
  },

  async resetPassword(email: string): Promise<{ success: boolean; error?: string }> {
    const supabase = getSupabase();
    if (supabase) {
      const { error } = await supabase.auth.resetPasswordForEmail(email);
      if (error) return { success: false, error: error.message };
      return { success: true };
    }
    return { success: true };
  },

  async updateProfile(userId: string, data: Partial<Profile>): Promise<Profile> {
    const supabase = getSupabase();
    if (supabase) {
      const { data: updated, error } = await supabase
        .from("profiles")
        .update(data)
        .eq("id", userId)
        .select()
        .single();
      if (error) console.error("Error updating profile:", error);
      if (updated) {
        const user = getActiveUser();
        if (user && user.id === userId) {
          user.profile = updated as Profile;
          setActiveUser(user);
        }
        return updated as Profile;
      }
    }

    // Local update
    const profiles = getLocalTable<Profile>("profiles");
    const idx = profiles.findIndex((p) => p.id === userId);
    if (idx !== -1) {
      profiles[idx] = { ...profiles[idx], ...data };
      setLocalTable("profiles", profiles);
      const user = getActiveUser();
      if (user && user.id === userId) {
        user.profile = profiles[idx];
        setActiveUser(user);
      }
      return profiles[idx];
    }
    throw new Error("Profile not found");
  },

  // --- LISTINGS & NEARBY SEARCH ---
  async getListings(params: {
    category?: string;
    radiusKm?: number;
    searchQuery?: string;
    userLat?: number;
    userLng?: number;
    limit?: number;
    offset?: number;
  }): Promise<{ listings: Listing[]; hasMore: boolean }> {
    const supabase = getSupabase();
    const limit = params.limit ?? 20;
    const offset = params.offset ?? 0;

    // Supabase RPC or Query
    if (supabase) {
      try {
        let baseQuery = supabase
          .from("listings")
          .select(`
            *,
            photos:listing_photos(*),
            measurements:listing_measurements(*),
            availability:availability(*),
            lender:profiles!lender_id(*)
          `)
          .eq("status", "active")
          .order("created_at", { ascending: false });

        if (params.category) {
          baseQuery = baseQuery.eq("category", params.category);
        }

        if (params.searchQuery && params.searchQuery.trim()) {
          const q = params.searchQuery.trim();
          baseQuery = baseQuery.or(`title.ilike.%${q}%,description.ilike.%${q}%`);
        }

        const { data, error } = await baseQuery.range(offset, offset + limit);

        if (error) {
          console.warn("Error fetching listings from Supabase, attempting RPC:", error);
          // Try RPC function listings_nearby
          if (params.userLat !== undefined && params.userLng !== undefined) {
            const { data: rpcData, error: rpcErr } = await supabase.rpc(
              "listings_nearby",
              {
                p_lat: params.userLat,
                p_lng: params.userLng,
                p_radius_km: params.radiusKm || 25,
                p_category: params.category || null,
              }
            );
            if (!rpcErr && rpcData) {
              return {
                listings: rpcData as Listing[],
                hasMore: false,
              };
            }
          }
          throw error;
        }

        const rawListings = (data || []) as Listing[];

        // Calculate distance if coordinates provided
        const computed = rawListings.map((l) => {
          let dist: number | undefined;
          if (params.userLat !== undefined && params.userLng !== undefined) {
            dist = calculateDistanceKm(params.userLat, params.userLng, l.approx_lat, l.approx_lng);
          }
          return {
            ...l,
            photos: (l.photos || []).sort((a, b) => a.position - b.position),
            distance_km: dist,
          };
        });

        // Filter by radius if requested
        const filtered = params.radiusKm
          ? computed.filter((l) => l.distance_km === undefined || l.distance_km <= params.radiusKm!)
          : computed;

        return {
          listings: filtered,
          hasMore: rawListings.length > limit,
        };
      } catch (err) {
        console.warn("Supabase query failed, falling back to local store:", err);
      }
    }

    // Local Store Implementation
    const listings = getLocalTable<Listing>("listings");
    const photos = getLocalTable<ListingPhoto>("listing_photos");
    const measurements = getLocalTable<ListingMeasurement>("listing_measurements");
    const availabilities = getLocalTable<Availability>("availability");
    const profiles = getLocalTable<Profile>("profiles");
    const blocks = getLocalTable<{ blocker_id: string; blocked_id: string }>("blocks");

    const currentUserId = getActiveUser()?.id;
    const blockedIds = new Set(
      blocks.filter((b) => b.blocker_id === currentUserId).map((b) => b.blocked_id)
    );

    let filtered = listings.filter((l) => l.status === "active" && !blockedIds.has(l.lender_id));

    if (params.category) {
      filtered = filtered.filter((l) => l.category === params.category);
    }

    if (params.searchQuery && params.searchQuery.trim()) {
      const q = params.searchQuery.toLowerCase().trim();
      filtered = filtered.filter(
        (l) => l.title.toLowerCase().includes(q) || l.description.toLowerCase().includes(q)
      );
    }

    const hydrated: Listing[] = filtered.map((l) => {
      let dist: number | undefined;
      if (params.userLat !== undefined && params.userLng !== undefined) {
        dist = calculateDistanceKm(params.userLat, params.userLng, l.approx_lat, l.approx_lng);
      }
      return {
        ...l,
        photos: photos.filter((p) => p.listing_id === l.id).sort((a, b) => a.position - b.position),
        measurements: measurements.filter((m) => m.listing_id === l.id),
        availability: availabilities.filter((a) => a.listing_id === l.id),
        lender: profiles.find((p) => p.id === l.lender_id),
        distance_km: dist,
      };
    });

    const radiusFiltered = params.radiusKm
      ? hydrated.filter((l) => l.distance_km === undefined || l.distance_km <= params.radiusKm!)
      : hydrated;

    // Sort by newest
    radiusFiltered.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());

    const page = radiusFiltered.slice(offset, offset + limit);

    return {
      listings: page,
      hasMore: offset + limit < radiusFiltered.length,
    };
  },

  async getListingDetail(id: string): Promise<Listing | null> {
    const supabase = getSupabase();

    if (supabase) {
      try {
        const { data, error } = await supabase
          .from("listings")
          .select(`
            *,
            photos:listing_photos(*),
            measurements:listing_measurements(*),
            availability:availability(*),
            lender:profiles!lender_id(*)
          `)
          .eq("id", id)
          .single();

        if (!error && data) {
          const l = data as Listing;
          return {
            ...l,
            photos: (l.photos || []).sort((a, b) => a.position - b.position),
          };
        }
      } catch (e) {
        console.warn("Failed to fetch listing detail from Supabase:", e);
      }
    }

    // Local fallback
    const listings = getLocalTable<Listing>("listings");
    const match = listings.find((l) => l.id === id);
    if (!match) return null;

    const photos = getLocalTable<ListingPhoto>("listing_photos")
      .filter((p) => p.listing_id === id)
      .sort((a, b) => a.position - b.position);
    const measurements = getLocalTable<ListingMeasurement>("listing_measurements").filter(
      (m) => m.listing_id === id
    );
    const availabilities = getLocalTable<Availability>("availability").filter(
      (a) => a.listing_id === id
    );
    const profiles = getLocalTable<Profile>("profiles");
    const lender = profiles.find((p) => p.id === match.lender_id);

    return {
      ...match,
      photos,
      measurements,
      availability: availabilities,
      lender,
    };
  },

  async getMyListings(lenderId: string): Promise<Listing[]> {
    const supabase = getSupabase();

    if (supabase) {
      try {
        const { data, error } = await supabase
          .from("listings")
          .select(`
            *,
            photos:listing_photos(*),
            measurements:listing_measurements(*),
            availability:availability(*)
          `)
          .eq("lender_id", lenderId)
          .order("created_at", { ascending: false });

        if (!error && data) {
          return (data as Listing[]).map((l) => ({
            ...l,
            photos: (l.photos || []).sort((a, b) => a.position - b.position),
          }));
        }
      } catch (e) {
        console.warn("Failed to get my listings from Supabase:", e);
      }
    }

    // Local
    const listings = getLocalTable<Listing>("listings").filter((l) => l.lender_id === lenderId);
    const photos = getLocalTable<ListingPhoto>("listing_photos");
    const measurements = getLocalTable<ListingMeasurement>("listing_measurements");
    const availabilities = getLocalTable<Availability>("availability");

    return listings
      .map((l) => ({
        ...l,
        photos: photos.filter((p) => p.listing_id === l.id).sort((a, b) => a.position - b.position),
        measurements: measurements.filter((m) => m.listing_id === l.id),
        availability: availabilities.filter((a) => a.listing_id === l.id),
      }))
      .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
  },

  async createListing(params: {
    lenderId: string;
    title: string;
    category: string;
    sizeLabel: string;
    description: string;
    priceNote: string;
    city: string;
    lat: number;
    lng: number;
    photos: { blob?: Blob; dataUrl: string }[];
    measurements: { label: string; value: number; unit: "cm" | "in" }[];
    availabilities: { startDate: string; endDate: string }[];
  }): Promise<Listing> {
    const supabase = getSupabase();
    const listingId = generateUUID();
    const cleanLat = roundToTwoDecimals(params.lat);
    const cleanLng = roundToTwoDecimals(params.lng);

    const newListing: Listing = {
      id: listingId,
      lender_id: params.lenderId,
      title: params.title.trim(),
      category: params.category,
      size_label: params.sizeLabel.trim(),
      description: params.description.trim(),
      price_note: params.priceNote.trim(),
      city: params.city,
      approx_lat: cleanLat,
      approx_lng: cleanLng,
      status: "active",
      created_at: new Date().toISOString(),
    };

    const uploadedPhotos: ListingPhoto[] = [];

    // Upload photos
    for (let i = 0; i < params.photos.length; i++) {
      const item = params.photos[i];
      const photoId = generateUUID();
      const storagePath = `${params.lenderId}/${listingId}/${generateUUID()}.jpg`;

      let finalPath = item.dataUrl;

      if (supabase && item.blob) {
        try {
          const { error: uploadErr } = await supabase.storage
            .from("listing-photos")
            .upload(storagePath, item.blob, {
              contentType: "image/jpeg",
              upsert: true,
            });

          if (!uploadErr) {
            const { data: pubUrl } = supabase.storage
              .from("listing-photos")
              .getPublicUrl(storagePath);
            finalPath = pubUrl.publicUrl;
          } else {
            console.warn("Storage upload failed, using fallback dataUrl:", uploadErr);
          }
        } catch (e) {
          console.warn("Storage upload exception:", e);
        }
      }

      uploadedPhotos.push({
        id: photoId,
        listing_id: listingId,
        storage_path: finalPath,
        position: i,
      });
    }

    const newMeasurements: ListingMeasurement[] = params.measurements.map((m) => ({
      id: generateUUID(),
      listing_id: listingId,
      label: m.label.trim(),
      value: m.value,
      unit: m.unit,
    }));

    const newAvailabilities: Availability[] = params.availabilities.map((a) => ({
      id: generateUUID(),
      listing_id: listingId,
      start_date: a.startDate,
      end_date: a.endDate,
    }));

    if (supabase) {
      try {
        await supabase.from("listings").insert(newListing);
        if (uploadedPhotos.length > 0) {
          await supabase.from("listing_photos").insert(uploadedPhotos);
        }
        if (newMeasurements.length > 0) {
          await supabase.from("listing_measurements").insert(newMeasurements);
        }
        if (newAvailabilities.length > 0) {
          await supabase.from("availability").insert(newAvailabilities);
        }
      } catch (e) {
        console.warn("Supabase insert error:", e);
      }
    }

    // Always keep local store in sync
    const allListings = getLocalTable<Listing>("listings");
    allListings.unshift(newListing);
    setLocalTable("listings", allListings);

    const allPhotos = getLocalTable<ListingPhoto>("listing_photos");
    setLocalTable("listing_photos", [...allPhotos, ...uploadedPhotos]);

    const allMeasurements = getLocalTable<ListingMeasurement>("listing_measurements");
    setLocalTable("listing_measurements", [...allMeasurements, ...newMeasurements]);

    const allAvailabilities = getLocalTable<Availability>("availability");
    setLocalTable("availability", [...allAvailabilities, ...newAvailabilities]);

    return {
      ...newListing,
      photos: uploadedPhotos,
      measurements: newMeasurements,
      availability: newAvailabilities,
    };
  },

  async updateListing(
    id: string,
    updates: Partial<Listing>,
    measurements?: { label: string; value: number; unit: "cm" | "in" }[],
    availabilities?: { startDate: string; endDate: string }[]
  ): Promise<void> {
    const supabase = getSupabase();

    if (supabase) {
      try {
        await supabase.from("listings").update(updates).eq("id", id);
        if (measurements) {
          await supabase.from("listing_measurements").delete().eq("listing_id", id);
          if (measurements.length > 0) {
            await supabase.from("listing_measurements").insert(
              measurements.map((m) => ({
                id: generateUUID(),
                listing_id: id,
                label: m.label,
                value: m.value,
                unit: m.unit,
              }))
            );
          }
        }
        if (availabilities) {
          await supabase.from("availability").delete().eq("listing_id", id);
          if (availabilities.length > 0) {
            await supabase.from("availability").insert(
              availabilities.map((a) => ({
                id: generateUUID(),
                listing_id: id,
                start_date: a.startDate,
                end_date: a.endDate,
              }))
            );
          }
        }
      } catch (e) {
        console.warn("Supabase update error:", e);
      }
    }

    // Local sync
    const listings = getLocalTable<Listing>("listings");
    const idx = listings.findIndex((l) => l.id === id);
    if (idx !== -1) {
      listings[idx] = { ...listings[idx], ...updates };
      setLocalTable("listings", listings);
    }
  },

  async toggleListingStatus(id: string, status: "active" | "paused"): Promise<void> {
    return this.updateListing(id, { status });
  },

  async deleteListing(id: string): Promise<void> {
    const supabase = getSupabase();

    if (supabase) {
      try {
        // Fetch photos to remove from storage
        const { data: photos } = await supabase
          .from("listing_photos")
          .select("storage_path")
          .eq("listing_id", id);

        if (photos && photos.length > 0) {
          const paths = photos.map((p) => p.storage_path).filter((p) => !p.startsWith("data:"));
          if (paths.length > 0) {
            await supabase.storage.from("listing-photos").remove(paths);
          }
        }

        await supabase.from("listings").delete().eq("id", id);
      } catch (e) {
        console.warn("Supabase delete listing error:", e);
      }
    }

    // Local sync
    setLocalTable(
      "listings",
      getLocalTable<Listing>("listings").filter((l) => l.id !== id)
    );
    setLocalTable(
      "listing_photos",
      getLocalTable<ListingPhoto>("listing_photos").filter((p) => p.listing_id !== id)
    );
    setLocalTable(
      "listing_measurements",
      getLocalTable<ListingMeasurement>("listing_measurements").filter((m) => m.listing_id !== id)
    );
    setLocalTable(
      "availability",
      getLocalTable<Availability>("availability").filter((a) => a.listing_id !== id)
    );
  },

  // --- CONVERSATIONS & CHAT ---
  async getConversations(userId: string): Promise<Conversation[]> {
    const supabase = getSupabase();

    if (supabase) {
      try {
        const { data, error } = await supabase
          .from("conversations")
          .select(`
            *,
            listing:listings(*, photos:listing_photos(*)),
            renter:profiles!renter_id(*),
            lender:profiles!lender_id(*),
            messages:messages(*)
          `)
          .or(`renter_id.eq.${userId},lender_id.eq.${userId}`)
          .order("created_at", { ascending: false });

        if (!error && data) {
          return (data as any[]).map((c) => {
            const isRenter = c.renter_id === userId;
            const other = isRenter ? c.lender : c.renter;
            const msgs = (c.messages || []).sort(
              (a: Message, b: Message) =>
                new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
            );
            return {
              id: c.id,
              listing_id: c.listing_id,
              renter_id: c.renter_id,
              lender_id: c.lender_id,
              created_at: c.created_at,
              listing: c.listing,
              other_user: other,
              last_message: msgs[0],
            };
          });
        }
      } catch (e) {
        console.warn("Error getting conversations from Supabase:", e);
      }
    }

    // Local
    const conversations = getLocalTable<Conversation>("conversations").filter(
      (c) => c.renter_id === userId || c.lender_id === userId
    );
    const messages = getLocalTable<Message>("messages");
    const listings = getLocalTable<Listing>("listings");
    const photos = getLocalTable<ListingPhoto>("listing_photos");
    const profiles = getLocalTable<Profile>("profiles");

    const result = conversations.map((c) => {
      const listing = listings.find((l) => l.id === c.listing_id);
      const listingWithPhotos = listing
        ? {
            ...listing,
            photos: photos.filter((p) => p.listing_id === listing.id),
          }
        : undefined;

      const otherUserId = c.renter_id === userId ? c.lender_id : c.renter_id;
      const otherUser = profiles.find((p) => p.id === otherUserId);

      const convMessages = messages
        .filter((m) => m.conversation_id === c.id)
        .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());

      return {
        ...c,
        listing: listingWithPhotos,
        other_user: otherUser,
        last_message: convMessages[0],
      };
    });

    // Sort by latest message date or creation date
    return result.sort((a, b) => {
      const timeA = new Date(a.last_message?.created_at || a.created_at).getTime();
      const timeB = new Date(b.last_message?.created_at || b.created_at).getTime();
      return timeB - timeA;
    });
  },

  async getOrCreateConversation(
    listingId: string,
    renterId: string,
    lenderId: string
  ): Promise<Conversation> {
    const supabase = getSupabase();

    if (supabase) {
      try {
        const { data: existing } = await supabase
          .from("conversations")
          .select("*")
          .eq("listing_id", listingId)
          .eq("renter_id", renterId)
          .eq("lender_id", lenderId)
          .maybeSingle();

        if (existing) {
          return existing as Conversation;
        }

        const newConv: Conversation = {
          id: generateUUID(),
          listing_id: listingId,
          renter_id: renterId,
          lender_id: lenderId,
          created_at: new Date().toISOString(),
        };

        const { data: created, error } = await supabase
          .from("conversations")
          .insert(newConv)
          .select()
          .single();

        if (!error && created) {
          return created as Conversation;
        }
      } catch (e) {
        console.warn("Error getting/creating conversation in Supabase:", e);
      }
    }

    // Local
    const conversations = getLocalTable<Conversation>("conversations");
    const existing = conversations.find(
      (c) => c.listing_id === listingId && c.renter_id === renterId && c.lender_id === lenderId
    );
    if (existing) return existing;

    const newConv: Conversation = {
      id: generateUUID(),
      listing_id: listingId,
      renter_id: renterId,
      lender_id: lenderId,
      created_at: new Date().toISOString(),
    };

    conversations.push(newConv);
    setLocalTable("conversations", conversations);
    return newConv;
  },

  async getMessages(conversationId: string): Promise<Message[]> {
    const supabase = getSupabase();

    if (supabase) {
      try {
        const { data, error } = await supabase
          .from("messages")
          .select("*")
          .eq("conversation_id", conversationId)
          .order("created_at", { ascending: true });

        if (!error && data) {
          return data as Message[];
        }
      } catch (e) {
        console.warn("Error getting messages from Supabase:", e);
      }
    }

    return getLocalTable<Message>("messages")
      .filter((m) => m.conversation_id === conversationId)
      .sort((a, b) => new Date(a.created_at).getTime() - new Date(b.created_at).getTime());
  },

  async sendMessage(conversationId: string, senderId: string, body: string): Promise<Message> {
    const msg: Message = {
      id: generateUUID(),
      conversation_id: conversationId,
      sender_id: senderId,
      body: body.trim(),
      created_at: new Date().toISOString(),
    };

    const supabase = getSupabase();
    if (supabase) {
      try {
        const { data, error } = await supabase.from("messages").insert(msg).select().single();
        if (!error && data) {
          return data as Message;
        }
      } catch (e) {
        console.warn("Supabase send message error:", e);
      }
    }

    const messages = getLocalTable<Message>("messages");
    messages.push(msg);
    setLocalTable("messages", messages);
    return msg;
  },

  subscribeToMessages(conversationId: string, onMessage: (msg: Message) => void) {
    const supabase = getSupabase();
    if (!supabase) return () => {};

    const channel = supabase
      .channel(`chat_${conversationId}`)
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "public",
          table: "messages",
          filter: `conversation_id=eq.${conversationId}`,
        },
        (payload) => {
          if (payload.new) {
            onMessage(payload.new as Message);
          }
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  },

  // --- REPORTING, BLOCKING & OUTCOMES ---
  async report(params: Report): Promise<void> {
    const supabase = getSupabase();
    const reportData = {
      ...params,
      id: generateUUID(),
      created_at: new Date().toISOString(),
    };

    if (supabase) {
      try {
        await supabase.from("reports").insert(reportData);
      } catch (e) {
        console.warn("Supabase report error:", e);
      }
    }

    const reports = getLocalTable<Report>("reports");
    reports.push(reportData);
    setLocalTable("reports", reports);
  },

  async blockUser(blockerId: string, blockedId: string): Promise<void> {
    const supabase = getSupabase();
    if (supabase) {
      try {
        await supabase.from("blocks").insert({ blocker_id: blockerId, blocked_id: blockedId });
      } catch (e) {
        console.warn("Supabase block error:", e);
      }
    }

    const blocks = getLocalTable<{ blocker_id: string; blocked_id: string }>("blocks");
    if (!blocks.some((b) => b.blocker_id === blockerId && b.blocked_id === blockedId)) {
      blocks.push({ blocker_id: blockerId, blocked_id: blockedId });
      setLocalTable("blocks", blocks);
    }
  },

  async getRentalOutcome(conversationId: string, userId: string): Promise<RentalOutcome | null> {
    const supabase = getSupabase();
    if (supabase) {
      try {
        const { data } = await supabase
          .from("rental_outcomes")
          .select("*")
          .eq("conversation_id", conversationId)
          .eq("user_id", userId)
          .maybeSingle();

        if (data) return data as RentalOutcome;
      } catch (e) {
        console.warn("Error getting rental outcome:", e);
      }
    }

    const outcomes = getLocalTable<RentalOutcome>("rental_outcomes");
    return (
      outcomes.find((o) => o.conversation_id === conversationId && o.user_id === userId) || null
    );
  },

  async submitRentalOutcome(
    conversationId: string,
    userId: string,
    happened: boolean
  ): Promise<void> {
    const outcome: RentalOutcome = {
      conversation_id: conversationId,
      user_id: userId,
      happened,
      answered_at: new Date().toISOString(),
    };

    const supabase = getSupabase();
    if (supabase) {
      try {
        await supabase.from("rental_outcomes").upsert(outcome);
      } catch (e) {
        console.warn("Supabase rental outcome error:", e);
      }
    }

    const outcomes = getLocalTable<RentalOutcome>("rental_outcomes");
    const filtered = outcomes.filter(
      (o) => !(o.conversation_id === conversationId && o.user_id === userId)
    );
    filtered.push(outcome);
    setLocalTable("rental_outcomes", filtered);
  },
};
