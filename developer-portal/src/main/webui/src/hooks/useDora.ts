import { useApiFetch } from './useApiFetch';
import type { DoraMetricsResponse } from '../types/dora';

export function useDora(teamId: string | undefined, appId: string | undefined) {
  const path =
    teamId && appId
      ? `/api/v1/teams/${teamId}/applications/${appId}/dora`
      : null;
  return useApiFetch<DoraMetricsResponse>(path);
}
