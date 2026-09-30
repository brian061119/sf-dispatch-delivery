// Geocoding via OpenStreetMap Nominatim — free, no API key, CORS-open.
// Used by the order wizard address step:
//   autocomplete(query)      — input dropdown suggestions (forward geocode)
//   geocode(address)         — validate a structured address → {lat,lng,...} or null
//   reverseGeocode(lat, lng) — map click → nearest street address
//
// Policy notes: Nominatim allows ~1 req/s and asks callers to identify
// themselves; browsers send Origin automatically. We debounce at the caller
// and cache results in-session to stay well under the limit.
// Demo scope: viewbox is clamped to San Francisco so suggestions stay relevant.

const BASE = 'https://nominatim.openstreetmap.org';
// SF bounding box: (left, top, right, bottom) lon/lat.
const SF_VIEWBOX = '-122.52,37.83,-122.35,37.70';

const cache = new Map();

async function fetchJson(url) {
    if (cache.has(url)) return cache.get(url);
    const res = await fetch(url, { headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error(`geocoding failed: ${res.status}`);
    const json = await res.json();
    cache.set(url, json);
    return json;
}

function toResult(item) {
    if (!item) return null;
    return {
        lat: Number(item.lat),
        lng: Number(item.lon),
        displayName: item.display_name,
        // Structured parts for the form (may be missing on sparse results).
        street: [item.address?.house_number, item.address?.road].filter(Boolean).join(' ') || item.address?.road || '',
        city: item.address?.city || item.address?.town || item.address?.village || 'San Francisco',
        zip: item.address?.postcode || '',
    };
}

/** Forward-search suggestions for an autocomplete dropdown.
 * @param {string} query free text, e.g. "ferry build"
 * @returns {Promise<Array<{lat,lng,displayName,street,city,zip}>>} */
export async function autocomplete(query) {
    const q = (query ?? '').trim();
    if (q.length < 3) return [];
    const url = `${BASE}/search?format=jsonv2&addressdetails=1&limit=5&countrycodes=us&viewbox=${SF_VIEWBOX}&bounded=1&q=${encodeURIComponent(q)}`;
    const items = await fetchJson(url);
    return (items ?? []).map(toResult).filter(Boolean);
}

/** Validate + geocode a structured address. Returns null when Nominatim
 * cannot find it — callers must treat null as "not a real address", and must
 * NOT fall back to a default pin. */
export async function geocode({ street, city, zip }) {
    const q = [street, city, zip].filter((s) => (s ?? '').trim()).join(', ');
    if (!q) return null;
    const url = `${BASE}/search?format=jsonv2&addressdetails=1&limit=1&countrycodes=us&q=${encodeURIComponent(q)}`;
    const items = await fetchJson(url);
    return toResult(items?.[0]);
}

/** Reverse-geocode a map click into the nearest street address. */
export async function reverseGeocode(lat, lng) {
    const url = `${BASE}/reverse?format=jsonv2&addressdetails=1&lat=${lat}&lon=${lng}`;
    const item = await fetchJson(url);
    return toResult(item);
}
