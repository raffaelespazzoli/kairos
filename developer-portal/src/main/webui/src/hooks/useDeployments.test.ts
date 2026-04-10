import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { useDeployments } from './useDeployments';

type MockApiFetchResult = {
  data: unknown;
  error: unknown;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

let mockResult: MockApiFetchResult = {
  data: null,
  error: null,
  isLoading: false,
  refresh: vi.fn(),
};

let capturedPath: string | null = null;

vi.mock('./useApiFetch', () => ({
  useApiFetch: (path: string | null) => {
    capturedPath = path;
    return mockResult;
  },
}));

describe('useDeployments', () => {
  beforeEach(() => {
    capturedPath = null;
    mockResult = {
      data: null,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
  });

  it('returns deployment list when all params are provided', () => {
    mockResult = {
      data: [{ deploymentId: 'sha1', releaseVersion: 'v1.0.0' }],
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };

    const { result } = renderHook(() => useDeployments('1', '42', 10));

    expect(result.current.data).toHaveLength(1);
    expect(capturedPath).toBe('/api/v1/teams/1/applications/42/deployments?environmentId=10');
  });

  it('skips fetch when environmentId is undefined', () => {
    renderHook(() => useDeployments('1', '42', undefined));

    expect(capturedPath).toBeNull();
  });

  it('skips fetch when environmentId is null', () => {
    renderHook(() => useDeployments('1', '42', null));

    expect(capturedPath).toBeNull();
  });

  it('skips fetch when teamId is undefined', () => {
    renderHook(() => useDeployments(undefined, '42', 10));

    expect(capturedPath).toBeNull();
  });

  it('skips fetch when appId is undefined', () => {
    renderHook(() => useDeployments('1', undefined, 10));

    expect(capturedPath).toBeNull();
  });

  it('returns error when fetch fails', () => {
    mockResult = {
      data: null,
      error: { error: 'unknown', message: 'Failed', timestamp: '' },
      isLoading: false,
      refresh: vi.fn(),
    };

    const { result } = renderHook(() => useDeployments('1', '42', 10));

    expect(result.current.error).toBeTruthy();
    expect(result.current.data).toBeNull();
  });
});
