export interface AuthInfo {
  username: string;
  teamName: string;
  teamId: string;
  role: 'member' | 'lead' | 'admin';
  isAuthenticated: boolean;
  /**
   * The current OIDC access token, or null when not yet authenticated.
   * MVP: hardcoded dev token; replaced by real OIDC provider integration later.
   */
  token: string | null;
}

const ROLE_STORAGE_KEY = 'portal.dev.role';

function resolveDevRole(): AuthInfo['role'] {
  const param = new URLSearchParams(window.location.search).get('role');
  if (param === 'admin' || param === 'lead') {
    sessionStorage.setItem(ROLE_STORAGE_KEY, param);
    return param;
  }
  const stored = sessionStorage.getItem(ROLE_STORAGE_KEY);
  if (stored === 'admin' || stored === 'lead') return stored;
  return 'member';
}

/**
 * Provides user/team context extracted from the OIDC token.
 * MVP: returns hardcoded dev values. Replace the token with a real OIDC
 * provider (oidc-client-ts, Keycloak JS, etc.) when login flow is implemented.
 *
 * Dev override: append ?role=admin (or lead) to any URL to switch roles
 * for the session. The choice persists across client-side navigations.
 */
export function useAuth(): AuthInfo {
  return {
    username: 'developer',
    teamName: 'My Team',
    teamId: '1',
    role: resolveDevRole(),
    isAuthenticated: true,
    token: 'dev-token',
  };
}
