import { create } from 'zustand';
import * as authApi from '../api/auth';
import { clearAuth, getRole, getToken, getUsername, setAuth } from '../lib/auth';
// Hydrates from localStorage on load so a refresh keeps you logged in.
// Contract login response: { token, user: { id, username, email, role } }.
// role ∈ USER | VIP | ADMIN (backend Role enum). VIP gets -10% prices;
// ADMIN unlocks the /admin console.
export const useAuth = create((set) => ({
    token: getToken(),
    username: getUsername(),
    role: getRole(),
    async login(username, password) {
        const res = await authApi.login({ username, password });
        const role = res.user?.role || 'USER';
        setAuth(res.token, res.user.username, role);
        set({ token: res.token, username: res.user.username, role });
    },
    async signup(username, password, email) {
        const res = await authApi.register({ username, password, email });
        const role = res.user?.role || 'USER';
        setAuth(res.token, res.user.username, role);
        set({ token: res.token, username: res.user.username, role });
    },
    async logout() {
        try {
            await authApi.logout(); // best-effort (contract TBD: server-side invalidation?)
        }
        catch { /* local logout must succeed even if the endpoint fails */ }
        clearAuth();
        set({ token: null, username: null, role: 'USER' });
    },
}));
