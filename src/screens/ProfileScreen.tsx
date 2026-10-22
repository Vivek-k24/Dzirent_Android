import React, { useState } from "react";
import {
  User,
  MapPin,
  ShieldCheck,
  LogOut,
  Edit2,
  Check,
  Loader2,
  Database,
  Moon,
  Sun,
  Shield,
  HelpCircle,
} from "lucide-react";
import { UserRole } from "../types/database";
import { DzirentService, AuthUser } from "../lib/supabaseClient";

interface ProfileScreenProps {
  currentUser: AuthUser;
  onProfileUpdated: (user: AuthUser) => void;
  onRequestChangeLocation: () => void;
  onOpenSupabaseModal: () => void;
  onSignOut: () => void;
  isDarkMode: boolean;
  onToggleDarkMode: () => void;
}

export const ProfileScreen: React.FC<ProfileScreenProps> = ({
  currentUser,
  onProfileUpdated,
  onRequestChangeLocation,
  onOpenSupabaseModal,
  onSignOut,
  isDarkMode,
  onToggleDarkMode,
}) => {
  const [isEditingName, setIsEditingName] = useState(false);
  const [displayName, setDisplayName] = useState(currentUser.profile.display_name);
  const [isSaving, setIsSaving] = useState(false);

  const handleUpdateName = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!displayName.trim() || isSaving) return;

    setIsSaving(true);
    try {
      const updated = await DzirentService.updateProfile(currentUser.id, {
        display_name: displayName.trim(),
      });
      onProfileUpdated({ ...currentUser, profile: updated });
      setIsEditingName(false);
    } catch (e) {
      console.error("Update name error:", e);
    } finally {
      setIsSaving(false);
    }
  };

  const handleRoleChange = async (newRole: UserRole) => {
    if (newRole === currentUser.profile.role) return;
    try {
      const updated = await DzirentService.updateProfile(currentUser.id, { role: newRole });
      onProfileUpdated({ ...currentUser, profile: updated });
    } catch (e) {
      console.error("Update role error:", e);
    }
  };

  return (
    <div className="flex-1 flex flex-col bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-neutral-100 overflow-y-auto pb-28">
      {/* Top Header */}
      <div className="p-4 bg-white dark:bg-neutral-900 border-b border-neutral-200 dark:border-neutral-800 sticky top-0 z-20">
        <h2 className="text-xl font-bold tracking-tight">Profile & Settings</h2>
      </div>

      <div className="p-4 space-y-5 max-w-lg mx-auto w-full">
        {/* User Card */}
        <div className="p-5 rounded-3xl bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 shadow-xs space-y-4">
          <div className="flex items-center space-x-4">
            <div className="w-16 h-16 rounded-3xl bg-blue-600 text-white font-extrabold text-2xl flex items-center justify-center shadow-lg shadow-blue-500/20 shrink-0">
              {currentUser.profile.display_name.charAt(0).toUpperCase()}
            </div>

            <div className="flex-1 min-w-0">
              {isEditingName ? (
                <form onSubmit={handleUpdateName} className="flex items-center space-x-2">
                  <input
                    type="text"
                    required
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    className="flex-1 px-3 py-1.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-sm font-semibold focus:outline-none"
                  />
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="p-2 rounded-xl bg-blue-600 text-white hover:bg-blue-700 shrink-0"
                  >
                    {isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                  </button>
                </form>
              ) : (
                <div className="flex items-center space-x-2">
                  <h3 className="text-lg font-bold truncate">
                    {currentUser.profile.display_name}
                  </h3>
                  <button
                    onClick={() => setIsEditingName(true)}
                    className="p-1 text-neutral-400 hover:text-neutral-700 dark:hover:text-neutral-200"
                  >
                    <Edit2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
              <p className="text-xs text-neutral-500 truncate">{currentUser.email}</p>
            </div>
          </div>

          <div className="pt-2 border-t border-neutral-100 dark:border-neutral-800 flex items-center justify-between text-xs text-neutral-500">
            <div className="flex items-center space-x-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-500" />
              <span>Age 18+ Confirmed</span>
            </div>
            <span>Member since {new Date(currentUser.profile.created_at).toLocaleDateString([], { month: "short", year: "numeric" })}</span>
          </div>
        </div>

        {/* Location Section */}
        <div className="p-4 rounded-3xl bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2.5">
              <div className="w-9 h-9 rounded-2xl bg-blue-100 dark:bg-blue-950 flex items-center justify-center text-blue-600 dark:text-blue-400">
                <MapPin className="w-4 h-4" />
              </div>
              <div>
                <h4 className="text-sm font-bold">Your Rental Location</h4>
                <p className="text-xs text-neutral-500">
                  {currentUser.profile.city} (approx {currentUser.profile.approx_lat}, {currentUser.profile.approx_lng})
                </p>
              </div>
            </div>

            <button
              onClick={onRequestChangeLocation}
              className="py-1.5 px-3 rounded-full border border-neutral-300 dark:border-neutral-700 hover:bg-neutral-100 dark:hover:bg-neutral-800 text-xs font-semibold text-blue-600 dark:text-blue-400 transition-colors"
            >
              Update
            </button>
          </div>
        </div>

        {/* Role Switcher: Renter / Lender / Both */}
        <div className="p-4 rounded-3xl bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 space-y-3">
          <h4 className="text-sm font-bold">Account Role</h4>
          <p className="text-xs text-neutral-500">
            Choose how you want to interact with the Dzirent community:
          </p>

          <div className="grid grid-cols-3 gap-2">
            {(["renter", "lender", "both"] as UserRole[]).map((r) => (
              <button
                key={r}
                onClick={() => handleRoleChange(r)}
                className={`py-3 px-2 rounded-2xl border text-xs font-semibold capitalize transition-all ${
                  currentUser.profile.role === r
                    ? "bg-blue-50 border-blue-600 text-blue-700 dark:bg-blue-950/60 dark:border-blue-500 dark:text-blue-300 shadow-xs"
                    : "border-neutral-200 dark:border-neutral-800 bg-neutral-50 dark:bg-neutral-800/40 text-neutral-600 dark:text-neutral-400 hover:border-neutral-300"
                }`}
              >
                {r === "both" ? "Both (Rent & Lend)" : `${r} only`}
              </button>
            ))}
          </div>
        </div>

        {/* App Preferences & Backend */}
        <div className="p-4 rounded-3xl bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 space-y-2">
          <h4 className="text-sm font-bold mb-2">Preferences & Backend</h4>

          {/* Supabase backend config trigger */}
          <button
            onClick={onOpenSupabaseModal}
            className="w-full flex items-center justify-between p-2.5 rounded-2xl hover:bg-neutral-50 dark:hover:bg-neutral-800/60 text-xs text-neutral-700 dark:text-neutral-300 transition-colors"
          >
            <div className="flex items-center space-x-2.5">
              <Database className="w-4 h-4 text-blue-600 dark:text-blue-400" />
              <span className="font-medium">Supabase Configuration</span>
            </div>
            <span className="text-neutral-400 font-medium">View / Edit →</span>
          </button>

          {/* Theme Switcher */}
          <button
            onClick={onToggleDarkMode}
            className="w-full flex items-center justify-between p-2.5 rounded-2xl hover:bg-neutral-50 dark:hover:bg-neutral-800/60 text-xs text-neutral-700 dark:text-neutral-300 transition-colors"
          >
            <div className="flex items-center space-x-2.5">
              {isDarkMode ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4 text-neutral-600" />}
              <span className="font-medium">Theme Mode</span>
            </div>
            <span className="text-neutral-400 font-medium">{isDarkMode ? "Dark" : "Light"}</span>
          </button>
        </div>

        {/* Sign Out */}
        <button
          onClick={onSignOut}
          className="w-full py-3.5 px-6 rounded-full border border-red-200 dark:border-red-900/60 hover:bg-red-50 dark:hover:bg-red-950/40 text-red-600 dark:text-red-400 font-semibold text-sm flex items-center justify-center space-x-2 transition-colors m3-button"
        >
          <LogOut className="w-4 h-4" />
          <span>Sign Out</span>
        </button>
      </div>
    </div>
  );
};
