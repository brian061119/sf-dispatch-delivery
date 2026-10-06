// Geocoding and address validation service.
// Supports Google Maps Platform (Places Autocomplete + Geocoding) with automatic
// fallback to OpenStreetMap Nominatim.
// All lookups and validations are strictly constrained to San Francisco, CA.

export const SF_BOUNDS = {
  south: 37.708, // The true southern boundary between SF and San Mateo County / Daly City
  north: 37.84,
  west: -122.53,
  east: -122.35,
};

export const SF_VIEWBOX = `${SF_BOUNDS.west},${SF_BOUNDS.north},${SF_BOUNDS.east},${SF_BOUNDS.south}`;

/** Checks if a coordinate pair falls within the San Francisco delivery zone. */
export function isWithinSanFrancisco(lat, lng) {
  if (lat == null || lng == null) return false;
  const numLat = Number(lat);
  const numLng = Number(lng);
  return (
    numLat >= SF_BOUNDS.south &&
    numLat <= SF_BOUNDS.north &&
    numLng >= SF_BOUNDS.west &&
    numLng <= SF_BOUNDS.east
  );
}

/* ------------------------------------------------------------------ */
/* Google Maps SDK Loader                                             */
/* ------------------------------------------------------------------ */

let googleMapsPromise = null;

export function loadGoogleMaps() {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("Window is not available"));
  }
  if (window.google?.maps?.places) {
    return Promise.resolve(window.google.maps);
  }

  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
  if (!apiKey || typeof apiKey !== "string" || !apiKey.trim()) {
    return Promise.reject(new Error("VITE_GOOGLE_MAPS_API_KEY is not configured"));
  }

  if (googleMapsPromise) return googleMapsPromise;

  googleMapsPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector('script[data-gmaps-loader="true"]');
    if (existing) {
      existing.addEventListener("load", () => resolve(window.google.maps));
      existing.addEventListener("error", (e) => reject(e));
      return;
    }

    const script = document.createElement("script");
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(
      apiKey.trim()
    )}&libraries=places&language=en&region=US`;
    script.async = true;
    script.defer = true;
    script.dataset.gmapsLoader = "true";

    script.onload = () => {
      if (window.google?.maps?.places) {
        resolve(window.google.maps);
      } else {
        reject(new Error("Google Maps loaded without Places library"));
      }
    };
    script.onerror = (err) => {
      googleMapsPromise = null;
      reject(new Error("Failed to load Google Maps script"));
    };
    document.head.appendChild(script);
  });

  return googleMapsPromise;
}

function extractGoogleComponents(result) {
  if (!result || !result.geometry?.location) return null;
  const lat = result.geometry.location.lat();
  const lng = result.geometry.location.lng();

  // 1. Physical land boundary check
  if (!isWithinSanFrancisco(lat, lng)) {
    return null;
  }

  // 2. Strict administrative boundary: must explicitly belong to San Francisco locality or county
  // This cleanly rejects neighboring cities like Daly City, South San Francisco, Oakland, etc.
  const isSF = (result.address_components || []).some(
    (comp) =>
      (comp.types.includes("locality") && comp.long_name.toLowerCase() === "san francisco") ||
      (comp.types.includes("administrative_area_level_2") && comp.long_name.toLowerCase() === "san francisco county") ||
      (comp.types.includes("sublocality") && comp.long_name.toLowerCase() === "san francisco")
  );
  if (!isSF) {
    return null;
  }

  // 3. Reject offshore / water bodies in the Pacific Ocean or San Francisco Bay
  const types = result.types || [];
  const isPureWater = types.some((t) => ["natural_feature", "water", "sea", "ocean", "bay"].includes(t));
  const hasLandFeature = types.some((t) =>
    ["street_address", "route", "premise", "subpremise", "point_of_interest", "establishment", "intersection"].includes(t)
  );
  if (isPureWater && !hasLandFeature) {
    return null;
  }

  let streetNumber = "";
  let route = "";
  let city = "San Francisco";
  let zip = "";

  for (const comp of result.address_components || []) {
    if (comp.types.includes("street_number")) streetNumber = comp.long_name;
    if (comp.types.includes("route")) route = comp.short_name || comp.long_name;
    if (comp.types.includes("locality")) city = comp.long_name;
    if (comp.types.includes("postal_code")) zip = comp.long_name;
  }

  const street =
    [streetNumber, route].filter(Boolean).join(" ") ||
    result.formatted_address.split(",")[0] ||
    "";

  return {
    lat,
    lng,
    displayName: result.formatted_address,
    street,
    city,
    zip,
  };
}

/* ------------------------------------------------------------------ */
/* Public API (Google Maps with OSM Fallback)                          */
/* ------------------------------------------------------------------ */

/**
 * Autocomplete address suggestions as user types.
 * Constrained strictly to San Francisco.
 */
export async function autocomplete(query) {
  const q = (query ?? "").trim();
  if (q.length < 3) return [];

  try {
    const maps = await loadGoogleMaps();
    const service = new maps.places.AutocompleteService();
    const sfBounds = new maps.LatLngBounds(
      new maps.LatLng(SF_BOUNDS.south, SF_BOUNDS.west),
      new maps.LatLng(SF_BOUNDS.north, SF_BOUNDS.east)
    );

    const predictions = await new Promise((resolve) => {
      service.getPlacePredictions(
        {
          input: q,
          bounds: sfBounds,
          strictBounds: true,
          componentRestrictions: { country: "us" },
        },
        (results, status) => {
          if (status === maps.places.PlacesServiceStatus.OK && results) {
            resolve(results);
          } else {
            resolve([]);
          }
        }
      );
    });

    return predictions.map((p) => ({
      placeId: p.place_id,
      displayName: p.description,
      street: p.structured_formatting?.main_text || p.description.split(",")[0],
      city: "San Francisco",
      zip: "",
      lat: null,
      lng: null,
      source: "google",
    }));
  } catch (err) {
    // Fall back to Nominatim OSM if Google Maps key is unavailable or fails
    return autocompleteNominatim(q);
  }
}

/**
 * Resolves a Google Place ID into exact coordinates and structured address.
 */
export async function geocodePlaceId(placeId) {
  if (!placeId) return null;
  try {
    const maps = await loadGoogleMaps();
    const geocoder = new maps.Geocoder();
    return await new Promise((resolve) => {
      geocoder.geocode({ placeId }, (results, status) => {
        if (status === "OK" && results?.[0]) {
          resolve(extractGoogleComponents(results[0]));
        } else {
          resolve(null);
        }
      });
    });
  } catch {
    return null;
  }
}

/**
 * Geocodes an address string/object into coordinates.
 * Returns null if the address cannot be found or is outside San Francisco.
 */
export async function geocode({ street, city, zip }) {
  const q = [street, city || "San Francisco", zip].filter((s) => (s ?? "").trim()).join(", ");
  if (!q) return null;

  try {
    const maps = await loadGoogleMaps();
    const geocoder = new maps.Geocoder();
    const sfBounds = new maps.LatLngBounds(
      new maps.LatLng(SF_BOUNDS.south, SF_BOUNDS.west),
      new maps.LatLng(SF_BOUNDS.north, SF_BOUNDS.east)
    );

    return await new Promise((resolve) => {
      geocoder.geocode(
        {
          address: q,
          bounds: sfBounds,
          componentRestrictions: { country: "us" },
        },
        (results, status) => {
          if (status === "OK" && results?.[0]) {
            resolve(extractGoogleComponents(results[0]));
          } else {
            resolve(null);
          }
        }
      );
    });
  } catch {
    return geocodeNominatim({ street, city, zip });
  }
}

/**
 * Reverse-geocodes coordinates into the nearest street address.
 * Rejects any coordinates outside San Francisco.
 */
export async function reverseGeocode(lat, lng) {
  if (!isWithinSanFrancisco(lat, lng)) {
    return null;
  }

  try {
    const maps = await loadGoogleMaps();
    const geocoder = new maps.Geocoder();
    return await new Promise((resolve) => {
      geocoder.geocode({ location: { lat: Number(lat), lng: Number(lng) } }, (results, status) => {
        if (status === "OK" && results?.[0]) {
          resolve(extractGoogleComponents(results[0]));
        } else {
          resolve(null);
        }
      });
    });
  } catch {
    return reverseGeocodeNominatim(lat, lng);
  }
}

/* ------------------------------------------------------------------ */
/* OpenStreetMap (Nominatim) Fallback Engine                          */
/* ------------------------------------------------------------------ */

const OSM_BASE = "https://nominatim.openstreetmap.org";
const LANG = "accept-language=en";
const osmCache = new Map();

async function fetchOsmJson(url) {
  if (osmCache.has(url)) return osmCache.get(url);
  const res = await fetch(url, { headers: { Accept: "application/json" } });
  if (!res.ok) throw new Error(`OSM failed: ${res.status}`);
  const json = await res.json();
  osmCache.set(url, json);
  return json;
}

function toOsmResult(item) {
  if (!item) return null;
  const lat = Number(item.lat);
  const lng = Number(item.lon);
  if (!isWithinSanFrancisco(lat, lng)) return null;

  const street =
    [item.address?.house_number, item.address?.road].filter(Boolean).join(" ") ||
    item.address?.road ||
    item.display_name?.split(",")[0] ||
    "";

  return {
    lat,
    lng,
    displayName: item.display_name,
    street,
    city: item.address?.city || "San Francisco",
    zip: item.address?.postcode || "",
  };
}

async function autocompleteNominatim(q) {
  const url = `${OSM_BASE}/search?format=jsonv2&addressdetails=1&limit=5&countrycodes=us&viewbox=${SF_VIEWBOX}&bounded=1&${LANG}&q=${encodeURIComponent(
    q
  )}`;
  const items = await fetchOsmJson(url);
  return (items ?? []).map(toOsmResult).filter(Boolean);
}

async function geocodeNominatim({ street, city, zip }) {
  const q = [street, city, zip].filter((s) => (s ?? "").trim()).join(", ");
  if (!q) return null;
  const url = `${OSM_BASE}/search?format=jsonv2&addressdetails=1&limit=1&countrycodes=us&viewbox=${SF_VIEWBOX}&bounded=1&${LANG}&q=${encodeURIComponent(
    q
  )}`;
  const items = await fetchOsmJson(url);
  return toOsmResult(items?.[0]);
}

async function reverseGeocodeNominatim(lat, lng) {
  const url = `${OSM_BASE}/reverse?format=jsonv2&addressdetails=1&lat=${lat}&lon=${lng}&${LANG}`;
  const item = await fetchOsmJson(url);
  return toOsmResult(item);
}
