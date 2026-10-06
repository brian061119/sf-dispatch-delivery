// Token + username + role persistence. One place so the whole app reads auth the same way.
const TOKEN_KEY = 'token';
const USER_KEY = 'wd_username';
const ROLE_KEY = 'wd_role';
export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const getUsername = () => localStorage.getItem(USER_KEY);
export const getRole = () => localStorage.getItem(ROLE_KEY) || 'USER';
export const isAuthed = () => !!getToken();
export function setAuth(token, username, role = 'USER') {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, username);
    localStorage.setItem(ROLE_KEY, role || 'USER');
}
export function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(ROLE_KEY);
}
