import { http } from '../lib/http';
// Owner: Yuning Zhang (追踪). Contract: GET /api/orders/:orderId/tracking.
// The Tracking page polls this every 5s. (Contract also lists an optional
// WS /api/ws/orders/:orderId as a stretch feature — P0/P1 uses polling only.)
export async function getTracking(orderId) {
    const { data } = await http.get(`/orders/${orderId}/tracking`);
    return data;
}
