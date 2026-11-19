import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { refreshSession, sessionUser, setSessionExpiredHandler } from '../api/client';
import { authApi } from '../api/endpoints';
import { tokenStore } from '../services/tokenStore';
import type { LoginResponse, UserInfo } from '../types';

/** Result of the password step: either signed in, or a second factor is needed. */
export type LoginOutcome = { user: UserInfo; mfaToken?: undefined } | { mfaToken: string; user?: undefined };

interface AuthContextValue {
  user: UserInfo | null;
  /** True while the session is being restored on page load. */
  initializing: boolean;
  login: (email: string, password: string) => Promise<LoginOutcome>;
  loginMfa: (mfaToken: string, code: string) => Promise<UserInfo>;
  register: (data: { email: string; password: string; firstName: string; lastName: string }) => Promise<UserInfo>;
  /** Replaces the session with a new one (e.g. after a password change). */
  startSession: (res: LoginResponse) => UserInfo;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    setSessionExpiredHandler(() => {
      tokenStore.clear();
      setUser(null);
    });
    // Restore the session: the access token is memory-only, so obtain a new one.
    if (tokenStore.getRefreshToken()) {
      refreshSession()
        .then((ok) => setUser(ok ? tokenStore.getUser() : null))
        .finally(() => setInitializing(false));
    } else {
      setInitializing(false);
    }
  }, []);

  const startSession = useCallback((res: LoginResponse) => {
    const u = sessionUser(res);
    tokenStore.setSession(res.accessToken ?? '', res.refreshToken ?? '', u);
    setUser(u);
    return u;
  }, []);

  const login = useCallback(
    async (email: string, password: string): Promise<LoginOutcome> => {
      const res = await authApi.login({ email, password });
      if (res.mfaRequired && res.mfaToken) return { mfaToken: res.mfaToken };
      return { user: startSession(res) };
    },
    [startSession],
  );

  const loginMfa = useCallback(
    async (mfaToken: string, code: string) => startSession(await authApi.loginMfa({ mfaToken, code })),
    [startSession],
  );

  const register = useCallback(
    async (data: { email: string; password: string; firstName: string; lastName: string }) =>
      startSession(await authApi.register(data)),
    [startSession],
  );

  const logout = useCallback(async () => {
    const refresh = tokenStore.getRefreshToken();
    tokenStore.clear();
    setUser(null);
    if (refresh) {
      await authApi.logout(refresh).catch(() => undefined);
    }
  }, []);

  const value = useMemo(
    () => ({ user, initializing, login, loginMfa, register, startSession, logout }),
    [user, initializing, login, loginMfa, register, startSession, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

export function homePathFor(user: UserInfo | null): string {
  if (!user) return '/login';
  if (user.passwordChangeRequired) return '/account/password';
  if (user.role === 'TOURIST') return '/tourist/dashboard';
  if (user.role === 'OFFICER') return '/officer/dashboard';
  return '/admin/dashboard';
}

/** Account security page for the user's role (each role has its own layout). */
export function securityPathFor(user: UserInfo | null): string {
  if (user?.role === 'TOURIST') return '/tourist/security';
  if (user?.role === 'OFFICER') return '/officer/security';
  return '/admin/security';
}
