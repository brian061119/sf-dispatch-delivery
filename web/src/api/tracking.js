import { http } from '../lib/http';
import * as mock from './mock';
// Owner: Yuning Zhang (tracking). Contract: GET /api/orders/:orderId/tracking.
// The Tracking page polls this every 5s. (Contract also lists an optional
// WS /api/ws/orders/:orderId as a stretch feature — P0/P1 uses polling only.)
// NOTE: also called ANONYMOUSLY from the guest lookup page (/track) — the
// backend must allow this endpoint without a JWT (open item in HANDOFF.md).
export async function getTracking(orderId) {
    if (import.meta.env.VITE_MOCK === '1') return mock.getTracking(orderId);
    const { data } = await http.get(`/orders/${orderId}/tracking`);
    return data;
}
