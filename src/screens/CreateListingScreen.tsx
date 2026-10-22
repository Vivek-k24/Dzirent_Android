import React, { useState, useRef } from "react";
import {
  ArrowLeft,
  Camera,
  Image as ImageIcon,
  Plus,
  Trash2,
  ChevronLeft,
  ChevronRight,
  AlertCircle,
  Loader2,
  Check,
  Calendar,
  Ruler,
  HelpCircle,
} from "lucide-react";
import {
  CATEGORIES,
  CATEGORY_MEASUREMENT_SUGGESTIONS,
  MeasurementUnit,
} from "../types/database";
import { DzirentService, AuthUser } from "../lib/supabaseClient";
import { processImageForUpload } from "../lib/imageUtils";
import { PermissionDialog, PermissionType } from "../components/PermissionDialog";

interface PhotoItem {
  id: string;
  blob?: Blob;
  dataUrl: string;
}

interface MeasurementRow {
  label: string;
  value: number;
  unit: MeasurementUnit;
}

interface AvailabilityRange {
  startDate: string;
  endDate: string;
}

interface CreateListingScreenProps {
  currentUser: AuthUser;
  onBack: () => void;
  onSuccess: (listingId: string) => void;
}

export const CreateListingScreen: React.FC<CreateListingScreenProps> = ({
  currentUser,
  onBack,
  onSuccess,
}) => {
  const [photos, setPhotos] = useState<PhotoItem[]>([]);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState<string>(CATEGORIES[0]);
  const [sizeLabel, setSizeLabel] = useState("");
  const [description, setDescription] = useState("");
  const [priceNote, setPriceNote] = useState("");
  const [measurements, setMeasurements] = useState<MeasurementRow[]>([]);
  const [availabilities, setAvailabilities] = useState<AvailabilityRange[]>([
    {
      startDate: new Date().toISOString().split("T")[0],
      endDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
    },
  ]);

  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Permission dialog
  const [permissionType, setPermissionType] = useState<PermissionType | null>(null);
  const [pendingSource, setPendingSource] = useState<"camera" | "gallery" | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);

  // Quick suggestions for current category
  const suggestions = CATEGORY_MEASUREMENT_SUGGESTIONS[category] || [
    "Shoulder",
    "Chest",
    "Length",
    "Waist",
  ];

  const handleOpenPhotoPicker = (source: "camera" | "gallery") => {
    if (photos.length >= 6) {
      alert("Maximum 6 photos allowed per listing.");
      return;
    }
    setPendingSource(source);
    setPermissionType(source === "camera" ? "camera" : "photo");
  };

  const handlePermissionAllow = () => {
    const source = pendingSource;
    setPermissionType(null);
    setPendingSource(null);

    if (source === "camera" && cameraInputRef.current) {
      cameraInputRef.current.click();
    } else if (fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const remainingSlots = 6 - photos.length;
    const selectedFiles = Array.from(files).slice(0, remainingSlots);

    for (const file of selectedFiles) {
      try {
        const { blob, dataUrl } = await processImageForUpload(file);
        setPhotos((prev) => [
          ...prev,
          {
            id: Math.random().toString(36).substring(7),
            blob,
            dataUrl,
          },
        ]);
      } catch (err) {
        console.error("Error processing image:", err);
      }
    }

    if (e.target) e.target.value = "";
  };

  const movePhoto = (index: number, direction: "left" | "right") => {
    const target = direction === "left" ? index - 1 : index + 1;
    if (target < 0 || target >= photos.length) return;
    const updated = [...photos];
    const temp = updated[index];
    updated[index] = updated[target];
    updated[target] = temp;
    setPhotos(updated);
  };

  const removePhoto = (index: number) => {
    setPhotos((prev) => prev.filter((_, i) => i !== index));
  };

  // Measurement helpers
  const addMeasurement = (labelName = "") => {
    setMeasurements((prev) => [
      ...prev,
      { label: labelName || "Length", value: 30, unit: "in" },
    ]);
  };

  const updateMeasurement = (
    index: number,
    field: keyof MeasurementRow,
    value: any
  ) => {
    setMeasurements((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], [field]: value };
      return updated;
    });
  };

  const removeMeasurement = (index: number) => {
    setMeasurements((prev) => prev.filter((_, i) => i !== index));
  };

  // Availability helpers
  const addAvailabilityRange = () => {
    const start = new Date().toISOString().split("T")[0];
    const end = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0];
    setAvailabilities((prev) => [...prev, { startDate: start, endDate: end }]);
  };

  const updateAvailability = (
    index: number,
    field: "startDate" | "endDate",
    value: string
  ) => {
    setAvailabilities((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], [field]: value };
      return updated;
    });
  };

  const removeAvailability = (index: number) => {
    setAvailabilities((prev) => prev.filter((_, i) => i !== index));
  };

  // Validation
  const validateForm = (): boolean => {
    const errors: string[] = [];

    if (photos.length === 0) {
      errors.push("Add at least 1 photo of your outfit (up to 6 photos)");
    }
    if (!title.trim()) {
      errors.push("Enter an outfit title (e.g., Silk Halter Evening Gown)");
    }
    if (!category) {
      errors.push("Select a category");
    }
    if (!sizeLabel.trim()) {
      errors.push("Enter size label (e.g. S, M, US 4, 38R)");
    }
    if (!priceNote.trim()) {
      errors.push("Enter price/rental note (e.g. $25 for weekend)");
    }
    if (!description.trim()) {
      errors.push("Provide a brief description of condition and style");
    }

    setValidationErrors(errors);
    return errors.length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) {
      window.scrollTo({ top: 0, behavior: "smooth" });
      return;
    }

    setIsSubmitting(true);
    try {
      const newListing = await DzirentService.createListing({
        lenderId: currentUser.id,
        title: title.trim(),
        category,
        sizeLabel: sizeLabel.trim(),
        description: description.trim(),
        priceNote: priceNote.trim(),
        city: currentUser.profile.city || "Local Area",
        lat: currentUser.profile.approx_lat || 37.77,
        lng: currentUser.profile.approx_lng || -122.42,
        photos: photos.map((p) => ({ blob: p.blob, dataUrl: p.dataUrl })),
        measurements: measurements.map((m) => ({
          label: m.label,
          value: Number(m.value) || 0,
          unit: m.unit,
        })),
        availabilities,
      });

      onSuccess(newListing.id);
    } catch (err: any) {
      console.error("Listing creation error:", err);
      setValidationErrors([err?.message || "Failed to save listing. Please check connection."]);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-neutral-100 overflow-y-auto pb-28">
      {/* Top App Bar */}
      <div className="sticky top-0 inset-x-0 bg-white/95 dark:bg-neutral-900/95 backdrop-blur-md border-b border-neutral-200 dark:border-neutral-800 z-30 px-4 py-2.5 flex items-center space-x-3">
        <button
          onClick={onBack}
          className="p-2 rounded-full hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-700 dark:text-neutral-300"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <h2 className="font-bold text-base">List an Outfit</h2>
      </div>

      {/* Hidden file inputs for Camera and Gallery */}
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        accept="image/*"
        multiple
        className="hidden"
      />
      <input
        type="file"
        ref={cameraInputRef}
        onChange={handleFileChange}
        accept="image/*"
        capture="environment"
        className="hidden"
      />

      <form onSubmit={handleSubmit} className="p-4 space-y-6 max-w-lg mx-auto w-full">
        {/* VALIDATION SUMMARY */}
        {validationErrors.length > 0 && (
          <div className="p-4 rounded-2xl bg-red-50 dark:bg-red-950/60 border border-red-200 dark:border-red-900 text-xs text-red-700 dark:text-red-300 space-y-1.5 animate-in fade-in">
            <div className="flex items-center space-x-1.5 font-bold">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-600" />
              <span>Please complete the missing required fields:</span>
            </div>
            <ul className="list-disc pl-5 space-y-0.5">
              {validationErrors.map((err, i) => (
                <li key={i}>{err}</li>
              ))}
            </ul>
          </div>
        )}

        {/* 1. PHOTOS (1-6 photos with reorder & delete) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-sm font-bold text-neutral-900 dark:text-neutral-100">
              Photos ({photos.length}/6)
            </label>
            <span className="text-xs text-neutral-500">
              {photos.length === 0 ? "At least 1 photo required" : "First photo is cover"}
            </span>
          </div>

          <div className="grid grid-cols-3 gap-2.5">
            {photos.map((item, idx) => (
              <div
                key={item.id}
                className="relative aspect-square rounded-2xl overflow-hidden bg-neutral-200 dark:bg-neutral-800 border-2 border-transparent hover:border-blue-500 group shadow-xs"
              >
                <img
                  src={item.dataUrl}
                  alt={`Upload ${idx + 1}`}
                  className="w-full h-full object-cover"
                />

                {idx === 0 && (
                  <span className="absolute top-1.5 left-1.5 bg-blue-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full shadow-xs">
                    Cover
                  </span>
                )}

                {/* Delete button */}
                <button
                  type="button"
                  onClick={() => removePhoto(idx)}
                  className="absolute top-1.5 right-1.5 w-6 h-6 rounded-full bg-black/70 hover:bg-red-600 text-white flex items-center justify-center transition-colors shadow-xs"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>

                {/* Reorder arrows */}
                <div className="absolute bottom-1.5 inset-x-1.5 flex justify-between">
                  {idx > 0 && (
                    <button
                      type="button"
                      onClick={() => movePhoto(idx, "left")}
                      className="w-6 h-6 rounded-full bg-black/60 text-white flex items-center justify-center"
                    >
                      <ChevronLeft className="w-3.5 h-3.5" />
                    </button>
                  )}
                  {idx < photos.length - 1 && (
                    <button
                      type="button"
                      onClick={() => movePhoto(idx, "right")}
                      className="w-6 h-6 rounded-full bg-black/60 text-white flex items-center justify-center ml-auto"
                    >
                      <ChevronRight className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
            ))}

            {photos.length < 6 && (
              <div className="flex flex-col gap-2">
                <button
                  type="button"
                  onClick={() => handleOpenPhotoPicker("gallery")}
                  className="flex-1 min-h-[56px] rounded-2xl border-2 border-dashed border-neutral-300 dark:border-neutral-700 hover:border-blue-500 dark:hover:border-blue-400 bg-white dark:bg-neutral-900 flex flex-col items-center justify-center text-xs text-neutral-600 dark:text-neutral-400 font-medium transition-colors m3-button p-2"
                >
                  <ImageIcon className="w-4 h-4 text-blue-600 dark:text-blue-400 mb-0.5" />
                  <span>Gallery</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleOpenPhotoPicker("camera")}
                  className="flex-1 min-h-[56px] rounded-2xl border-2 border-dashed border-neutral-300 dark:border-neutral-700 hover:border-blue-500 dark:hover:border-blue-400 bg-white dark:bg-neutral-900 flex flex-col items-center justify-center text-xs text-neutral-600 dark:text-neutral-400 font-medium transition-colors m3-button p-2"
                >
                  <Camera className="w-4 h-4 text-blue-600 dark:text-blue-400 mb-0.5" />
                  <span>Camera</span>
                </button>
              </div>
            )}
          </div>
        </div>

        {/* 2. TITLE & CATEGORY */}
        <div className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Outfit Title *
            </label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Navy Double-Breasted Italian Wool Suit"
              className="w-full px-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Category *
            </label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="w-full px-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 cursor-pointer"
            >
              {CATEGORIES.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* 3. SIZE LABEL & PRICE NOTE */}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Size Label *
            </label>
            <input
              type="text"
              required
              value={sizeLabel}
              onChange={(e) => setSizeLabel(e.target.value)}
              placeholder="e.g. M, US 6, 40R"
              className="w-full px-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
              Price Note *
            </label>
            <input
              type="text"
              required
              value={priceNote}
              onChange={(e) => setPriceNote(e.target.value)}
              placeholder="e.g. $20 for 3 days"
              className="w-full px-4 py-3 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* 4. DESCRIPTION */}
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
            Description *
          </label>
          <textarea
            required
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe fabric, fit, styling tips, dry-cleaning requirements..."
            className="w-full p-4 rounded-2xl border border-neutral-300 dark:border-neutral-700 bg-white dark:bg-neutral-800 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* 5. MEASUREMENTS DYNAMIC LIST + QUICK ADD SUGGESTIONS */}
        <div className="space-y-3 pt-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-1.5">
              <Ruler className="w-4 h-4 text-blue-600 dark:text-blue-400" />
              <label className="text-sm font-bold text-neutral-900 dark:text-neutral-100">
                Exact Measurements
              </label>
            </div>
            <button
              type="button"
              onClick={() => addMeasurement()}
              className="text-xs font-bold text-blue-600 dark:text-blue-400 hover:underline flex items-center space-x-1"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add row</span>
            </button>
          </div>

          {/* Category Suggestions */}
          <div className="space-y-1">
            <span className="text-[11px] text-neutral-500">
              Quick suggestions for {category}:
            </span>
            <div className="flex flex-wrap gap-1.5">
              {suggestions.map((sug) => {
                const alreadyAdded = measurements.some(
                  (m) => m.label.toLowerCase() === sug.toLowerCase()
                );
                return (
                  <button
                    key={sug}
                    type="button"
                    disabled={alreadyAdded}
                    onClick={() => addMeasurement(sug)}
                    className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${
                      alreadyAdded
                        ? "bg-neutral-100 dark:bg-neutral-800 border-neutral-200 text-neutral-400 cursor-default"
                        : "bg-white dark:bg-neutral-900 border-neutral-300 dark:border-neutral-700 hover:border-blue-500 hover:text-blue-600 text-neutral-700 dark:text-neutral-300"
                    }`}
                  >
                    + {sug}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Dynamic Measurements Rows */}
          <div className="space-y-2">
            {measurements.map((m, idx) => (
              <div
                key={idx}
                className="flex items-center space-x-2 bg-white dark:bg-neutral-900 p-2.5 rounded-2xl border border-neutral-200 dark:border-neutral-800"
              >
                <input
                  type="text"
                  value={m.label}
                  onChange={(e) => updateMeasurement(idx, "label", e.target.value)}
                  placeholder="Measurement label"
                  className="flex-1 px-3 py-1.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-xs font-medium focus:outline-none focus:ring-1 focus:ring-blue-500"
                />

                <input
                  type="number"
                  step="0.5"
                  value={m.value}
                  onChange={(e) =>
                    updateMeasurement(idx, "value", parseFloat(e.target.value) || 0)
                  }
                  className="w-20 px-3 py-1.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-xs font-medium text-center focus:outline-none focus:ring-1 focus:ring-blue-500"
                />

                <select
                  value={m.unit}
                  onChange={(e) =>
                    updateMeasurement(idx, "unit", e.target.value as MeasurementUnit)
                  }
                  className="px-2.5 py-1.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-xs font-medium focus:outline-none"
                >
                  <option value="in">in</option>
                  <option value="cm">cm</option>
                </select>

                <button
                  type="button"
                  onClick={() => removeMeasurement(idx)}
                  className="p-1.5 text-neutral-400 hover:text-red-500 transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* 6. AVAILABILITY DATE RANGES */}
        <div className="space-y-3 pt-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-1.5">
              <Calendar className="w-4 h-4 text-blue-600 dark:text-blue-400" />
              <label className="text-sm font-bold text-neutral-900 dark:text-neutral-100">
                Available Rental Dates
              </label>
            </div>
            <button
              type="button"
              onClick={addAvailabilityRange}
              className="text-xs font-bold text-blue-600 dark:text-blue-400 hover:underline flex items-center space-x-1"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add range</span>
            </button>
          </div>

          <div className="space-y-2">
            {availabilities.map((range, idx) => (
              <div
                key={idx}
                className="flex items-center space-x-2 bg-white dark:bg-neutral-900 p-2.5 rounded-2xl border border-neutral-200 dark:border-neutral-800 text-xs"
              >
                <div className="flex-1 space-y-1">
                  <span className="text-[10px] text-neutral-400 block">From</span>
                  <input
                    type="date"
                    value={range.startDate}
                    onChange={(e) => updateAvailability(idx, "startDate", e.target.value)}
                    className="w-full px-2.5 py-1.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-xs font-medium focus:outline-none"
                  />
                </div>

                <div className="flex-1 space-y-1">
                  <span className="text-[10px] text-neutral-400 block">Until</span>
                  <input
                    type="date"
                    value={range.endDate}
                    onChange={(e) => updateAvailability(idx, "endDate", e.target.value)}
                    className="w-full px-2.5 py-1.5 rounded-xl border border-neutral-300 dark:border-neutral-700 bg-neutral-50 dark:bg-neutral-800 text-xs font-medium focus:outline-none"
                  />
                </div>

                <button
                  type="button"
                  onClick={() => removeAvailability(idx)}
                  className="p-1.5 text-neutral-400 hover:text-red-500 self-end mb-1"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* Location note */}
        <p className="text-xs text-neutral-500 pt-2">
          Listing location will display as{" "}
          <strong className="text-neutral-700 dark:text-neutral-300">
            {currentUser.profile.city}
          </strong>{" "}
          from your profile.
        </p>

        {/* SUBMIT BUTTON */}
        <div className="pt-4">
          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full py-4 px-6 rounded-full bg-blue-600 hover:bg-blue-700 text-white font-semibold text-base shadow-lg shadow-blue-500/25 flex items-center justify-center space-x-2 transition-all m3-button disabled:opacity-50"
          >
            {isSubmitting ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <>
                <Check className="w-5 h-5" />
                <span>Publish Listing</span>
              </>
            )}
          </button>
        </div>
      </form>

      {/* Permission Dialog */}
      {permissionType && (
        <PermissionDialog
          type={permissionType}
          isOpen={true}
          onAllow={handlePermissionAllow}
          onDeny={() => {
            setPermissionType(null);
            setPendingSource(null);
          }}
        />
      )}
    </div>
  );
};
