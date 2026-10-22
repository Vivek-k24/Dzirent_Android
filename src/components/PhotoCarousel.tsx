import React, { useState } from "react";
import { ChevronLeft, ChevronRight, Image as ImageIcon } from "lucide-react";
import { ListingPhoto } from "../types/database";

interface PhotoCarouselProps {
  photos?: ListingPhoto[];
  title: string;
}

export const PhotoCarousel: React.FC<PhotoCarouselProps> = ({ photos = [], title }) => {
  const [currentIndex, setCurrentIndex] = useState(0);

  if (photos.length === 0) {
    return (
      <div className="w-full aspect-4/3 bg-neutral-100 dark:bg-neutral-800 rounded-3xl flex flex-col items-center justify-center text-neutral-400 dark:text-neutral-500">
        <ImageIcon className="w-12 h-12 mb-2 stroke-1" />
        <span className="text-sm font-medium">No photo available</span>
      </div>
    );
  }

  const prev = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCurrentIndex((curr) => (curr === 0 ? photos.length - 1 : curr - 1));
  };

  const next = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCurrentIndex((curr) => (curr === photos.length - 1 ? 0 : curr + 1));
  };

  return (
    <div className="relative w-full aspect-4/3 bg-neutral-900 rounded-3xl overflow-hidden group select-none shadow-md">
      <img
        src={photos[currentIndex]?.storage_path}
        alt={`${title} - Photo ${currentIndex + 1}`}
        className="w-full h-full object-cover transition-opacity duration-300"
        loading="lazy"
        onError={(e) => {
          (e.currentTarget as HTMLImageElement).src =
            "https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=800&auto=format&fit=crop&q=80";
        }}
      />

      {/* Navigation arrows */}
      {photos.length > 1 && (
        <>
          <button
            onClick={prev}
            aria-label="Previous photo"
            className="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/40 hover:bg-black/70 text-white flex items-center justify-center backdrop-blur-xs transition-opacity sm:opacity-0 sm:group-hover:opacity-100"
          >
            <ChevronLeft className="w-5 h-5" />
          </button>
          <button
            onClick={next}
            aria-label="Next photo"
            className="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/40 hover:bg-black/70 text-white flex items-center justify-center backdrop-blur-xs transition-opacity sm:opacity-0 sm:group-hover:opacity-100"
          >
            <ChevronRight className="w-5 h-5" />
          </button>

          {/* Dot Indicators */}
          <div className="absolute bottom-3 inset-x-0 flex items-center justify-center space-x-1.5">
            {photos.map((_, idx) => (
              <button
                key={idx}
                onClick={(e) => {
                  e.stopPropagation();
                  setCurrentIndex(idx);
                }}
                className={`transition-all rounded-full ${
                  idx === currentIndex
                    ? "w-5 h-2 bg-white shadow-sm"
                    : "w-2 h-2 bg-white/50 hover:bg-white/80"
                }`}
                aria-label={`Jump to photo ${idx + 1}`}
              />
            ))}
          </div>

          {/* Photo Counter Pill */}
          <div className="absolute top-3 right-3 bg-black/60 backdrop-blur-xs text-white text-xs px-2.5 py-1 rounded-full font-medium">
            {currentIndex + 1} / {photos.length}
          </div>
        </>
      )}
    </div>
  );
};
