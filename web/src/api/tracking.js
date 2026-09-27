import { http } from '../lib/http';
import * as mock from './mock';
// Owner: Yuning Zhang (tracking). Contract: GET /api/orders/:orderId/tracking.
// The Tracking page polls this every 5s. (Contract also lists an optional
// WS /api/ws/orders/:orderId as a stretch feature — P0/P1 uses polling only.)
//
// Auth model (backend, 2026-09-26):
//  - GET /api/orders/:orderNumber/tracking → requires JWT (owner or admin only).
//  - GET /api/tracking/:trackingCode       → PUBLIC, keyed by the random
//    trackingCode returned by POST /api/orders and GET /api/orders. The guest
//    lookup page (/track) MUST use getTrackingByCode, not this function.

// Backend internal states (OrderStatus.java) that are NOT in the contract
// 4-state model. The list endpoint maps them server-side, but the tracking
// endpoint returns the raw enum name in `status` — normalize here so pages and
// shared components only ever see the contract values.
const CONTRACT_STATUSES = ['PENDING', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'];

/** Map an internal 6-state (or anything unknown) onto the contract 4-state.
 * PENDING_PAYMENT / PAID / PICKING_UP all mean "not yet on the way" → PENDING.
 * @param {string} raw @returns {import('../types/api.js').OrderStatus} */
export function normalizeStatus(raw) {
    if (CONTRACT_STATUSES.includes(raw)) return raw;
    if (raw === 'DELIVERED' || raw === 'CANCELLED' || raw === 'IN_TRANSIT') return raw;
    return 'PENDING'; // PENDING_PAYMENT | PAID | PICKING_UP | unknown → not yet in transit
}

/** Decorate a raw tracking payload with the contract fields the UI relies on:
 * `status` (normalized 4-state) + `detailStatus` (raw, for the timeline). */
function withContractFields(data) {
    if (!data || typeof data !== 'object') return data;
    return { ...data, detailStatus: data.status, status: normalizeStatus(data.status) };
}

/** Logged-in tracking by order number (owner or admin). 401 handled globally. */
export async function getTracking(orderId) {
    if (import.meta.env.VITE_MOCK === '1') return mock.getTracking(orderId);
    const { data } = await http.get(`/orders/${orderId}/tracking`);
    return withContractFields(data);
}

/** PUBLIC tracking by tracking code — no JWT needed. Use from GuestTrack. */
export async function getTrackingByCode(trackingCode) {
    if (import.meta.env.VITE_MOCK === '1') return mock.getTrackingByCode(trackingCode);
    const { data } = await http.get(`/tracking/${encodeURIComponent(trackingCode)}`);
    return withContractFields(data);
}
