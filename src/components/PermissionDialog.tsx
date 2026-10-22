import React from "react";
import { MapPin, Camera, Image as ImageIcon, Shield } from "lucide-react";

export type PermissionType = "location" | "camera" | "photo";

interface PermissionDialogProps {
  type: PermissionType;
  isOpen: boolean;
  onAllow: () => void;
  onDeny: () => void;
}

export const PermissionDialog: React.FC<PermissionDialogProps> = ({
  type,
  isOpen,
  onAllow,
  onDeny,
}) => {
  if (!isOpen) return null;

  const content = {
    location: {
      title: "Use your location?",
      reason: "Dzirent finds clothing listings near you within walking or driving distance.",
      icon: <MapPin className="w-8 h-8 text-blue-600 dark:text-blue-400" />,
      subtext: "Your exact address is never stored; coordinates are rounded to ~1 km.",
    },
    camera: {
      title: "Allow camera access?",
      reason: "Take high-quality photos of your clothing pieces directly to add to your listing.",
      icon: <Camera className="w-8 h-8 text-blue-600 dark:text-blue-400" />,
      subtext: "Photos are compressed to JPEG ~80% before uploading.",
    },
    photo: {
      title: "Access photo gallery?",
      reason: "Select clear photos of outfits, tags, and measurements from your photo library.",
      icon: <ImageIcon className="w-8 h-8 text-blue-600 dark:text-blue-400" />,
      subtext: "Only photos you choose will be uploaded.",
    },
  }[type];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-4">
        <div className="w-12 h-12 rounded-2xl bg-blue-50 dark:bg-blue-950/60 flex items-center justify-center mx-auto">
          {content.icon}
        </div>

        <div className="text-center space-y-1.5">
          <h3 className="text-lg font-bold text-neutral-900 dark:text-neutral-100">
            {content.title}
          </h3>
          <p className="text-neutral-600 dark:text-neutral-300 text-sm leading-relaxed">
            {content.reason}
          </p>
          <div className="pt-2 flex items-center justify-center space-x-1.5 text-xs text-neutral-500 dark:text-neutral-400">
            <Shield className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
            <span>{content.subtext}</span>
          </div>
        </div>

        <div className="pt-3 flex flex-col sm:flex-row gap-2.5">
          <button
            onClick={onDeny}
            className="flex-1 py-2.5 px-4 rounded-full border border-neutral-300 dark:border-neutral-700 font-medium text-sm text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors m3-button"
          >
            Not now
          </button>
          <button
            onClick={onAllow}
            className="flex-1 py-2.5 px-4 rounded-full bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm shadow-md transition-colors m3-button"
          >
            Allow
          </button>
        </div>
      </div>
    </div>
  );
};
