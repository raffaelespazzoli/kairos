import { useApiFetch } from './useApiFetch';
import type { HealthResponse } from '../types/health';

export function useHealth(teamId: string | undefined, appId: string | undefined) {
  const path =
    teamId && appId
      ? `/api/v1/teams/${teamId}/applications/${appId}/health`
      : null;
  return useApiFetch<HealthResponse>(path);
}
