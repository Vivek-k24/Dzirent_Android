import React from "react";
import { Wifi, Signal, BatteryMedium } from "lucide-react";

interface AndroidDeviceFrameProps {
  children: React.ReactNode;
  enabled: boolean;
}

export const AndroidDeviceFrame: React.FC<AndroidDeviceFrameProps> = ({ children, enabled }) => {
  if (!enabled) {
    return <div className="min-h-screen bg-neutral-100 dark:bg-neutral-950">{children}</div>;
  }

  // Format current local time (e.g. 7:15)
  const currentTime = new Date().toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });

  return (
    <div className="min-h-screen bg-neutral-900 py-4 sm:py-8 px-2 flex justify-center items-center">
      {/* Google Pixel / Android Material 3 Device Frame */}
      <div className="w-full max-w-[420px] h-[880px] max-h-[96vh] bg-white dark:bg-neutral-900 rounded-[44px] shadow-2xl border-[10px] border-neutral-800 dark:border-neutral-800 flex flex-col overflow-hidden relative ring-1 ring-white/10">
        {/* Status Bar */}
        <div className="h-10 bg-white/90 dark:bg-neutral-900/90 backdrop-blur-md px-6 flex items-center justify-between z-30 shrink-0 text-xs font-medium text-neutral-800 dark:text-neutral-200 select-none">
          <span>{currentTime}</span>

          {/* Camera punch hole cutout */}
          <div className="w-4 h-4 rounded-full bg-neutral-900 dark:bg-black border border-neutral-700/60 shadow-inner flex items-center justify-center">
            <div className="w-1.5 h-1.5 rounded-full bg-blue-900/40" />
          </div>

          <div className="flex items-center space-x-1.5 opacity-90">
            <Signal className="w-3.5 h-3.5" />
            <Wifi className="w-3.5 h-3.5" />
            <BatteryMedium className="w-4 h-4" />
          </div>
        </div>

        {/* Device screen viewport */}
        <div className="flex-1 overflow-y-auto relative flex flex-col">
          {children}
        </div>

        {/* Android Gesture Bar */}
        <div className="h-5 bg-white dark:bg-neutral-900 shrink-0 flex items-center justify-center pointer-events-none z-30">
          <div className="w-28 h-1 bg-neutral-400 dark:bg-neutral-600 rounded-full" />
        </div>
      </div>
    </div>
  );
};
