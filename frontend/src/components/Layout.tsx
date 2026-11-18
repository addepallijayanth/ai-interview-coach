import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { api } from '../api/client';
import type { UsageResponse, User } from '../api/types';
import { displayName, useAuth } from '../auth/AuthContext';
import { formatUsd } from '../lib/format';

function initials(user: User): string {
  const letters = [user.firstName, user.lastName].filter(Boolean).map((name) => name!.charAt(0));
  return (letters.length ? letters.join('') : user.email.charAt(0)).toUpperCase();
}

export function Layout() {
  const { user, signOut } = useAuth();
  const location = useLocation();
  const [usage, setUsage] = useState<UsageResponse>();

  useEffect(() => {
    if (!user) return;
    api.usage().then(setUsage).catch(() => setUsage(undefined));
  }, [user, location.pathname]);

  return (
    <div className="app">
      <header className="topbar">
        <div className="container topbar-inner">
          <Link to="/" className="brand">
            <img src="/favicon.svg" alt="" />
            Interview Coach
          </Link>
          {user && (
            <nav className="nav" aria-label="Main">
              <NavLink to="/" end>New analysis</NavLink>
              <NavLink to="/history">History</NavLink>
            </nav>
          )}
          {user && (
            <div className="topbar-right">
              <span className="email" title={user.email}>
                <span className="avatar-initials" aria-hidden>{initials(user)}</span>
                {displayName(user)}
              </span>
              <button type="button" className="btn btn-ghost btn-sm" onClick={signOut}>Sign out</button>
            </div>
          )}
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
      <footer className="footer">
        <div className="container">
          <span>
            {usage
              ? `${usage.aiRequestsRemainingToday} of ${usage.aiRequestsPerDay} AI requests left today · ${formatUsd(usage.costUsd)} of AI usage so far`
              : 'Mock interviews powered by Claude'}
          </span>
          <span>Feedback is AI-generated: use it as practice guidance, not a verdict.</span>
        </div>
      </footer>
    </div>
  );
}
