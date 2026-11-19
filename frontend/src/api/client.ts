import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { LoginResponse, UserInfo } from '../types';
import { tokenStore } from '../services/tokenStore';

export const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30_000,
});

api.interceptors.request.use((config) => {
  const token = tokenStore.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** User info as cached by the client, including the forced-password-change flag. */
export function sessionUser(res: LoginResponse): UserInfo {
  return { ...res.user, passwordChangeRequired: res.passwordChangeRequired };
}

let refreshInFlight: Promise<boolean> | null = null;
let onSessionExpired: () => void = () => {};

/** Registers the callback invoked when the session cannot be refreshed. */
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler;
}

/** Rotates tokens once, sharing the result between concurrent callers. */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    const refreshToken = tokenStore.getRefreshToken();
    refreshInFlight = (async () => {
      if (!refreshToken) return false;
      try {
        const { data } = await axios.post<LoginResponse>('/api/v1/auth/refresh', { refreshToken });
        tokenStore.setSession(data.accessToken ?? '', data.refreshToken ?? '', sessionUser(data));
        return true;
      } catch {
        tokenStore.clear();
        return false;
      }
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const original = error.config as RetriableConfig | undefined;
  const isAuthCall = original?.url?.startsWith('/auth/');
  if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
    original._retried = true;
    if (await refreshSession()) {
      return api(original);
    }
    onSessionExpired();
  }
  // Temporary staff passwords must be replaced before anything else is allowed.
  const message = (error.response?.data as { message?: string } | undefined)?.message ?? '';
  if (error.response?.status === 403 && message.includes('change your password')
      && window.location.pathname !== '/account/password') {
    window.location.assign('/account/password');
  }
  return Promise.reject(error);
});
