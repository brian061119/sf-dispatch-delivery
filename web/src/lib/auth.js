// Token + username persistence. One place so the whole app reads auth the same way.
const TOKEN_KEY = 'token';
const USER_KEY = 'wd_username';
export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const getUsername = () => localStorage.getItem(USER_KEY);
export const isAuthed = () => !!getToken();
export function setAuth(token, username) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, username);
}
export function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
}
