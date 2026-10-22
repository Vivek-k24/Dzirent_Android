/**
 * Location utilities for Dzirent
 * - Rounds latitude and longitude to 2 decimals (~1 km) before saving for privacy
 * - Reverse geocoding city lookup
 * - Fallback coordinates for common cities when manual input is provided
 * - Haversine distance calculation
 */

export function roundToTwoDecimals(num: number): number {
  return Math.round(num * 100) / 100;
}

export function calculateDistanceKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371; // Earth's radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c * 10) / 10;
}

export async function reverseGeocodeCity(lat: number, lng: number): Promise<string> {
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=10&addressdetails=1`,
      {
        headers: {
          "Accept-Language": "en",
        },
      }
    );
    if (!res.ok) throw new Error("Geocoding failed");
    const data = await res.json();
    const city =
      data.address?.city ||
      data.address?.town ||
      data.address?.village ||
      data.address?.municipality ||
      data.address?.suburb ||
      data.address?.state_district ||
      data.address?.state ||
      "Nearby City";
    return city;
  } catch {
    return "Nearby City";
  }
}

export async function geocodeCityInput(cityName: string): Promise<{ city: string; lat: number; lng: number }> {
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(cityName)}&limit=1`,
      {
        headers: {
          "Accept-Language": "en",
        },
      }
    );
    if (res.ok) {
      const data = await res.json();
      if (data && data.length > 0) {
        return {
          city: data[0].display_name.split(",")[0].trim() || cityName,
          lat: roundToTwoDecimals(parseFloat(data[0].lat)),
          lng: roundToTwoDecimals(parseFloat(data[0].lon)),
        };
      }
    }
  } catch {
    // fallback
  }

  // Fallback defaults
  return {
    city: cityName.trim(),
    lat: 37.77,
    lng: -122.42,
  };
}
