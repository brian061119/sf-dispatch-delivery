import { http } from '../lib/http';
import * as mock from './mock';
// Owner: Ziyuan Xu (认证). Paths follow api-contract.md: /api/auth/*.
export async function register(body) {
    if (import.meta.env.VITE_MOCK === '1') return mock.register(body);
    const { data } = await http.post('/auth/register', body);
    return data;
}
export async function login(body) {
    if (import.meta.env.VITE_MOCK === '1') return mock.login(body);
    const { data } = await http.post('/auth/login', body);
    return data;
}
// Contract TBD: whether logout invalidates a server-side token. We call it
// best-effort and always clear the local session regardless (store/auth.js).
export async function logout() {
    if (import.meta.env.VITE_MOCK === '1') return mock.logout();
    const { data } = await http.post('/auth/logout');
    return data;
}
// Reserved (contract GET /api/auth/me) — no page needs it yet.
export async function getMe() {
    if (import.meta.env.VITE_MOCK === '1') return mock.getMe();
    const { data } = await http.get('/auth/me');
    return data;
}
