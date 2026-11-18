import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, onUnauthorized, tokenStore } from '../api/client';
import type { AuthResponse, RegisterRequest, RegistrationResponse, User } from '../api/types';

interface AuthContextValue {
  user: User | null;
  /** False until the stored token (if any) has been checked. */
  ready: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  /** Creates the account. It can be used once the emailed confirmation link has been clicked. */
  signUp: (request: RegisterRequest) => Promise<RegistrationResponse>;
  /** Starts a session from a response that carries a token (email confirmation, password reset). */
  acceptSession: (response: AuthResponse) => void;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  const signOut = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    onUnauthorized(signOut); // an expired token anywhere in the app signs the user out
    if (!tokenStore.get()) {
      setReady(true);
      return;
    }
    api.me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setReady(true));
  }, [signOut]);

  const acceptSession = useCallback((response: AuthResponse) => {
    tokenStore.set(response.token);
    setUser(response.user);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    ready,
    signOut,
    acceptSession,
    signIn: async (email, password) => acceptSession(await api.login(email, password)),
    signUp: (request) => api.register(request),
  }), [user, ready, signOut, acceptSession]);

  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}

/** "Asha Verma", falling back to the email for accounts without a name. */
export function displayName(user: User): string {
  const name = [user.firstName, user.lastName].filter(Boolean).join(' ');
  return name || user.email;
}
