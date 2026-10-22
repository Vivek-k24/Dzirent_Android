import React, { useState, useEffect } from "react";
import {
  Sparkles,
  Plus,
  Play,
  Pause,
  Trash2,
  RefreshCw,
  AlertTriangle,
  Loader2,
  MapPin,
  Calendar,
} from "lucide-react";
import { Listing } from "../types/database";
import { DzirentService, AuthUser } from "../lib/supabaseClient";

interface MyClosetScreenProps {
  currentUser: AuthUser;
  onSelectListing: (listingId: string) => void;
  onCreateListing: () => void;
}

export const MyClosetScreen: React.FC<MyClosetScreenProps> = ({
  currentUser,
  onSelectListing,
  onCreateListing,
}) => {
  const [listings, setListings] = useState<Listing[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Delete modal state
  const [listingToDelete, setListingToDelete] = useState<Listing | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const fetchMyListings = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await DzirentService.getMyListings(currentUser.id);
      setListings(data);
    } catch (e: any) {
      setErrorMessage(e?.message || "Failed to load your listings");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchMyListings();
  }, [currentUser.id]);

  const handleToggleStatus = async (item: Listing, e: React.MouseEvent) => {
    e.stopPropagation();
    const newStatus = item.status === "active" ? "paused" : "active";
    try {
      await DzirentService.toggleListingStatus(item.id, newStatus);
      setListings((prev) =>
        prev.map((l) => (l.id === item.id ? { ...l, status: newStatus } : l))
      );
    } catch (err) {
      console.error("Toggle status error:", err);
    }
  };

  const handleConfirmDelete = async () => {
    if (!listingToDelete) return;
    setIsDeleting(true);
    try {
      await DzirentService.deleteListing(listingToDelete.id);
      setListings((prev) => prev.filter((l) => l.id !== listingToDelete.id));
      setListingToDelete(null);
    } catch (err) {
      console.error("Delete listing error:", err);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-neutral-100 overflow-y-auto pb-24">
      {/* Top Header */}
      <div className="p-4 bg-white dark:bg-neutral-900 border-b border-neutral-200 dark:border-neutral-800 flex items-center justify-between sticky top-0 z-20">
        <div>
          <h2 className="text-xl font-bold tracking-tight">My Closet</h2>
          <p className="text-xs text-neutral-500">Manage your active and paused rental listings</p>
        </div>
        <button
          onClick={onCreateListing}
          className="py-2 px-3.5 rounded-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-sm flex items-center space-x-1.5 transition-colors m3-button"
        >
          <Plus className="w-4 h-4" />
          <span>New</span>
        </button>
      </div>

      <div className="p-4 flex-1 flex flex-col">
        {/* Loading state */}
        {isLoading && (
          <div className="flex-1 flex flex-col items-center justify-center p-8 space-y-3">
            <Loader2 className="w-8 h-8 text-blue-600 animate-spin" />
            <p className="text-xs text-neutral-500">Loading your closet items...</p>
          </div>
        )}

        {/* Error state */}
        {!isLoading && errorMessage && (
          <div className="my-auto py-12 px-4 text-center space-y-3">
            <div className="w-12 h-12 rounded-full bg-red-100 dark:bg-red-950/60 text-red-600 dark:text-red-400 flex items-center justify-center mx-auto">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <h4 className="text-base font-bold">Could not load closet</h4>
            <p className="text-xs text-neutral-500 max-w-xs mx-auto">{errorMessage}</p>
            <button
              onClick={fetchMyListings}
              className="py-2 px-5 rounded-full bg-blue-600 text-white text-xs font-semibold shadow-md inline-flex items-center space-x-1.5"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Retry</span>
            </button>
          </div>
        )}

        {/* Empty state */}
        {!isLoading && !errorMessage && listings.length === 0 && (
          <div className="my-auto py-16 px-4 text-center space-y-4">
            <div className="w-16 h-16 rounded-3xl bg-neutral-100 dark:bg-neutral-800 flex items-center justify-center mx-auto text-neutral-400 dark:text-neutral-500">
              <Sparkles className="w-8 h-8 stroke-1" />
            </div>
            <div className="space-y-1">
              <h3 className="text-base font-bold">Your closet is empty</h3>
              <p className="text-xs text-neutral-500 max-w-xs mx-auto">
                Turn outfits hanging in your closet into peer-to-peer rentals.
              </p>
            </div>
            <button
              onClick={onCreateListing}
              className="py-3 px-6 rounded-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md inline-flex items-center space-x-1.5 m3-button"
            >
              <Plus className="w-4 h-4" />
              <span>List your first outfit</span>
            </button>
          </div>
        )}

        {/* Listings List */}
        {!isLoading && !errorMessage && listings.length > 0 && (
          <div className="space-y-3">
            {listings.map((item) => {
              const coverPhoto = item.photos && item.photos[0] ? item.photos[0].storage_path : null;
              const isActive = item.status === "active";

              return (
                <div
                  key={item.id}
                  onClick={() => onSelectListing(item.id)}
                  className="bg-white dark:bg-neutral-900 rounded-3xl p-3 border border-neutral-200 dark:border-neutral-800 shadow-xs hover:shadow-md transition-all flex items-center space-x-3.5 cursor-pointer group"
                >
                  {/* Photo */}
                  <div className="w-20 h-24 rounded-2xl bg-neutral-200 dark:bg-neutral-800 overflow-hidden shrink-0 relative">
                    {coverPhoto ? (
                      <img
                        src={coverPhoto}
                        alt={item.title}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-[10px] text-neutral-400">
                        No photo
                      </div>
                    )}
                    <span
                      className={`absolute top-1 left-1 w-2.5 h-2.5 rounded-full ring-2 ring-white dark:ring-neutral-900 ${
                        isActive ? "bg-emerald-500" : "bg-neutral-400"
                      }`}
                    />
                  </div>

                  {/* Info */}
                  <div className="flex-1 min-w-0 space-y-1">
                    <div className="flex items-center space-x-1.5">
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-neutral-100 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-400">
                        Size {item.size_label}
                      </span>
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                          isActive
                            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                            : "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300"
                        }`}
                      >
                        {isActive ? "Active" : "Paused"}
                      </span>
                    </div>

                    <h4 className="font-bold text-sm text-neutral-900 dark:text-neutral-100 truncate">
                      {item.title}
                    </h4>

                    <p className="text-xs font-semibold text-blue-600 dark:text-blue-400">
                      {item.price_note || "Peer agreement"}
                    </p>

                    <p className="text-[10px] text-neutral-400">
                      {item.photos?.length || 0} photos · {item.measurements?.length || 0} measurements
                    </p>
                  </div>

                  {/* Actions: Active/Paused toggle & Delete */}
                  <div className="flex flex-col space-y-2 shrink-0">
                    <button
                      type="button"
                      onClick={(e) => handleToggleStatus(item, e)}
                      title={isActive ? "Pause listing" : "Activate listing"}
                      className={`p-2 rounded-2xl border transition-colors ${
                        isActive
                          ? "border-amber-300 text-amber-600 bg-amber-50 dark:bg-amber-950/50 hover:bg-amber-100"
                          : "border-emerald-300 text-emerald-600 bg-emerald-50 dark:bg-emerald-950/50 hover:bg-emerald-100"
                      }`}
                    >
                      {isActive ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
                    </button>

                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setListingToDelete(item);
                      }}
                      title="Delete listing"
                      className="p-2 rounded-2xl border border-neutral-200 dark:border-neutral-800 text-neutral-400 hover:text-red-600 hover:border-red-300 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Delete Confirmation Modal */}
      {listingToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-red-100 dark:bg-red-950/60 text-red-600 dark:text-red-400 flex items-center justify-center mx-auto">
              <Trash2 className="w-6 h-6" />
            </div>

            <div className="text-center space-y-1.5">
              <h3 className="text-base font-bold text-neutral-900 dark:text-neutral-100">
                Delete "{listingToDelete.title}"?
              </h3>
              <p className="text-xs text-neutral-500">
                This will permanently delete the listing and remove its uploaded photos from storage. This action cannot be undone.
              </p>
            </div>

            <div className="pt-2 flex gap-2.5">
              <button
                onClick={() => setListingToDelete(null)}
                disabled={isDeleting}
                className="flex-1 py-2.5 rounded-full border border-neutral-300 dark:border-neutral-700 text-xs font-semibold"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmDelete}
                disabled={isDeleting}
                className="flex-1 py-2.5 rounded-full bg-red-600 hover:bg-red-700 text-white text-xs font-semibold shadow-md flex items-center justify-center space-x-1.5 disabled:opacity-50"
              >
                {isDeleting ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <span>Delete</span>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
