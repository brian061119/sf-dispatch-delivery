import { create } from 'zustand';
import * as authApi from '../api/auth';
import { clearAuth, getToken, getUsername, setAuth } from '../lib/auth';
// Hydrates from localStorage on load so a refresh keeps you logged in.
// Contract login response: { token, user: { id, username, email } }.
export const useAuth = create((set) => ({
    token: getToken(),
    username: getUsername(),
    async login(username, password) {
        const res = await authApi.login({ username, password });
        setAuth(res.token, res.user.username);
        set({ token: res.token, username: res.user.username });
    },
    async signup(username, password, email) {
        // Register body/response are contract TBD; we assume the same { token, user } shape.
        const res = await authApi.register({ username, password, email });
        setAuth(res.token, res.user.username);
        set({ token: res.token, username: res.user.username });
    },
    async logout() {
        try {
            await authApi.logout(); // best-effort (contract TBD: server-side invalidation?)
        }
        catch { /* local logout must succeed even if the endpoint fails */ }
        clearAuth();
        set({ token: null, username: null });
    },
}));
