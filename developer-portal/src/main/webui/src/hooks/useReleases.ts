import { useApiFetch } from './useApiFetch';
import type { ReleaseSummary } from '../types/release';

export function useReleases(teamId: string | undefined, appId: string | undefined) {
  const path =
    teamId && appId
      ? `/api/v1/teams/${teamId}/applications/${appId}/releases`
      : null;
  return useApiFetch<ReleaseSummary[]>(path);
}
