import React, { useState, useEffect, useCallback } from "react";
import {
  Search,
  SlidersHorizontal,
  MapPin,
  RefreshCw,
  Plus,
  AlertCircle,
  Inbox,
  Sparkles,
} from "lucide-react";
import { Listing, CATEGORIES } from "../types/database";
import { DzirentService, AuthUser } from "../lib/supabaseClient";

interface BrowseScreenProps {
  currentUser: AuthUser;
  onSelectListing: (listingId: string) => void;
  onCreateListing: () => void;
  onRequestChangeLocation: () => void;
}

export const BrowseScreen: React.FC<BrowseScreenProps> = ({
  currentUser,
  onSelectListing,
  onCreateListing,
  onRequestChangeLocation,
}) => {
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedRadius, setSelectedRadius] = useState<number>(25); // 2, 5, 10, 25 km
  const [listings, setListings] = useState<Listing[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [offset, setOffset] = useState(0);

  const fetchListings = useCallback(
    async (isRefresh = false, currentOffset = 0) => {
      if (isRefresh) {
        setIsRefreshing(true);
      } else if (currentOffset === 0) {
        setIsLoading(true);
      }
      setErrorMessage(null);

      try {
        const res = await DzirentService.getListings({
          category: selectedCategory || undefined,
          radiusKm: selectedRadius,
          searchQuery: searchQuery.trim() || undefined,
          userLat: currentUser.profile.approx_lat,
          userLng: currentUser.profile.approx_lng,
          limit: 20,
          offset: currentOffset,
        });

        if (currentOffset === 0) {
          setListings(res.listings);
        } else {
          setListings((prev) => [...prev, ...res.listings]);
        }
        setHasMore(res.hasMore);
      } catch (err: any) {
        console.error("Browse load error:", err);
        setErrorMessage(err?.message || "Failed to load listings. Check connection.");
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [selectedCategory, selectedRadius, searchQuery, currentUser.profile.approx_lat, currentUser.profile.approx_lng]
  );

  useEffect(() => {
    setOffset(0);
    fetchListings(false, 0);
  }, [fetchListings]);

  const handlePullRefresh = () => {
    setOffset(0);
    fetchListings(true, 0);
  };

  const handleLoadMore = () => {
    const nextOffset = offset + 20;
    setOffset(nextOffset);
    fetchListings(false, nextOffset);
  };

  const canLend = currentUser.profile.role === "lender" || currentUser.profile.role === "both";

  return (
    <div className="flex-1 flex flex-col bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-neutral-100 overflow-y-auto pb-24">
      {/* Search & Location Bar */}
      <div className="p-4 bg-white dark:bg-neutral-900 border-b border-neutral-200 dark:border-neutral-800 space-y-3 sticky top-0 z-20 shadow-xs">
        {/* Search Input */}
        <div className="relative">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by outfit name, style, or brand..."
            className="w-full pl-10 pr-10 py-2.5 rounded-2xl bg-neutral-100 dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 dark:focus:ring-blue-500 placeholder:text-neutral-400"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-neutral-400 hover:text-neutral-600 dark:hover:text-neutral-200 p-1"
            >
              Clear
            </button>
          )}
        </div>

        {/* Distance Selector (2, 5, 10, 25 km) */}
        <div className="flex items-center justify-between text-xs pt-0.5">
          <div className="flex items-center space-x-1.5 text-neutral-500 dark:text-neutral-400 font-medium">
            <MapPin className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
            <span>Within:</span>
          </div>

          <div className="flex items-center space-x-1.5 bg-neutral-100 dark:bg-neutral-800 p-1 rounded-xl">
            {[2, 5, 10, 25].map((km) => (
              <button
                key={km}
                onClick={() => setSelectedRadius(km)}
                className={`px-2.5 py-1 rounded-lg font-semibold transition-all ${
                  selectedRadius === km
                    ? "bg-white dark:bg-neutral-900 text-blue-600 dark:text-blue-400 shadow-xs"
                    : "text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
                }`}
              >
                {km} km
              </button>
            ))}
          </div>
        </div>

        {/* Horizontal Category Chips */}
        <div className="flex items-center space-x-2 overflow-x-auto no-scrollbar pt-1 -mx-4 px-4">
          <button
            onClick={() => setSelectedCategory(null)}
            className={`whitespace-nowrap px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all shrink-0 ${
              selectedCategory === null
                ? "bg-blue-600 text-white shadow-xs"
                : "bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200 dark:hover:bg-neutral-700"
            }`}
          >
            All Categories
          </button>

          {CATEGORIES.map((cat) => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat === selectedCategory ? null : cat)}
              className={`whitespace-nowrap px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all shrink-0 ${
                selectedCategory === cat
                  ? "bg-blue-600 text-white shadow-xs"
                  : "bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-300 hover:bg-neutral-200 dark:hover:bg-neutral-700"
              }`}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {/* Main Content Area */}
      <div className="p-4 flex-1 flex flex-col">
        {/* Pull to refresh indicator */}
        <div className="flex items-center justify-between pb-3 text-xs text-neutral-500">
          <span>
            {listings.length} {listings.length === 1 ? "outfit" : "outfits"} near {currentUser.profile.city}
          </span>
          <button
            onClick={handlePullRefresh}
            disabled={isRefreshing}
            className="flex items-center space-x-1 hover:text-blue-600 transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? "animate-spin" : ""}`} />
            <span>Refresh</span>
          </button>
        </div>

        {/* LOADING STATE */}
        {isLoading && (
          <div className="grid grid-cols-2 gap-3 py-4 animate-pulse">
            {[1, 2, 3, 4].map((n) => (
              <div
                key={n}
                className="bg-white dark:bg-neutral-900 rounded-2xl p-2.5 space-y-2 border border-neutral-200 dark:border-neutral-800"
              >
                <div className="w-full aspect-4/5 bg-neutral-200 dark:bg-neutral-800 rounded-xl" />
                <div className="h-4 bg-neutral-200 dark:bg-neutral-800 rounded w-3/4" />
                <div className="h-3 bg-neutral-200 dark:bg-neutral-800 rounded w-1/2" />
              </div>
            ))}
          </div>
        )}

        {/* ERROR STATE WITH RETRY */}
        {!isLoading && errorMessage && (
          <div className="my-auto py-12 px-4 text-center space-y-3">
            <div className="w-12 h-12 rounded-full bg-red-100 dark:bg-red-950/60 text-red-600 dark:text-red-400 flex items-center justify-center mx-auto">
              <AlertCircle className="w-6 h-6" />
            </div>
            <h3 className="text-base font-bold">Could not load listings</h3>
            <p className="text-xs text-neutral-500 max-w-xs mx-auto">{errorMessage}</p>
            <button
              onClick={() => fetchListings(false, 0)}
              className="py-2 px-5 rounded-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md transition-colors m3-button inline-flex items-center space-x-1.5"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Retry</span>
            </button>
          </div>
        )}

        {/* EMPTY STATE */}
        {!isLoading && !errorMessage && listings.length === 0 && (
          <div className="my-auto py-16 px-4 text-center space-y-4">
            <div className="w-16 h-16 rounded-3xl bg-neutral-100 dark:bg-neutral-800 flex items-center justify-center mx-auto text-neutral-400 dark:text-neutral-500">
              <Sparkles className="w-8 h-8 stroke-1" />
            </div>
            <div className="space-y-1">
              <h3 className="text-base font-bold text-neutral-900 dark:text-neutral-100">
                No outfits found nearby
              </h3>
              <p className="text-xs text-neutral-500 max-w-xs mx-auto">
                {searchQuery || selectedCategory
                  ? "Try expanding your distance radius or clearing filters."
                  : `Be the first person in ${currentUser.profile.city} to list an outfit!`}
              </p>
            </div>

            <div className="pt-2 flex flex-col sm:flex-row gap-2.5 justify-center max-w-xs mx-auto">
              {canLend ? (
                <button
                  onClick={onCreateListing}
                  className="py-3 px-5 rounded-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md flex items-center justify-center space-x-1.5 m3-button"
                >
                  <Plus className="w-4 h-4" />
                  <span>List your clothes</span>
                </button>
              ) : (
                <button
                  onClick={onRequestChangeLocation}
                  className="py-3 px-5 rounded-full border border-neutral-300 dark:border-neutral-700 hover:bg-neutral-100 dark:hover:bg-neutral-800 text-xs font-semibold flex items-center justify-center space-x-1.5"
                >
                  <MapPin className="w-4 h-4 text-blue-600" />
                  <span>Change location</span>
                </button>
              )}
            </div>
          </div>
        )}

        {/* 2-COLUMN GRID OF CARDS */}
        {!isLoading && !errorMessage && listings.length > 0 && (
          <div className="grid grid-cols-2 gap-3.5">
            {listings.map((item) => {
              const firstPhoto = item.photos && item.photos.length > 0 ? item.photos[0].storage_path : null;

              return (
                <div
                  key={item.id}
                  onClick={() => onSelectListing(item.id)}
                  className="bg-white dark:bg-neutral-900 rounded-3xl overflow-hidden border border-neutral-200/80 dark:border-neutral-800 shadow-xs hover:shadow-md transition-all cursor-pointer flex flex-col group select-none"
                >
                  {/* Photo Container */}
                  <div className="relative aspect-3/4 w-full bg-neutral-200 dark:bg-neutral-800 overflow-hidden">
                    {firstPhoto ? (
                      <img
                        src={firstPhoto}
                        alt={item.title}
                        loading="lazy"
                        className="w-full h-full object-cover group-hover:scale-102 transition-transform duration-300"
                        onError={(e) => {
                          (e.currentTarget as HTMLImageElement).src =
                            "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=600&auto=format&fit=crop&q=80";
                        }}
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-neutral-400 text-xs">
                        No photo
                      </div>
                    )}

                    {/* Size Badge */}
                    <div className="absolute top-2.5 left-2.5 bg-black/70 backdrop-blur-xs text-white text-[11px] font-bold px-2 py-0.5 rounded-full">
                      Size {item.size_label}
                    </div>

                    {/* Distance Badge */}
                    {item.distance_km !== undefined && (
                      <div className="absolute bottom-2.5 right-2.5 bg-black/60 backdrop-blur-xs text-white text-[10px] font-medium px-2 py-0.5 rounded-full flex items-center space-x-1">
                        <MapPin className="w-2.5 h-2.5 text-blue-400" />
                        <span>{item.distance_km} km</span>
                      </div>
                    )}
                  </div>

                  {/* Card Content */}
                  <div className="p-3 flex-1 flex flex-col justify-between space-y-1.5">
                    <div>
                      <h4 className="font-bold text-sm text-neutral-900 dark:text-neutral-100 line-clamp-1 leading-snug">
                        {item.title}
                      </h4>
                      <p className="text-[11px] text-neutral-500 dark:text-neutral-400 line-clamp-1">
                        {item.category}
                      </p>
                    </div>

                    <div className="pt-1 border-t border-neutral-100 dark:border-neutral-800/80 flex items-center justify-between">
                      <span className="text-xs font-bold text-blue-600 dark:text-blue-400">
                        {item.price_note || "Peer agreement"}
                      </span>
                      <span className="text-[10px] text-neutral-400 truncate max-w-[70px]">
                        {item.city}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* PAGINATION (Pagination of 20) */}
        {!isLoading && hasMore && (
          <div className="pt-6 pb-2 text-center">
            <button
              onClick={handleLoadMore}
              className="py-2.5 px-6 rounded-full border border-neutral-300 dark:border-neutral-700 hover:bg-neutral-100 dark:hover:bg-neutral-800 text-xs font-semibold text-neutral-700 dark:text-neutral-300 transition-colors m3-button"
            >
              Load more outfits (20)
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
