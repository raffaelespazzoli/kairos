import { useState, useEffect, useCallback } from 'react';
import { apiFetch, ApiError } from '../api/client';
import type { PortalError } from '../types/error';

interface UseApiFetchResult<T> {
  data: T | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: () => void;
}

export function useApiFetch<T>(path: string): UseApiFetchResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<PortalError | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchData = useCallback(async () => {
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
    fetchData();
  }, [fetchData]);

  return { data, error, isLoading, refresh: fetchData };
}
