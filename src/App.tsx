import React, { useState, useEffect } from "react";
import {
  AuthUser,
  getActiveUser,
  setActiveUser,
  DzirentService,
} from "./lib/supabaseClient";
import { Header } from "./components/Header";
import { BottomNav, NavTab } from "./components/BottomNav";
import { AndroidDeviceFrame } from "./components/AndroidDeviceFrame";
import { NetworkBanner } from "./components/NetworkBanner";
import { SupabaseSettingsModal } from "./components/SupabaseSettingsModal";
import { WelcomeScreen } from "./screens/WelcomeScreen";
import { SignUpScreen } from "./screens/SignUpScreen";
import { LoginScreen } from "./screens/LoginScreen";
import { LocationModal } from "./screens/LocationModal";
import { BrowseScreen } from "./screens/BrowseScreen";
import { ListingDetailScreen } from "./screens/ListingDetailScreen";
import { CreateListingScreen } from "./screens/CreateListingScreen";
import { MyClosetScreen } from "./screens/MyClosetScreen";
import { InboxScreen } from "./screens/InboxScreen";
import { ChatScreen } from "./screens/ChatScreen";
import { ProfileScreen } from "./screens/ProfileScreen";
import { Listing, UserRole } from "./types/database";

export const App: React.FC = () => {
  const [currentUser, setCurrentUserState] = useState<AuthUser | null>(getActiveUser());
  const [authView, setAuthView] = useState<"welcome" | "signup" | "login">("welcome");
  const [currentTab, setCurrentTab] = useState<NavTab>("browse");

  // Navigation Stack for detail screens
  const [selectedListingId, setSelectedListingId] = useState<string | null>(null);
  const [activeChat, setActiveChat] = useState<{ conversationId: string; listingId: string } | null>(null);

  // Modals & UI Toggles
  const [isLocationModalOpen, setIsLocationModalOpen] = useState(false);
  const [isSupabaseModalOpen, setIsSupabaseModalOpen] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const [isFrameEnabled, setIsFrameEnabled] = useState(true);

  // Sync dark mode class
  useEffect(() => {
    if (isDarkMode) {
      document.documentElement.classList.add("dark");
    } else {
      document.documentElement.classList.remove("dark");
    }
  }, [isDarkMode]);

  const handleUpdateUser = (user: AuthUser | null) => {
    setCurrentUserState(user);
    setActiveUser(user);
  };

  const handleSignUpSuccess = (role: UserRole) => {
    const updated = getActiveUser();
    if (updated) {
      setCurrentUserState(updated);
      setIsLocationModalOpen(true); // Ask for location right after signup
    }
  };

  const handleLoginSuccess = () => {
    const user = getActiveUser();
    setCurrentUserState(user);
  };

  const handleSignOut = async () => {
    await DzirentService.signOut();
    handleUpdateUser(null);
    setAuthView("welcome");
    setSelectedListingId(null);
    setActiveChat(null);
    setCurrentTab("browse");
  };

  const handleLocationSaved = async (city: string, lat: number, lng: number) => {
    if (currentUser) {
      try {
        const updated = await DzirentService.updateProfile(currentUser.id, {
          city,
          approx_lat: lat,
          approx_lng: lng,
        });
        handleUpdateUser({ ...currentUser, profile: updated });
      } catch (e) {
        console.error("Location update failed:", e);
      }
    }
    setIsLocationModalOpen(false);
  };

  const handleStartChatFromListing = async (listing: Listing) => {
    if (!currentUser) return;
    try {
      const conv = await DzirentService.getOrCreateConversation(
        listing.id,
        currentUser.id,
        listing.lender_id
      );
      setSelectedListingId(null);
      setActiveChat({ conversationId: conv.id, listingId: listing.id });
    } catch (e) {
      console.error("Failed to start chat:", e);
    }
  };

  // RENDER AUTH FLOW IF NOT LOGGED IN
  if (!currentUser) {
    return (
      <AndroidDeviceFrame enabled={isFrameEnabled}>
        <NetworkBanner />
        <Header
          isDarkMode={isDarkMode}
          isFrameEnabled={isFrameEnabled}
          onToggleDarkMode={() => setIsDarkMode(!isDarkMode)}
          onToggleFrame={() => setIsFrameEnabled(!isFrameEnabled)}
          onClickLocation={() => setIsLocationModalOpen(true)}
          onClickSupabase={() => setIsSupabaseModalOpen(true)}
        />

        {authView === "welcome" && (
          <WelcomeScreen
            onGoToSignUp={() => setAuthView("signup")}
            onGoToLogin={() => setAuthView("login")}
          />
        )}

        {authView === "signup" && (
          <SignUpScreen
            onSuccess={handleSignUpSuccess}
            onGoToLogin={() => setAuthView("login")}
            onBack={() => setAuthView("welcome")}
          />
        )}

        {authView === "login" && (
          <LoginScreen
            onSuccess={handleLoginSuccess}
            onGoToSignUp={() => setAuthView("signup")}
            onBack={() => setAuthView("welcome")}
          />
        )}

        <SupabaseSettingsModal
          isOpen={isSupabaseModalOpen}
          onClose={() => setIsSupabaseModalOpen(false)}
          onConfigChanged={() => {}}
        />
      </AndroidDeviceFrame>
    );
  }

  // AUTHENTICATED USER FLOW
  return (
    <AndroidDeviceFrame enabled={isFrameEnabled}>
      <NetworkBanner />

      {/* Show header unless inside full-screen chat */}
      {!activeChat && (
        <Header
          city={currentUser.profile.city}
          isDarkMode={isDarkMode}
          isFrameEnabled={isFrameEnabled}
          onToggleDarkMode={() => setIsDarkMode(!isDarkMode)}
          onToggleFrame={() => setIsFrameEnabled(!isFrameEnabled)}
          onClickLocation={() => setIsLocationModalOpen(true)}
          onClickSupabase={() => setIsSupabaseModalOpen(true)}
        />
      )}

      {/* SCREEN ROUTING */}
      <div className="flex-1 flex flex-col min-h-0 relative">
        {/* Detail: Chat Screen */}
        {activeChat ? (
          <ChatScreen
            conversationId={activeChat.conversationId}
            listingId={activeChat.listingId}
            currentUser={currentUser}
            onBack={() => setActiveChat(null)}
            onViewListing={(id) => {
              setSelectedListingId(id);
              setActiveChat(null);
            }}
          />
        ) : selectedListingId ? (
          /* Detail: Listing Detail Screen */
          <ListingDetailScreen
            listingId={selectedListingId}
            currentUser={currentUser}
            onBack={() => setSelectedListingId(null)}
            onStartChat={handleStartChatFromListing}
          />
        ) : currentTab === "browse" ? (
          /* Tab: Browse */
          <BrowseScreen
            currentUser={currentUser}
            onSelectListing={(id) => setSelectedListingId(id)}
            onCreateListing={() => setCurrentTab("list")}
            onRequestChangeLocation={() => setIsLocationModalOpen(true)}
          />
        ) : currentTab === "inbox" ? (
          /* Tab: Inbox */
          <InboxScreen
            currentUser={currentUser}
            onSelectConversation={(conversationId, listingId) =>
              setActiveChat({ conversationId, listingId })
            }
            onExplore={() => setCurrentTab("browse")}
          />
        ) : currentTab === "list" ? (
          /* Tab: Create Listing */
          <CreateListingScreen
            currentUser={currentUser}
            onBack={() => setCurrentTab("browse")}
            onSuccess={(newId) => {
              setSelectedListingId(newId);
              setCurrentTab("browse");
            }}
          />
        ) : currentTab === "closet" ? (
          /* Tab: My Closet */
          <MyClosetScreen
            currentUser={currentUser}
            onSelectListing={(id) => setSelectedListingId(id)}
            onCreateListing={() => setCurrentTab("list")}
          />
        ) : (
          /* Tab: Profile */
          <ProfileScreen
            currentUser={currentUser}
            onProfileUpdated={handleUpdateUser}
            onRequestChangeLocation={() => setIsLocationModalOpen(true)}
            onOpenSupabaseModal={() => setIsSupabaseModalOpen(true)}
            onSignOut={handleSignOut}
            isDarkMode={isDarkMode}
            onToggleDarkMode={() => setIsDarkMode(!isDarkMode)}
          />
        )}
      </div>

      {/* Bottom Navigation (Hidden when on detail/chat screen) */}
      {!selectedListingId && !activeChat && (
        <BottomNav
          currentTab={currentTab}
          role={currentUser.profile.role}
          onSelectTab={(tab) => {
            setSelectedListingId(null);
            setActiveChat(null);
            setCurrentTab(tab);
          }}
        />
      )}

      {/* Location Modal */}
      <LocationModal
        isOpen={isLocationModalOpen}
        currentCity={currentUser.profile.city}
        onSave={handleLocationSaved}
        onClose={() => setIsLocationModalOpen(false)}
        canDismiss={Boolean(currentUser.profile.city)}
      />

      {/* Supabase Settings Modal */}
      <SupabaseSettingsModal
        isOpen={isSupabaseModalOpen}
        onClose={() => setIsSupabaseModalOpen(false)}
        onConfigChanged={() => {}}
      />
    </AndroidDeviceFrame>
  );
};

export default App;
