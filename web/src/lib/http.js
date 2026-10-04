import axios from 'axios';
import { clearAuth, getToken } from './auth';
// Single axios instance for the whole app. Backend alignment lives here:
//  - baseURL comes from env (dev uses the vite proxy, so the default is '/api')
//  - every request carries the JWT
//  - a 401 anywhere clears the session and bounces back to /login
export const http = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    timeout: 15000,
});
http.interceptors.request.use((config) => {
    const token = getToken();
    if (token)
        config.headers.Authorization = `Bearer ${token}`;
    return config;
});
http.interceptors.response.use((res) => res, (err) => {
    if (err?.response?.status === 401) {
        clearAuth();
        if (window.location.pathname !== '/login')
            window.location.assign('/login');
    }
    return Promise.reject(err);
});

/**
 * Extracts a human-readable message from a failed API call.
 *
 * The backend is not consistent about the field name, so we check both:
 *   - GlobalExceptionHandler (400/403/404 ...) -> { message: "..." }
 *   - DispatchController's own handler (404)   -> { error:   "..." }
 *   - hand-written error bodies                 -> { code: "PAYMENT_DECLINED", ... }
 * Reading only `data.message` silently produced a generic fallback message for
 * every SF-boundary / missing-coordinate rejection, hiding text the backend had
 * already written for the user.
 */
export function apiErrorMessage(err, fallback) {
    const data = err?.response?.data;
    if (typeof data === 'string' && data.trim()) return data;
    return data?.message || data?.error || fallback;
}
