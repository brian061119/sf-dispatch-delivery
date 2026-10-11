import { http } from '../lib/http';
import * as mock from './mock';
// Admin console. Contract: GET /api/admin/dashboard → AdminDashboardDto
// (stations + fleet counters + recent orders). Backend enforces ADMIN role —
// a regular user gets 403 and the page explains that.
export async function getAdminDashboard() {
    if (import.meta.env.VITE_MOCK === '1') return mock.getAdminDashboard();
    const { data } = await http.get('/admin/dashboard');
    return data;
}

// GET /api/admin/users → [{id, username, firstName, lastName, email, role,
// isVip, vipExpireAt, createdAt, orderCount, activeOrderCount}] (ADMIN only).
export async function getAdminUsers() {
    if (import.meta.env.VITE_MOCK === '1') return mock.getAdminUsers();
    const { data } = await http.get('/admin/users');
    return data;
}
// GET /api/admin/users/:userId → { user: <same as list item>, orders: [{orderNumber,
// trackingCode, status (4-state), detailStatus, vehicleType, pickupAddress,
// dropoffAddress, finalPrice, createdAt, actualDeliveryTime}] } (ADMIN only, read-only).
export async function getAdminUser(userId) {
    if (import.meta.env.VITE_MOCK === '1') return mock.getAdminUser(userId);
    const { data } = await http.get(`/admin/users/${encodeURIComponent(userId)}`);
    return data;
}
