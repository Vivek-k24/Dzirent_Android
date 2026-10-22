import React, { useState, useEffect } from "react";
import { MessageSquare, RefreshCw, AlertCircle, Loader2, Sparkles } from "lucide-react";
import { Conversation } from "../types/database";
import { DzirentService, AuthUser } from "../lib/supabaseClient";

interface InboxScreenProps {
  currentUser: AuthUser;
  onSelectConversation: (conversationId: string, listingId: string) => void;
  onExplore: () => void;
}

export const InboxScreen: React.FC<InboxScreenProps> = ({
  currentUser,
  onSelectConversation,
  onExplore,
}) => {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchConversations = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await DzirentService.getConversations(currentUser.id);
      setConversations(data);
    } catch (e: any) {
      setErrorMessage(e?.message || "Failed to load messages");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchConversations();
  }, [currentUser.id]);

  return (
    <div className="flex-1 flex flex-col bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-neutral-100 overflow-y-auto pb-24">
      {/* Top Header */}
      <div className="p-4 bg-white dark:bg-neutral-900 border-b border-neutral-200 dark:border-neutral-800 flex items-center justify-between sticky top-0 z-20">
        <div>
          <h2 className="text-xl font-bold tracking-tight">Inbox</h2>
          <p className="text-xs text-neutral-500">Your direct peer rental conversations</p>
        </div>
        <button
          onClick={fetchConversations}
          className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-500"
          title="Refresh"
        >
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>

      <div className="p-4 flex-1 flex flex-col">
        {/* Loading state */}
        {isLoading && (
          <div className="flex-1 flex flex-col items-center justify-center p-8 space-y-3">
            <Loader2 className="w-8 h-8 text-blue-600 animate-spin" />
            <p className="text-xs text-neutral-500">Loading conversations...</p>
          </div>
        )}

        {/* Error state with Retry */}
        {!isLoading && errorMessage && (
          <div className="my-auto py-12 px-4 text-center space-y-3">
            <div className="w-12 h-12 rounded-full bg-red-100 dark:bg-red-950/60 text-red-600 dark:text-red-400 flex items-center justify-center mx-auto">
              <AlertCircle className="w-6 h-6" />
            </div>
            <h4 className="text-base font-bold">Could not load inbox</h4>
            <p className="text-xs text-neutral-500 max-w-xs mx-auto">{errorMessage}</p>
            <button
              onClick={fetchConversations}
              className="py-2 px-5 rounded-full bg-blue-600 text-white text-xs font-semibold shadow-md inline-flex items-center space-x-1.5"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Retry</span>
            </button>
          </div>
        )}

        {/* Empty state */}
        {!isLoading && !errorMessage && conversations.length === 0 && (
          <div className="my-auto py-16 px-4 text-center space-y-4">
            <div className="w-16 h-16 rounded-3xl bg-neutral-100 dark:bg-neutral-800 flex items-center justify-center mx-auto text-neutral-400 dark:text-neutral-500">
              <MessageSquare className="w-8 h-8 stroke-1" />
            </div>
            <div className="space-y-1">
              <h3 className="text-base font-bold">No messages yet</h3>
              <p className="text-xs text-neutral-500 max-w-xs mx-auto">
                Find an outfit you like nearby and message the lender to ask questions or arrange rental!
              </p>
            </div>
            <button
              onClick={onExplore}
              className="py-3 px-6 rounded-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md inline-flex items-center space-x-1.5 m3-button"
            >
              <Sparkles className="w-4 h-4" />
              <span>Browse outfits</span>
            </button>
          </div>
        )}

        {/* Conversations List */}
        {!isLoading && !errorMessage && conversations.length > 0 && (
          <div className="space-y-2.5">
            {conversations.map((conv) => {
              const coverPhoto =
                conv.listing?.photos && conv.listing.photos[0]
                  ? conv.listing.photos[0].storage_path
                  : null;

              const otherName = conv.other_user?.display_name || "Community Member";
              const lastMsgText = conv.last_message?.body || "Tap to chat about this listing";
              const msgTime = conv.last_message?.created_at
                ? new Date(conv.last_message.created_at).toLocaleTimeString([], {
                    hour: "numeric",
                    minute: "2-digit",
                  })
                : new Date(conv.created_at).toLocaleDateString([], {
                    month: "short",
                    day: "numeric",
                  });

              return (
                <div
                  key={conv.id}
                  onClick={() => onSelectConversation(conv.id, conv.listing_id)}
                  className="bg-white dark:bg-neutral-900 rounded-3xl p-3.5 border border-neutral-200 dark:border-neutral-800 shadow-xs hover:shadow-md transition-all flex items-center space-x-3.5 cursor-pointer group"
                >
                  {/* Listing thumbnail */}
                  <div className="w-14 h-16 rounded-2xl bg-neutral-200 dark:bg-neutral-800 overflow-hidden shrink-0 relative">
                    {coverPhoto ? (
                      <img
                        src={coverPhoto}
                        alt="Outfit"
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-[10px] text-neutral-400">
                        Outfit
                      </div>
                    )}
                  </div>

                  {/* Conversation snippet */}
                  <div className="flex-1 min-w-0 space-y-0.5">
                    <div className="flex items-center justify-between">
                      <h4 className="font-bold text-sm text-neutral-900 dark:text-neutral-100 truncate">
                        {otherName}
                      </h4>
                      <span className="text-[10px] text-neutral-400 shrink-0 font-medium">
                        {msgTime}
                      </span>
                    </div>

                    <p className="text-xs font-semibold text-blue-600 dark:text-blue-400 truncate">
                      {conv.listing?.title || "Rental outfit"}
                    </p>

                    <p className="text-xs text-neutral-500 dark:text-neutral-400 truncate">
                      {lastMsgText}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
