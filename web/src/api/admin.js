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
