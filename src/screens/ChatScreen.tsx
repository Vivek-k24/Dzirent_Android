import React, { useState, useEffect, useRef } from "react";
import {
  ArrowLeft,
  MoreVertical,
  Send,
  Shield,
  ShieldAlert,
  UserX,
  AlertCircle,
  Loader2,
  MapPin,
  ExternalLink,
} from "lucide-react";
import { Listing, Message, Conversation, Profile } from "../types/database";
import { DzirentService, AuthUser } from "../lib/supabaseClient";
import { RentalFollowUpCard } from "../components/RentalFollowUpCard";

interface ChatScreenProps {
  conversationId: string;
  listingId: string;
  currentUser: AuthUser;
  onBack: () => void;
  onViewListing: (listingId: string) => void;
}

export const ChatScreen: React.FC<ChatScreenProps> = ({
  conversationId,
  listingId,
  currentUser,
  onBack,
  onViewListing,
}) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputText, setInputText] = useState("");
  const [listing, setListing] = useState<Listing | null>(null);
  const [otherUser, setOtherUser] = useState<Profile | null>(null);
  const [conversation, setConversation] = useState<Conversation | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSending, setIsSending] = useState(false);

  // Overflow menu & reporting/blocking state
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [actionNotice, setActionNotice] = useState<string | null>(null);
  const [isReportOpen, setIsReportOpen] = useState(false);
  const [reportReason, setReportReason] = useState("");

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = (behavior: ScrollBehavior = "smooth") => {
    messagesEndRef.current?.scrollIntoView({ behavior });
  };

  useEffect(() => {
    let mounted = true;

    async function loadChat() {
      setIsLoading(true);
      try {
        const [msgs, listDetail, convs] = await Promise.all([
          DzirentService.getMessages(conversationId),
          DzirentService.getListingDetail(listingId),
          DzirentService.getConversations(currentUser.id),
        ]);

        if (mounted) {
          setMessages(msgs);
          setListing(listDetail);

          const currentConv = convs.find((c) => c.id === conversationId);
          if (currentConv) {
            setConversation(currentConv);
            setOtherUser(currentConv.other_user || null);
          } else if (listDetail?.lender) {
            setOtherUser(listDetail.lender);
          }
          setIsLoading(false);
          setTimeout(() => scrollToBottom("auto"), 100);
        }
      } catch (e) {
        console.error("Error loading chat:", e);
        if (mounted) setIsLoading(false);
      }
    }

    loadChat();

    // Subscribe to real-time messages via Supabase Realtime
    const unsubscribe = DzirentService.subscribeToMessages(conversationId, (newMsg) => {
      if (mounted) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === newMsg.id)) return prev;
          return [...prev, newMsg];
        });
        setTimeout(() => scrollToBottom(), 80);
      }
    });

    return () => {
      mounted = false;
      unsubscribe();
    };
  }, [conversationId, listingId, currentUser.id]);

  const handleSendMessage = async (textToSend: string) => {
    const text = textToSend.trim();
    if (!text || isSending) return;

    setInputText("");
    setIsSending(true);

    try {
      const sent = await DzirentService.sendMessage(conversationId, currentUser.id, text);
      setMessages((prev) => {
        if (prev.some((m) => m.id === sent.id)) return prev;
        return [...prev, sent];
      });
      setTimeout(() => scrollToBottom(), 50);
    } catch (e) {
      console.error("Failed to send message:", e);
    } finally {
      setIsSending(false);
    }
  };

  const handleBlockUser = async () => {
    if (!otherUser) return;
    setIsMenuOpen(false);
    try {
      await DzirentService.blockUser(currentUser.id, otherUser.id);
      setActionNotice(`Blocked ${otherUser.display_name}. You won't see their listings or messages.`);
    } catch (e) {
      console.error("Block error:", e);
    }
  };

  const handleReportUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reportReason.trim() || !otherUser) return;

    try {
      await DzirentService.report({
        reporter_id: currentUser.id,
        target_type: "user",
        target_id: otherUser.id,
        reason: reportReason.trim(),
      });
      setIsReportOpen(false);
      setReportReason("");
      setActionNotice("Report submitted. Our moderation team will review this user.");
    } catch (e) {
      console.error("Report error:", e);
    }
  };

  return (
    <div className="flex-1 flex flex-col bg-neutral-100 dark:bg-neutral-950 text-neutral-900 dark:text-neutral-100 h-full overflow-hidden">
      {/* Top App Bar with Other Person & Overflow Menu */}
      <div className="bg-white/95 dark:bg-neutral-900/95 backdrop-blur-md border-b border-neutral-200 dark:border-neutral-800 px-4 py-2.5 flex items-center justify-between z-30 shrink-0">
        <div className="flex items-center space-x-3">
          <button
            onClick={onBack}
            className="p-1.5 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-700 dark:text-neutral-300"
            aria-label="Back"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h3 className="font-bold text-sm text-neutral-900 dark:text-neutral-100 leading-tight">
              {otherUser?.display_name || "Peer"}
            </h3>
            <span className="text-[11px] text-neutral-500 flex items-center space-x-1">
              <MapPin className="w-2.5 h-2.5 text-blue-500" />
              <span>{otherUser?.city || "Nearby"}</span>
            </span>
          </div>
        </div>

        <div className="relative">
          <button
            onClick={() => setIsMenuOpen(!isMenuOpen)}
            className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-600 dark:text-neutral-400"
          >
            <MoreVertical className="w-5 h-5" />
          </button>

          {isMenuOpen && (
            <div className="absolute right-0 mt-1 w-44 bg-white dark:bg-neutral-800 border border-neutral-200 dark:border-neutral-700 rounded-2xl shadow-xl py-1.5 z-40 text-xs">
              <button
                onClick={handleBlockUser}
                className="w-full px-4 py-2 text-left flex items-center space-x-2 text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-700/60"
              >
                <UserX className="w-4 h-4 text-neutral-500" />
                <span>Block user</span>
              </button>
              <button
                onClick={() => {
                  setIsMenuOpen(false);
                  setIsReportOpen(true);
                }}
                className="w-full px-4 py-2 text-left flex items-center space-x-2 text-red-600 hover:bg-red-50 dark:hover:bg-red-950/50"
              >
                <ShieldAlert className="w-4 h-4" />
                <span>Report</span>
              </button>
            </div>
          )}
        </div>
      </div>

      {/* PINNED LISTING BANNER AT THE TOP */}
      {listing && (
        <div
          onClick={() => onViewListing(listing.id)}
          className="bg-white dark:bg-neutral-900 px-4 py-2 border-b border-neutral-200 dark:border-neutral-800 flex items-center justify-between cursor-pointer hover:bg-neutral-50 dark:hover:bg-neutral-800/80 transition-colors shrink-0 shadow-xs"
        >
          <div className="flex items-center space-x-3 min-w-0">
            <div className="w-10 h-12 rounded-xl bg-neutral-200 dark:bg-neutral-800 overflow-hidden shrink-0">
              {listing.photos && listing.photos[0] ? (
                <img
                  src={listing.photos[0].storage_path}
                  alt={listing.title}
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-[9px] text-neutral-400">
                  Outfit
                </div>
              )}
            </div>
            <div className="min-w-0">
              <span className="text-[10px] uppercase font-bold text-blue-600 dark:text-blue-400 block">
                {listing.category} · Size {listing.size_label}
              </span>
              <h4 className="font-bold text-xs text-neutral-900 dark:text-neutral-100 truncate">
                {listing.title}
              </h4>
              <p className="text-[11px] font-semibold text-neutral-600 dark:text-neutral-400">
                {listing.price_note || "Direct Agreement"}
              </p>
            </div>
          </div>
          <ExternalLink className="w-4 h-4 text-neutral-400 shrink-0 ml-2" />
        </div>
      )}

      {/* SAFETY TIP BANNER: "Meet in a public place. Never send money in advance." */}
      <div className="bg-amber-50 dark:bg-amber-950/40 border-b border-amber-200/80 dark:border-amber-900/60 px-4 py-2 flex items-center space-x-2 text-xs text-amber-900 dark:text-amber-200 shrink-0">
        <Shield className="w-4 h-4 text-amber-600 dark:text-amber-400 shrink-0" />
        <span className="font-medium text-[11px] leading-tight">
          Safety tip: Meet in a public place. Never send money in advance.
        </span>
      </div>

      {/* Action Notice (e.g. Blocked or Reported) */}
      {actionNotice && (
        <div className="m-3 p-3 rounded-2xl bg-blue-50 dark:bg-blue-950/60 border border-blue-200 dark:border-blue-900 text-xs text-blue-700 dark:text-blue-300 flex items-center justify-between">
          <span>{actionNotice}</span>
          <button
            onClick={() => setActionNotice(null)}
            className="text-blue-500 font-bold ml-2"
          >
            ✕
          </button>
        </div>
      )}

      {/* RENTAL FOLLOW-UP CARD (shown 3 days after conversation created) */}
      {conversation && (
        <RentalFollowUpCard
          conversationId={conversation.id}
          userId={currentUser.id}
          createdAt={conversation.created_at}
        />
      )}

      {/* MESSAGES SCROLL CONTAINER */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {isLoading && (
          <div className="flex justify-center p-8">
            <Loader2 className="w-6 h-6 text-blue-600 animate-spin" />
          </div>
        )}

        {!isLoading && messages.length === 0 && (
          <div className="text-center py-10 space-y-2">
            <p className="text-xs text-neutral-500">
              Start the conversation! Tap a quick suggestion below or write a custom message.
            </p>
          </div>
        )}

        {messages.map((m) => {
          const isMe = m.sender_id === currentUser.id;
          const time = new Date(m.created_at).toLocaleTimeString([], {
            hour: "numeric",
            minute: "2-digit",
          });

          return (
            <div
              key={m.id}
              className={`flex flex-col ${isMe ? "items-end" : "items-start"}`}
            >
              <div
                className={`max-w-[78%] px-4 py-2.5 rounded-3xl text-sm leading-relaxed shadow-xs ${
                  isMe
                    ? "bg-blue-600 text-white rounded-br-xs"
                    : "bg-white dark:bg-neutral-800 text-neutral-900 dark:text-neutral-100 border border-neutral-200/80 dark:border-neutral-700/80 rounded-bl-xs"
                }`}
              >
                <p className="whitespace-pre-wrap break-words">{m.body}</p>
              </div>
              <span className="text-[10px] text-neutral-400 mt-1 px-1">{time}</span>
            </div>
          );
        })}

        <div ref={messagesEndRef} />
      </div>

      {/* QUICK CHIPS: "Is this available on these dates?" & "Can I see more photos?" */}
      <div className="px-4 py-1.5 bg-white/90 dark:bg-neutral-900/90 backdrop-blur-md flex items-center space-x-2 overflow-x-auto no-scrollbar shrink-0 border-t border-neutral-100 dark:border-neutral-800">
        <button
          onClick={() => handleSendMessage("Hi! Is this available on these dates?")}
          className="whitespace-nowrap px-3 py-1.5 rounded-full text-xs font-semibold bg-neutral-100 dark:bg-neutral-800 text-neutral-700 dark:text-neutral-300 hover:bg-neutral-200 dark:hover:bg-neutral-700 transition-colors shrink-0"
        >
          "Is this available on these dates?"
        </button>
        <button
          onClick={() => handleSendMessage("Can I see more photos of this piece?")}
          className="whitespace-nowrap px-3 py-1.5 rounded-full text-xs font-semibold bg-neutral-100 dark:bg-neutral-800 text-neutral-700 dark:text-neutral-300 hover:bg-neutral-200 dark:hover:bg-neutral-700 transition-colors shrink-0"
        >
          "Can I see more photos?"
        </button>
      </div>

      {/* INPUT BAR */}
      <div className="p-3 bg-white dark:bg-neutral-900 border-t border-neutral-200 dark:border-neutral-800 shrink-0 pb-safe">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSendMessage(inputText);
          }}
          className="flex items-center space-x-2 max-w-lg mx-auto"
        >
          <input
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            placeholder="Type a message..."
            className="flex-1 px-4 py-2.5 rounded-full bg-neutral-100 dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="submit"
            disabled={!inputText.trim() || isSending}
            className="w-10 h-10 rounded-full bg-blue-600 hover:bg-blue-700 text-white flex items-center justify-center shadow-md transition-colors disabled:opacity-40 shrink-0"
            aria-label="Send message"
          >
            {isSending ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Send className="w-4 h-4 ml-0.5" />
            )}
          </button>
        </form>
      </div>

      {/* Report Modal */}
      {isReportOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-4">
            <h3 className="font-bold text-base text-red-600 dark:text-red-400 flex items-center space-x-2">
              <ShieldAlert className="w-5 h-5" />
              <span>Report User</span>
            </h3>
            <p className="text-xs text-neutral-500">
              Please describe why you are reporting this conversation:
            </p>
            <form onSubmit={handleReportUser} className="space-y-3">
              <textarea
                required
                rows={3}
                value={reportReason}
                onChange={(e) => setReportReason(e.target.value)}
                placeholder="Spam, harassment, request for advance payment..."
                className="w-full p-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-xs focus:outline-none focus:ring-2 focus:ring-red-500"
              />
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setIsReportOpen(false)}
                  className="flex-1 py-2.5 rounded-full border border-neutral-300 dark:border-neutral-700 text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2.5 rounded-full bg-red-600 text-white text-xs font-semibold"
                >
                  Submit
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
