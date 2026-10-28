import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api, getToken, setToken, UNAUTHORIZED_EVENT } from "~/lib/api";
import type { AuthResponse, User } from "~/lib/types";

interface AuthState {
  user: User | null;
  /** False until the stored session has been checked on the client. */
  ready: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (name: string, email: string, password: string) => Promise<User>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    if (!getToken()) {
      setReady(true);
      return;
    }
    api<User>("/auth/me")
      .then(setUser)
      .catch(logout)
      .finally(() => setReady(true));
  }, [logout]);

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, logout);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, logout);
  }, [logout]);

  const value = useMemo<AuthState>(() => {
    const accept = (res: AuthResponse) => {
      setToken(res.token);
      setUser(res.user);
      return res.user;
    };
    return {
      user,
      ready,
      logout,
      login: (email, password) => api<AuthResponse>("/auth/login", { method: "POST", body: { email, password } }).then(accept),
      register: (name, email, password) =>
        api<AuthResponse>("/auth/register", { method: "POST", body: { name, email, password } }).then(accept),
    };
  }, [user, ready, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
