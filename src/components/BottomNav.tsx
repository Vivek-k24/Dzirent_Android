import React from "react";
import { Search, MessageSquare, PlusCircle, Sparkles, User } from "lucide-react";
import { UserRole } from "../types/database";

export type NavTab = "browse" | "inbox" | "list" | "closet" | "profile";

interface BottomNavProps {
  currentTab: NavTab;
  role: UserRole;
  unreadCount?: number;
  onSelectTab: (tab: NavTab) => void;
}

export const BottomNav: React.FC<BottomNavProps> = ({
  currentTab,
  role,
  unreadCount = 0,
  onSelectTab,
}) => {
  const canLend = role === "lender" || role === "both";

  return (
    <nav className="fixed bottom-0 inset-x-0 bg-white/95 dark:bg-neutral-900/95 backdrop-blur-md border-t border-neutral-200 dark:border-neutral-800 z-40 pb-safe shadow-lg">
      <div className="max-w-md mx-auto flex items-center justify-around h-16 px-2">
        {/* 1. Browse */}
        <button
          onClick={() => onSelectTab("browse")}
          className={`flex-1 flex flex-col items-center justify-center py-1 transition-colors relative ${
            currentTab === "browse"
              ? "text-blue-600 dark:text-blue-400 font-semibold"
              : "text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
          }`}
        >
          <div
            className={`w-12 h-7 rounded-full flex items-center justify-center transition-all ${
              currentTab === "browse"
                ? "bg-blue-100 dark:bg-blue-950/80"
                : "bg-transparent"
            }`}
          >
            <Search className="w-5 h-5" />
          </div>
          <span className="text-[11px] mt-0.5 tracking-tight">Browse</span>
        </button>

        {/* 2. Inbox */}
        <button
          onClick={() => onSelectTab("inbox")}
          className={`flex-1 flex flex-col items-center justify-center py-1 transition-colors relative ${
            currentTab === "inbox"
              ? "text-blue-600 dark:text-blue-400 font-semibold"
              : "text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
          }`}
        >
          <div
            className={`w-12 h-7 rounded-full flex items-center justify-center transition-all relative ${
              currentTab === "inbox"
                ? "bg-blue-100 dark:bg-blue-950/80"
                : "bg-transparent"
            }`}
          >
            <MessageSquare className="w-5 h-5" />
            {unreadCount > 0 && (
              <span className="absolute top-0.5 right-2 w-2 h-2 rounded-full bg-blue-600 ring-2 ring-white dark:ring-neutral-900" />
            )}
          </div>
          <span className="text-[11px] mt-0.5 tracking-tight">Inbox</span>
        </button>

        {/* 3. List (Only if role is lender or both) */}
        {canLend && (
          <button
            onClick={() => onSelectTab("list")}
            className={`flex-1 flex flex-col items-center justify-center py-1 transition-colors relative ${
              currentTab === "list"
                ? "text-blue-600 dark:text-blue-400 font-semibold"
                : "text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
            }`}
          >
            <div
              className={`w-12 h-7 rounded-full flex items-center justify-center transition-all ${
                currentTab === "list"
                  ? "bg-blue-100 dark:bg-blue-950/80"
                  : "bg-transparent"
              }`}
            >
              <PlusCircle className="w-5 h-5" />
            </div>
            <span className="text-[11px] mt-0.5 tracking-tight">List</span>
          </button>
        )}

        {/* 4. My Closet */}
        <button
          onClick={() => onSelectTab("closet")}
          className={`flex-1 flex flex-col items-center justify-center py-1 transition-colors relative ${
            currentTab === "closet"
              ? "text-blue-600 dark:text-blue-400 font-semibold"
              : "text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
          }`}
        >
          <div
            className={`w-12 h-7 rounded-full flex items-center justify-center transition-all ${
              currentTab === "closet"
                ? "bg-blue-100 dark:bg-blue-950/80"
                : "bg-transparent"
            }`}
          >
            <Sparkles className="w-5 h-5" />
          </div>
          <span className="text-[11px] mt-0.5 tracking-tight">My Closet</span>
        </button>

        {/* 5. Profile */}
        <button
          onClick={() => onSelectTab("profile")}
          className={`flex-1 flex flex-col items-center justify-center py-1 transition-colors relative ${
            currentTab === "profile"
              ? "text-blue-600 dark:text-blue-400 font-semibold"
              : "text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200"
          }`}
        >
          <div
            className={`w-12 h-7 rounded-full flex items-center justify-center transition-all ${
              currentTab === "profile"
                ? "bg-blue-100 dark:bg-blue-950/80"
                : "bg-transparent"
            }`}
          >
            <User className="w-5 h-5" />
          </div>
          <span className="text-[11px] mt-0.5 tracking-tight">Profile</span>
        </button>
      </div>
    </nav>
  );
};
