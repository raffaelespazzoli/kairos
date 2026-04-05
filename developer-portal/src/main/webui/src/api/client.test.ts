import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { apiFetch, ApiError, setTokenAccessor } from './client';

function extractHeaders(call: unknown[]): Headers {
  return (call[1] as RequestInit).headers as Headers;
}

describe('apiFetch', () => {
  const mockFetch = vi.fn();

  beforeEach(() => {
    mockFetch.mockClear();
    vi.stubGlobal('fetch', mockFetch);
    setTokenAccessor(() => 'test-bearer-token');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setTokenAccessor(() => null);
  });

  it('injects Authorization header with bearer token', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: 'test' }),
    });

    await apiFetch('/api/v1/teams');

    const headers = extractHeaders(mockFetch.mock.calls[0]);
    expect(headers.get('Authorization')).toBe('Bearer test-bearer-token');
  });

  it('sets Content-Type to application/json by default', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve([]),
    });

    await apiFetch('/api/v1/teams');

    const headers = extractHeaders(mockFetch.mock.calls[0]);
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('does not override caller-provided Content-Type', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    await apiFetch('/api/v1/upload', {
      headers: { 'Content-Type': 'multipart/form-data' },
    });

    const headers = extractHeaders(mockFetch.mock.calls[0]);
    expect(headers.get('Content-Type')).toBe('multipart/form-data');
  });

  it('uses relative URLs without modification', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve([]),
    });

    await apiFetch('/api/v1/teams');

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/teams', expect.anything());
  });

  it('returns typed response data on success', async () => {
    const teams = [{ id: 1, name: 'payments', oidcGroupId: 'payments' }];
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(teams),
    });

    const result = await apiFetch<typeof teams>('/api/v1/teams');

    expect(result).toEqual(teams);
  });

  it('returns undefined for 204 No Content responses', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 204,
      json: () => Promise.reject(new Error('no body')),
    });

    const result = await apiFetch('/api/v1/teams/1');

    expect(result).toBeUndefined();
  });

  it('throws ApiError with typed PortalError on failure', async () => {
    const portalError = {
      error: 'forbidden',
      message: 'You do not have permission',
      detail: 'Role member cannot access admin',
      system: 'portal',
      timestamp: '2026-04-04T10:00:00Z',
    };
    mockFetch.mockResolvedValue({
      ok: false,
      status: 403,
      json: () => Promise.resolve(portalError),
    });

    await expect(apiFetch('/api/v1/admin/clusters')).rejects.toThrow(ApiError);

    try {
      await apiFetch('/api/v1/admin/clusters');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      const apiError = e as ApiError;
      expect(apiError.status).toBe(403);
      expect(apiError.portalError).toEqual(portalError);
      expect(apiError.message).toBe('You do not have permission');
    }
  });

  it('creates fallback PortalError when error response body is not JSON', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      json: () => Promise.reject(new Error('invalid json')),
    });

    try {
      await apiFetch('/api/v1/teams');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      const apiError = e as ApiError;
      expect(apiError.status).toBe(500);
      expect(apiError.portalError.error).toBe('unknown');
      expect(apiError.portalError.message).toContain('500');
    }
  });

  it('throws ApiError when success response body is not JSON', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.reject(new SyntaxError('Unexpected token')),
    });

    try {
      await apiFetch('/api/v1/teams');
      expect.fail('should have thrown');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      const apiError = e as ApiError;
      expect(apiError.status).toBe(200);
      expect(apiError.portalError.error).toBe('parse-error');
      expect(apiError.portalError.message).toContain('unparseable body');
    }
  });

  it('does not inject Authorization header when no token is available', async () => {
    setTokenAccessor(() => null);
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve([]),
    });

    await apiFetch('/api/v1/teams');

    const headers = extractHeaders(mockFetch.mock.calls[0]);
    expect(headers.has('Authorization')).toBe(false);
  });

  it('passes through custom options', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 201,
      json: () => Promise.resolve({ id: 1 }),
    });

    await apiFetch('/api/v1/teams', {
      method: 'POST',
      body: JSON.stringify({ name: 'new-team' }),
    });

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/teams', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ name: 'new-team' }),
    }));
  });

  it('accepts Headers instance as options.headers', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });

    await apiFetch('/api/v1/teams', {
      headers: new Headers({ 'X-Custom': 'value' }),
    });

    const headers = extractHeaders(mockFetch.mock.calls[0]);
    expect(headers.get('X-Custom')).toBe('value');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('Authorization')).toBe('Bearer test-bearer-token');
  });
});
