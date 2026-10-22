import React, { useState } from "react";
import { Database, Check, AlertCircle, RefreshCw, X, KeyRound, ExternalLink } from "lucide-react";
import {
  getEffectiveSupabaseConfig,
  saveCustomSupabaseConfig,
  clearCustomSupabaseConfig,
} from "../lib/supabaseConfig";
import { DzirentService } from "../lib/supabaseClient";

interface SupabaseSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfigChanged: () => void;
}

export const SupabaseSettingsModal: React.FC<SupabaseSettingsModalProps> = ({
  isOpen,
  onClose,
  onConfigChanged,
}) => {
  const current = getEffectiveSupabaseConfig();
  const [url, setUrl] = useState(current.url);
  const [key, setKey] = useState(current.anonKey);
  const [statusMsg, setStatusMsg] = useState<string | null>(null);
  const [isTesting, setIsTesting] = useState(false);

  if (!isOpen) return null;

  const handleSave = () => {
    saveCustomSupabaseConfig(url, key);
    setStatusMsg("Configuration saved!");
    onConfigChanged();
    setTimeout(() => {
      onClose();
    }, 800);
  };

  const handleResetToPlaceholders = () => {
    clearCustomSupabaseConfig();
    const defaults = getEffectiveSupabaseConfig();
    setUrl(defaults.url);
    setKey(defaults.anonKey);
    setStatusMsg("Reset to code defaults.");
    onConfigChanged();
  };

  const handleTestConnection = async () => {
    setIsTesting(true);
    setStatusMsg(null);
    try {
      saveCustomSupabaseConfig(url, key);
      const res = await DzirentService.getListings({ limit: 1 });
      setStatusMsg(`Connected successfully! Found ${res.listings.length} live listings.`);
      onConfigChanged();
    } catch (e: any) {
      setStatusMsg(`Connection test error: ${e?.message || "Failed to reach Supabase"}`);
    } finally {
      setIsTesting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl p-6 max-w-md w-full shadow-2xl space-y-4">
        <div className="flex items-center justify-between border-b border-neutral-100 dark:border-neutral-800 pb-3">
          <div className="flex items-center space-x-2.5">
            <div className="w-9 h-9 rounded-xl bg-blue-100 dark:bg-blue-950/80 flex items-center justify-center text-blue-600 dark:text-blue-400">
              <Database className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-base text-neutral-900 dark:text-neutral-100">
                Supabase Backend
              </h3>
              <p className="text-xs text-neutral-500">Official Supabase configuration</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-500"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-3 text-sm">
          <div className="p-3 bg-neutral-50 dark:bg-neutral-800/60 rounded-2xl border border-neutral-200 dark:border-neutral-700/60 text-xs space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-neutral-700 dark:text-neutral-300">
                Status:
              </span>
              <span
                className={`font-medium px-2 py-0.5 rounded-full ${
                  current.isConfigured
                    ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300"
                    : "bg-amber-100 text-amber-800 dark:bg-amber-950/80 dark:text-amber-300"
                }`}
              >
                {current.isConfigured ? "Connected (Live Cloud)" : "Config Placeholder / Standalone"}
              </span>
            </div>
            <p className="text-neutral-500 pt-1">
              {current.isConfigured
                ? "All read/write operations sync directly with your live Supabase project."
                : "SUPABASE_URL and SUPABASE_ANON_KEY are set in src/lib/supabaseConfig.ts with clear placeholders. You can supply live keys here to test against your live database."}
            </p>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300 flex items-center space-x-1">
              <span>SUPABASE_URL</span>
            </label>
            <input
              type="text"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://your-project-id.supabase.co"
              className="w-full px-3.5 py-2.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300 flex items-center space-x-1">
              <KeyRound className="w-3.5 h-3.5" />
              <span>SUPABASE_ANON_KEY</span>
            </label>
            <input
              type="password"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              placeholder="eyJhbGciOi..."
              className="w-full px-3.5 py-2.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {statusMsg && (
            <div className="p-2.5 rounded-xl text-xs bg-blue-50 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-900 flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{statusMsg}</span>
            </div>
          )}
        </div>

        <div className="pt-2 flex flex-col gap-2">
          <div className="flex gap-2">
            <button
              onClick={handleTestConnection}
              disabled={isTesting}
              className="flex-1 py-2.5 px-3 rounded-full border border-neutral-300 dark:border-neutral-700 hover:bg-neutral-100 dark:hover:bg-neutral-800 text-xs font-semibold flex items-center justify-center space-x-1.5 m3-button disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isTesting ? "animate-spin" : ""}`} />
              <span>Test Connection</span>
            </button>
            <button
              onClick={handleSave}
              className="flex-1 py-2.5 px-3 rounded-full bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-md flex items-center justify-center space-x-1.5 m3-button"
            >
              <Check className="w-3.5 h-3.5" />
              <span>Save & Apply</span>
            </button>
          </div>
          <button
            onClick={handleResetToPlaceholders}
            className="text-center text-xs text-neutral-500 hover:text-neutral-800 dark:hover:text-neutral-300 underline py-1"
          >
            Reset to code placeholders
          </button>
        </div>
      </div>
    </div>
  );
};
