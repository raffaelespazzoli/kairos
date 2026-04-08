import type { PortalError } from '../types/error';

export class ApiError extends Error {
  constructor(
    public status: number,
    public portalError: PortalError,
  ) {
    super(portalError.message);
    this.name = 'ApiError';
  }
}

let tokenAccessor: (() => string | null) | null = null;

/**
 * Registers a function that returns the current OIDC bearer token.
 * Called once during app initialization by the auth provider.
 */
export function setTokenAccessor(accessor: () => string | null): void {
  tokenAccessor = accessor;
}

function getOidcToken(): string | null {
  if (tokenAccessor) {
    return tokenAccessor();
  }
  return null;
}

/**
 * Typed fetch wrapper that injects the OIDC bearer token, sets JSON content type,
 * uses relative URLs, and parses error responses into typed PortalError objects.
 */
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getOidcToken();

  const headers = new Headers(options.headers);
  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const devRole = sessionStorage.getItem('portal.dev.role');
  if (devRole) {
    headers.set('X-Dev-Role', devRole);
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  if (!response.ok) {
    const errorBody: PortalError = await response.json().catch(() => ({
      error: 'unknown',
      message: `Request failed with status ${response.status}`,
      timestamp: new Date().toISOString(),
    }));
    throw new ApiError(response.status, errorBody);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  try {
    return await response.json() as T;
  } catch {
    throw new ApiError(response.status, {
      error: 'parse-error',
      message: `Expected JSON response but got unparseable body (status ${response.status})`,
      timestamp: new Date().toISOString(),
    });
  }
}

/**
 * Fetch wrapper for endpoints that return plain text (e.g. build logs).
 * Uses the same auth and error conventions as apiFetch.
 */
export async function apiFetchText(
  path: string,
  options: RequestInit = {},
): Promise<string> {
  const token = getOidcToken();

  const headers = new Headers(options.headers);
  headers.set('Accept', 'text/plain');
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const devRole = sessionStorage.getItem('portal.dev.role');
  if (devRole) {
    headers.set('X-Dev-Role', devRole);
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  if (!response.ok) {
    const errorBody: PortalError = await response.json().catch(() => ({
      error: 'unknown',
      message: `Request failed with status ${response.status}`,
      timestamp: new Date().toISOString(),
    }));
    throw new ApiError(response.status, errorBody);
  }

  return response.text();
}
