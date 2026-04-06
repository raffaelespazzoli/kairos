import { useApiFetch } from './useApiFetch';
import type { EnvironmentChainResponse } from '../types/environment';

export function useEnvironments(teamId: string | undefined, appId: string | undefined) {
  const path =
    teamId && appId
      ? `/api/v1/teams/${teamId}/applications/${appId}/environments`
      : null;
  return useApiFetch<EnvironmentChainResponse>(path);
}
