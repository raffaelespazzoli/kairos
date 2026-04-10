import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { triggerDeployment, fetchDeploymentHistory } from './deployments';
import { setTokenAccessor } from './client';

describe('deployments API', () => {
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

  describe('triggerDeployment', () => {
    it('sends POST with correct body and path', async () => {
      const responseData = {
        deploymentId: 'dep-1',
        releaseVersion: 'v2.1.1',
        environmentName: 'dev',
        status: 'Deploying',
        startedAt: '2026-04-10T14:30:00Z',
      };

      mockFetch.mockResolvedValue({
        ok: true,
        status: 201,
        json: () => Promise.resolve(responseData),
      });

      const result = await triggerDeployment('team-1', '42', {
        releaseVersion: 'v2.1.1',
        environmentId: 10,
      });

      expect(mockFetch).toHaveBeenCalledOnce();
      const [url, options] = mockFetch.mock.calls[0];
      expect(url).toBe('/api/v1/teams/team-1/applications/42/deployments');
      expect(options.method).toBe('POST');
      expect(JSON.parse(options.body)).toEqual({
        releaseVersion: 'v2.1.1',
        environmentId: 10,
      });
      expect(result).toEqual(responseData);
    });

    it('throws ApiError on failure', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        json: () =>
          Promise.resolve({
            error: 'not-found',
            message: 'Environment not found',
            timestamp: '2026-04-10T14:30:00Z',
          }),
      });

      await expect(
        triggerDeployment('team-1', '42', {
          releaseVersion: 'v2.1.1',
          environmentId: 999,
        }),
      ).rejects.toThrow('Environment not found');
    });
  });

  describe('fetchDeploymentHistory', () => {
    it('sends GET with correct query parameter', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await fetchDeploymentHistory('team-1', '42', 10);

      expect(mockFetch).toHaveBeenCalledOnce();
      const [url] = mockFetch.mock.calls[0];
      expect(url).toBe(
        '/api/v1/teams/team-1/applications/42/deployments?environmentId=10',
      );
    });
  });
});
