import { http } from '../lib/http';
import * as mock from './mock';
// ---- Zihang Cao — order create + list ------------------------------------------
// Contract: POST /api/orders (embedded payment: pay + create in ONE call, 201).
// PRICING IS BACKEND-OWNED (confirmed 2026-09-26): send only candidateId + the
// same pickup / dropoff / package used for POST /api/recommendations — NEVER a
// price. The backend re-derives the candidate and charges its estimatedCost.
// 409 "Selected plan <candidateId> is no longer available" → re-fetch
// recommendations and let the user pick again; 409 also means no idle vehicle
// could be locked at checkout. Handle these distinctly in the wizard.
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
// PATCH /api/orders/:orderId/cancel is supported by the backend:
// Cancels order, releases vehicle to IDLE, and processes refund.
export async function cancelOrder(orderId) {
    if (import.meta.env.VITE_MOCK === '1') return mock.cancelOrder(orderId);
    const { data } = await http.patch(`/orders/${orderId}/cancel`);
    return data;
}

// PATCH /api/orders/:orderId (Update delivery mode, address, specs before transit; 1 modification limit)
export async function updateOrder(orderId, body) {
    const { data } = await http.patch(`/orders/${orderId}`, body);
    return data;
}

// GET /api/orders/:orderId/review (Retrieve submitted review)
export async function getOrderReview(orderId) {
    if (import.meta.env.VITE_MOCK === '1') return null;
    const { data } = await http.get(`/orders/${orderId}/review`);
    return data;
}

