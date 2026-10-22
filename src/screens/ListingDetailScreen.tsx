import React, { useState, useEffect } from "react";
import {
  ArrowLeft,
  MoreVertical,
  MessageSquare,
  MapPin,
  Calendar,
  Ruler,
  AlertTriangle,
  User,
  ShieldAlert,
  Loader2,
  RefreshCw,
  Clock,
  Sparkles,
} from "lucide-react";
import { Listing } from "../types/database";
import { DzirentService, AuthUser } from "../lib/supabaseClient";
import { PhotoCarousel } from "../components/PhotoCarousel";

interface ListingDetailScreenProps {
  listingId: string;
  currentUser: AuthUser;
  onBack: () => void;
  onStartChat: (listing: Listing) => void;
}

export const ListingDetailScreen: React.FC<ListingDetailScreenProps> = ({
  listingId,
  currentUser,
  onBack,
  onStartChat,
}) => {
  const [listing, setListing] = useState<Listing | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Menu & Reporting Modal
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isReportModalOpen, setIsReportModalOpen] = useState(false);
  const [reportReason, setReportReason] = useState("");
  const [reportSuccess, setReportSuccess] = useState(false);
  const [isSubmittingReport, setIsSubmittingReport] = useState(false);

  const fetchDetail = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await DzirentService.getListingDetail(listingId);
      if (!data) {
        setErrorMessage("Listing not found or has been removed.");
      } else {
        setListing(data);
      }
    } catch (e: any) {
      setErrorMessage(e?.message || "Failed to load listing details");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchDetail();
  }, [listingId]);

  const handleReport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reportReason.trim() || !listing) return;

    setIsSubmittingReport(true);
    try {
      await DzirentService.report({
        reporter_id: currentUser.id,
        target_type: "listing",
        target_id: listing.id,
        reason: reportReason.trim(),
      });
      setReportSuccess(true);
      setTimeout(() => {
        setIsReportModalOpen(false);
        setReportSuccess(false);
        setReportReason("");
      }, 1500);
    } catch (err) {
      console.error("Report failed:", err);
    } finally {
      setIsSubmittingReport(false);
    }
  };

  const isOwnListing = listing?.lender_id === currentUser.id;

  return (
    <div className="flex-1 flex flex-col bg-white dark:bg-neutral-900 text-neutral-900 dark:text-neutral-100 overflow-y-auto">
      {/* Top App Bar */}
      <div className="sticky top-0 inset-x-0 bg-white/95 dark:bg-neutral-900/95 backdrop-blur-md border-b border-neutral-200 dark:border-neutral-800 z-30 px-4 py-2.5 flex items-center justify-between">
        <button
          onClick={onBack}
          className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-700 dark:text-neutral-300"
          aria-label="Back"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>

        <h3 className="font-bold text-sm tracking-tight truncate max-w-[200px]">
          {listing ? listing.title : "Listing Details"}
        </h3>

        <div className="relative">
          <button
            onClick={() => setIsMenuOpen(!isMenuOpen)}
            className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-600 dark:text-neutral-300"
            aria-label="More options"
          >
            <MoreVertical className="w-5 h-5" />
          </button>

          {isMenuOpen && (
            <div className="absolute right-0 mt-1 w-44 bg-white dark:bg-neutral-800 border border-neutral-200 dark:border-neutral-700 rounded-2xl shadow-xl py-1.5 z-40 text-xs">
              <button
                onClick={() => {
                  setIsMenuOpen(false);
                  setIsReportModalOpen(true);
                }}
                className="w-full px-4 py-2 text-left flex items-center space-x-2 text-red-600 hover:bg-red-50 dark:hover:bg-red-950/50"
              >
                <ShieldAlert className="w-4 h-4" />
                <span>Report listing</span>
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Loading state */}
      {isLoading && (
        <div className="flex-1 flex flex-col items-center justify-center p-8 space-y-3">
          <Loader2 className="w-8 h-8 text-blue-600 animate-spin" />
          <p className="text-xs text-neutral-500 font-medium">Loading outfit details...</p>
        </div>
      )}

      {/* Error state */}
      {!isLoading && errorMessage && (
        <div className="flex-1 flex flex-col items-center justify-center p-8 text-center space-y-3">
          <AlertTriangle className="w-10 h-10 text-amber-500" />
          <h4 className="text-base font-bold">Could not load listing</h4>
          <p className="text-xs text-neutral-500">{errorMessage}</p>
          <button
            onClick={fetchDetail}
            className="py-2 px-5 rounded-full bg-blue-600 text-white text-xs font-semibold shadow-md flex items-center space-x-1.5"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Retry</span>
          </button>
        </div>
      )}

      {/* Content */}
      {!isLoading && listing && (
        <div className="p-4 space-y-6 pb-28 max-w-lg mx-auto w-full">
          {/* Swipeable Photo Carousel */}
          <PhotoCarousel photos={listing.photos} title={listing.title} />

          {/* Title, Category & Price */}
          <div className="space-y-2">
            <div className="flex items-center space-x-2">
              <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-blue-100 text-blue-700 dark:bg-blue-950/80 dark:text-blue-300">
                {listing.category}
              </span>
              <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-neutral-100 dark:bg-neutral-800 text-neutral-700 dark:text-neutral-300">
                Size {listing.size_label}
              </span>
            </div>

            <h1 className="text-2xl font-bold tracking-tight text-neutral-900 dark:text-neutral-100">
              {listing.title}
            </h1>

            <div className="p-3 rounded-2xl bg-neutral-50 dark:bg-neutral-800/60 border border-neutral-100 dark:border-neutral-800 flex items-center justify-between">
              <div>
                <span className="text-[11px] uppercase tracking-wider text-neutral-400 font-bold block">
                  Price / Rental Note
                </span>
                <span className="text-base font-extrabold text-blue-600 dark:text-blue-400">
                  {listing.price_note || "Peer agreement"}
                </span>
              </div>
              <span className="text-xs text-neutral-500 font-medium">
                Direct peer handover
              </span>
            </div>
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <h3 className="text-sm font-bold text-neutral-900 dark:text-neutral-100">
              About this piece
            </h3>
            <p className="text-sm text-neutral-600 dark:text-neutral-300 leading-relaxed whitespace-pre-wrap">
              {listing.description || "No description provided."}
            </p>
          </div>

          {/* Measurements Table */}
          <div className="space-y-2">
            <div className="flex items-center space-x-1.5">
              <Ruler className="w-4 h-4 text-blue-600 dark:text-blue-400" />
              <h3 className="text-sm font-bold text-neutral-900 dark:text-neutral-100">
                Exact Measurements
              </h3>
            </div>

            {listing.measurements && listing.measurements.length > 0 ? (
              <div className="border border-neutral-200 dark:border-neutral-800 rounded-2xl overflow-hidden divide-y divide-neutral-200 dark:divide-neutral-800 text-xs">
                {listing.measurements.map((m, idx) => (
                  <div
                    key={idx}
                    className="flex items-center justify-between px-4 py-2.5 bg-neutral-50/50 dark:bg-neutral-800/30"
                  >
                    <span className="font-medium text-neutral-600 dark:text-neutral-400">
                      {m.label}
                    </span>
                    <span className="font-bold text-neutral-900 dark:text-neutral-100">
                      {m.value} {m.unit}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-neutral-500 italic p-3 bg-neutral-50 dark:bg-neutral-800/40 rounded-2xl">
                No specific measurements listed. Feel free to message the lender to ask!
              </p>
            )}
          </div>

          {/* Availability Calendar / Date Ranges */}
          <div className="space-y-2">
            <div className="flex items-center space-x-1.5">
              <Calendar className="w-4 h-4 text-blue-600 dark:text-blue-400" />
              <h3 className="text-sm font-bold text-neutral-900 dark:text-neutral-100">
                Availability Calendar
              </h3>
            </div>

            {listing.availability && listing.availability.length > 0 ? (
              <div className="space-y-1.5">
                {listing.availability.map((avail, idx) => (
                  <div
                    key={idx}
                    className="flex items-center space-x-2.5 p-3 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800/60 text-xs"
                  >
                    <Clock className="w-4 h-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
                    <div>
                      <span className="font-bold text-emerald-900 dark:text-emerald-200">
                        Available:
                      </span>{" "}
                      <span className="text-emerald-800 dark:text-emerald-300 font-medium">
                        {avail.start_date} to {avail.end_date}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-neutral-500 italic p-3 bg-neutral-50 dark:bg-neutral-800/40 rounded-2xl">
                Flexible availability. Message the lender to check your desired dates.
              </p>
            )}
          </div>

          {/* Lender Info */}
          <div className="space-y-2 pt-2">
            <h3 className="text-sm font-bold text-neutral-900 dark:text-neutral-100">
              Lender
            </h3>
            <div className="flex items-center justify-between p-3.5 rounded-2xl border border-neutral-200 dark:border-neutral-800 bg-neutral-50 dark:bg-neutral-800/40">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-950 text-blue-600 dark:text-blue-400 flex items-center justify-center font-bold text-sm">
                  {listing.lender?.display_name?.charAt(0).toUpperCase() || "L"}
                </div>
                <div>
                  <h4 className="font-bold text-sm text-neutral-900 dark:text-neutral-100">
                    {listing.lender?.display_name || "Lender"}
                  </h4>
                  <div className="flex items-center space-x-1 text-xs text-neutral-500">
                    <MapPin className="w-3 h-3 text-blue-500 shrink-0" />
                    <span>{listing.city || listing.lender?.city || "Nearby"}</span>
                  </div>
                </div>
              </div>

              <div className="text-right text-[11px] text-neutral-500">
                <span className="inline-block px-2 py-0.5 rounded-full bg-blue-50 dark:bg-blue-950/60 text-blue-600 dark:text-blue-400 font-semibold">
                  Verified Age 18+
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Floating Bottom Action: "Message lender" */}
      {!isLoading && listing && (
        <div className="fixed bottom-0 inset-x-0 bg-white/95 dark:bg-neutral-900/95 backdrop-blur-md border-t border-neutral-200 dark:border-neutral-800 p-4 pb-safe z-30 shadow-lg">
          <div className="max-w-lg mx-auto flex items-center justify-between">
            <div>
              <span className="text-[10px] uppercase font-bold text-neutral-400">Rental terms</span>
              <p className="text-sm font-extrabold text-neutral-900 dark:text-neutral-100">
                {listing.price_note || "Direct Handover"}
              </p>
            </div>

            {isOwnListing ? (
              <span className="text-xs font-semibold px-4 py-2 rounded-full bg-neutral-100 dark:bg-neutral-800 text-neutral-500">
                This is your listing
              </span>
            ) : (
              <button
                onClick={() => onStartChat(listing)}
                className="py-3.5 px-6 rounded-full bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm shadow-lg shadow-blue-500/25 flex items-center space-x-2 transition-all m3-button"
              >
                <MessageSquare className="w-4 h-4" />
                <span>Message Lender</span>
              </button>
            )}
          </div>
        </div>
      )}

      {/* Report Listing Modal */}
      {isReportModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-4">
            <div className="flex items-center space-x-2 text-red-600 dark:text-red-400">
              <ShieldAlert className="w-5 h-5 shrink-0" />
              <h3 className="font-bold text-base">Report Listing</h3>
            </div>

            {reportSuccess ? (
              <div className="p-4 rounded-2xl bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 text-xs text-center font-semibold">
                Thank you. Your report has been submitted for moderation.
              </div>
            ) : (
              <form onSubmit={handleReport} className="space-y-3">
                <p className="text-xs text-neutral-500">
                  Help keep Dzirent safe. Please describe why you are reporting this listing:
                </p>
                <textarea
                  required
                  rows={3}
                  value={reportReason}
                  onChange={(e) => setReportReason(e.target.value)}
                  placeholder="Inappropriate content, misleading photo, prohibited item..."
                  className="w-full p-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-xs focus:outline-none focus:ring-2 focus:ring-red-500"
                />

                <div className="flex gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setIsReportModalOpen(false)}
                    className="flex-1 py-2.5 rounded-full border border-neutral-300 dark:border-neutral-700 text-xs font-semibold"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmittingReport || !reportReason.trim()}
                    className="flex-1 py-2.5 rounded-full bg-red-600 hover:bg-red-700 text-white text-xs font-semibold shadow-md disabled:opacity-50"
                  >
                    {isSubmittingReport ? "Submitting..." : "Submit Report"}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
