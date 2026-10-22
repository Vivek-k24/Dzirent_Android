import React from "react";
import {
  MapPin,
  Moon,
  Sun,
  Database,
  Smartphone,
  Maximize2,
} from "lucide-react";
import { getEffectiveSupabaseConfig } from "../lib/supabaseConfig";

interface HeaderProps {
  city?: string;
  isDarkMode: boolean;
  isFrameEnabled: boolean;
  onToggleDarkMode: () => void;
  onToggleFrame: () => void;
  onClickLocation: () => void;
  onClickSupabase: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  city,
  isDarkMode,
  isFrameEnabled,
  onToggleDarkMode,
  onToggleFrame,
  onClickLocation,
  onClickSupabase,
}) => {
  const { isConfigured } = getEffectiveSupabaseConfig();

  return (
    <header className="sticky top-0 inset-x-0 bg-white/95 dark:bg-neutral-900/95 backdrop-blur-md border-b border-neutral-200 dark:border-neutral-800 z-30 px-4 py-2.5 flex items-center justify-between shadow-xs transition-colors">
      <div className="flex items-center space-x-2">
        <div className="w-8 h-8 rounded-xl bg-blue-600 text-white flex items-center justify-center font-bold text-sm tracking-wider shadow-sm">
          DZ
        </div>
        <div className="flex flex-col">
          <span className="font-bold text-base tracking-tight leading-none text-neutral-900 dark:text-neutral-100">
            Dzirent
          </span>
          <button
            onClick={onClickLocation}
            className="flex items-center space-x-1 text-[11px] text-neutral-500 hover:text-blue-600 dark:text-neutral-400 dark:hover:text-blue-400 font-medium transition-colors pt-0.5 text-left"
          >
            <MapPin className="w-3 h-3 text-blue-500 shrink-0" />
            <span className="truncate max-w-[120px]">{city || "Set location"}</span>
          </button>
        </div>
      </div>

      <div className="flex items-center space-x-1.5">
        {/* Supabase backend status pill */}
        <button
          onClick={onClickSupabase}
          title="Supabase Backend Config"
          className={`flex items-center space-x-1 px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${
            isConfigured
              ? "bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/60 dark:text-emerald-300 dark:border-emerald-800"
              : "bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/60 dark:text-blue-300 dark:border-blue-800"
          }`}
        >
          <Database className="w-3 h-3" />
          <span className="hidden sm:inline">{isConfigured ? "Supabase Live" : "Supabase"}</span>
        </button>

        {/* Frame Toggle */}
        <button
          onClick={onToggleFrame}
          title={isFrameEnabled ? "Switch to Fullscreen" : "Switch to Android Phone Preview"}
          className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-600 dark:text-neutral-300 transition-colors"
        >
          {isFrameEnabled ? <Maximize2 className="w-4 h-4" /> : <Smartphone className="w-4 h-4" />}
        </button>

        {/* Dark Mode Toggle */}
        <button
          onClick={onToggleDarkMode}
          title={isDarkMode ? "Switch to Light Mode" : "Switch to Dark Mode"}
          className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-600 dark:text-neutral-300 transition-colors"
        >
          {isDarkMode ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4" />}
        </button>
      </div>
    </header>
  );
};
