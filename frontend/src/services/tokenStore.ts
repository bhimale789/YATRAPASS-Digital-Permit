import type { UserInfo } from '../types';

/**
 * Token handling:
 *  - The short-lived access token lives only in memory.
 *  - The single-use refresh token is kept in localStorage so a page reload can resume
 *    the session. It is rotated on every refresh and revoked server-side on logout.
 *  - The cached user is display-only; authorization is always enforced by the backend.
 */
const REFRESH_KEY = 'tp.refreshToken';
const USER_KEY = 'tp.user';

let accessToken: string | null = null;

function safeGet(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSet(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    /* storage unavailable (private mode) — session will not survive reload */
  }
}

export const tokenStore = {
  getAccessToken: () => accessToken,
  getRefreshToken: () => safeGet(REFRESH_KEY),
  getUser(): UserInfo | null {
    const raw = safeGet(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as UserInfo;
    } catch {
      return null;
    }
  },
  setSession(access: string, refresh: string, user: UserInfo) {
    accessToken = access;
    safeSet(REFRESH_KEY, refresh);
    safeSet(USER_KEY, JSON.stringify(user));
  },
  clear() {
    accessToken = null;
    safeSet(REFRESH_KEY, null);
    safeSet(USER_KEY, null);
  },
};
