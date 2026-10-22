import React, { useState, useEffect } from "react";
import { WifiOff, RefreshCw } from "lucide-react";

export const NetworkBanner: React.FC = () => {
  const [isOffline, setIsOffline] = useState(!navigator.onLine);
  const [isRetrying, setIsRetrying] = useState(false);

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  const handleRetry = () => {
    setIsRetrying(true);
    setTimeout(() => {
      setIsOffline(!navigator.onLine);
      setIsRetrying(false);
    }, 600);
  };

  if (!isOffline) return null;

  return (
    <div className="bg-red-600 text-white px-4 py-2.5 text-sm flex items-center justify-between shadow-md transition-all">
      <div className="flex items-center space-x-2">
        <WifiOff className="w-4 h-4 shrink-0" />
        <span className="font-medium text-xs sm:text-sm">No internet connection</span>
      </div>
      <button
        onClick={handleRetry}
        disabled={isRetrying}
        className="flex items-center space-x-1.5 bg-red-700 hover:bg-red-800 px-3 py-1 rounded text-xs font-semibold uppercase tracking-wider transition-colors disabled:opacity-50"
      >
        <RefreshCw className={`w-3 h-3 ${isRetrying ? "animate-spin" : ""}`} />
        <span>Retry</span>
      </button>
    </div>
  );
};
