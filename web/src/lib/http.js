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
