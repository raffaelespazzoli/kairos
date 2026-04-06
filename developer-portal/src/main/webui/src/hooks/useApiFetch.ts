import { useState, useEffect, useCallback } from 'react';
import { apiFetch, ApiError } from '../api/client';
import type { PortalError } from '../types/error';

interface UseApiFetchResult<T> {
  data: T | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: () => void;
}

/**
 * Generic data-fetching hook. Pass null to skip the fetch (useful for
 * conditional fetching when a prerequisite value is not yet available).
 */
export function useApiFetch<T>(path: string | null): UseApiFetchResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<PortalError | null>(null);
  const [isLoading, setIsLoading] = useState(path !== null);

  const fetchData = useCallback(async () => {
    if (path === null) return;
    setIsLoading(true);
    setError(null);
    try {
      const result = await apiFetch<T>(path);
      setData(result);
    } catch (e) {
      setData(null);
      if (e instanceof ApiError) {
        setError(e.portalError);
      } else {
        setError({
          error: 'unknown',
          message: 'An unexpected error occurred',
          timestamp: new Date().toISOString(),
        });
      }
    } finally {
      setIsLoading(false);
    }
  }, [path]);

  useEffect(() => {
    if (path !== null) {
      fetchData();
    } else {
      setData(null);
      setError(null);
      setIsLoading(false);
    }
  }, [fetchData, path]);

  return { data, error, isLoading, refresh: fetchData };
}
