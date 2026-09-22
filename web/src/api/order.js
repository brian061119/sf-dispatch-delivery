import { http } from '../lib/http';
import * as mock from './mock';
// ---- Zihang Cao — order create + list ------------------------------------------
// Contract: POST /api/orders (embedded payment: pay + create in ONE call, 201).
export async function createOrder(body) {
    if (import.meta.env.VITE_MOCK === '1') return mock.createOrder(body);
    const { data } = await http.post('/orders', body);
    return data;
}
// Contract: GET /api/orders -> { orders: OrderSummary[] }.
export async function getOrders() {
    if (import.meta.env.VITE_MOCK === '1') return mock.getOrders();
    const { data } = await http.get('/orders');
    return data;
}
// ---- Y — detail + confirm receipt + review --------------------------------------
// Contract: GET /api/orders/:orderId (response body TBD — page renders defensively).
export async function getOrder(orderId) {
    if (import.meta.env.VITE_MOCK === '1') return mock.getOrder(orderId);
    const { data } = await http.get(`/orders/${orderId}`);
    return data;
}
// Contract: PATCH /api/orders/:orderId/confirm-receipt -> { orderId, status: 'DELIVERED' }.
export async function confirmReceipt(orderId) {
    if (import.meta.env.VITE_MOCK === '1') return mock.confirmReceipt(orderId);
    const { data } = await http.patch(`/orders/${orderId}/confirm-receipt`);
    return data;
}
// Contract: POST /api/orders/:orderId/review { rating, comment, damageReported }.
export async function submitReview(orderId, body) {
    if (import.meta.env.VITE_MOCK === '1') return mock.submitReview(orderId, body);
    const { data } = await http.post(`/orders/${orderId}/review`, body);
    return data;
}
// NOTE: the contract has NO cancel endpoint. The Tracking page's cancel button is
// commented out with a TODO until the backend adds one (tracked in HANDOFF.md).
