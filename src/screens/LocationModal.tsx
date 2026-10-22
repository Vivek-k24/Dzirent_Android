import React, { useState } from "react";
import { MapPin, Navigation, Search, Check, AlertCircle, Loader2, ShieldCheck, X } from "lucide-react";
import { roundToTwoDecimals, reverseGeocodeCity, geocodeCityInput } from "../lib/locationUtils";

interface LocationModalProps {
  isOpen: boolean;
  currentCity?: string;
  onSave: (city: string, lat: number, lng: number) => void;
  onClose?: () => void;
  canDismiss?: boolean;
}

export const LocationModal: React.FC<LocationModalProps> = ({
  isOpen,
  currentCity,
  onSave,
  onClose,
  canDismiss = true,
}) => {
  const [manualCity, setManualCity] = useState(currentCity || "");
  const [isLocating, setIsLocating] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleUseDeviceLocation = () => {
    setIsLocating(true);
    setErrorMsg(null);
    setStatusMessage("Finding nearby location via GPS...");

    if (!navigator.geolocation) {
      setIsLocating(false);
      setErrorMsg("Location services not supported. Please type your city.");
      return;
    }

    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        // Round to 2 decimals (~1km) before saving
        const roundedLat = roundToTwoDecimals(pos.coords.latitude);
        const roundedLng = roundToTwoDecimals(pos.coords.longitude);

        setStatusMessage("Looking up nearest city...");
        const cityName = await reverseGeocodeCity(roundedLat, roundedLng);

        setIsLocating(false);
        setStatusMessage(null);
        onSave(cityName, roundedLat, roundedLng);
      },
      (err) => {
        setIsLocating(false);
        setStatusMessage(null);
        if (err.code === err.PERMISSION_DENIED) {
          setErrorMsg("Location permission denied. Please enter your city below.");
        } else {
          setErrorMsg("Could not detect location. Please enter your city below.");
        }
      },
      { timeout: 10000, enableHighAccuracy: false }
    );
  };

  const handleSaveManualCity = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!manualCity.trim()) return;

    setIsLocating(true);
    setErrorMsg(null);
    try {
      const { city, lat, lng } = await geocodeCityInput(manualCity.trim());
      setIsLocating(false);
      onSave(city, lat, lng);
    } catch {
      setIsLocating(false);
      onSave(manualCity.trim(), 37.77, -122.42);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
      <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-3xl p-6 max-w-sm w-full shadow-2xl space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2.5">
            <div className="w-10 h-10 rounded-2xl bg-blue-100 dark:bg-blue-950 flex items-center justify-center text-blue-600 dark:text-blue-400">
              <MapPin className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-base text-neutral-900 dark:text-neutral-100">
                Your Rental Area
              </h3>
              <p className="text-xs text-neutral-500">Show clothing near you</p>
            </div>
          </div>
          {canDismiss && onClose && (
            <button
              onClick={onClose}
              className="p-1.5 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-500"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Privacy Note */}
        <div className="flex items-start space-x-2 p-2.5 rounded-2xl bg-neutral-50 dark:bg-neutral-800/60 border border-neutral-100 dark:border-neutral-800 text-xs text-neutral-600 dark:text-neutral-300">
          <ShieldCheck className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
          <span>
            Coordinates are rounded to 2 decimals (~1 km) before saving. Exact addresses are never stored.
          </span>
        </div>

        {/* Option 1: GPS / Fused Provider */}
        <button
          type="button"
          onClick={handleUseDeviceLocation}
          disabled={isLocating}
          className="w-full py-3.5 px-4 rounded-2xl bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm shadow-md flex items-center justify-center space-x-2 transition-all m3-button disabled:opacity-50"
        >
          {isLocating ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <Navigation className="w-4 h-4" />
          )}
          <span>Use Current Location</span>
        </button>

        {statusMessage && (
          <p className="text-center text-xs text-blue-600 dark:text-blue-400 font-medium animate-pulse">
            {statusMessage}
          </p>
        )}

        <div className="flex items-center space-x-2 text-xs text-neutral-400 py-1">
          <div className="flex-1 h-px bg-neutral-200 dark:bg-neutral-800" />
          <span>or enter manually</span>
          <div className="flex-1 h-px bg-neutral-200 dark:bg-neutral-800" />
        </div>

        {/* Option 2: Manual City */}
        <form onSubmit={handleSaveManualCity} className="space-y-3">
          <div className="relative">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-neutral-400" />
            <input
              type="text"
              required
              value={manualCity}
              onChange={(e) => setManualCity(e.target.value)}
              placeholder="e.g. Austin, Brooklyn, Chicago"
              className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {errorMsg && (
            <div className="p-2.5 rounded-xl bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-900 text-xs flex items-center space-x-1.5">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          <button
            type="submit"
            disabled={!manualCity.trim() || isLocating}
            className="w-full py-2.5 rounded-full border border-neutral-300 dark:border-neutral-700 hover:bg-neutral-100 dark:hover:bg-neutral-800 text-xs font-semibold flex items-center justify-center space-x-1.5 transition-colors disabled:opacity-40"
          >
            <Check className="w-3.5 h-3.5" />
            <span>Set City</span>
          </button>
        </form>
      </div>
    </div>
  );
};
