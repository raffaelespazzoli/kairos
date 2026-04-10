import { useApiFetch } from './useApiFetch';
import type { DeploymentHistoryEntry } from '../types/deployment';

export function useDeployments(
  teamId: string | undefined,
  appId: string | undefined,
  environmentId: number | null | undefined,
) {
  const path =
    teamId && appId && environmentId != null
      ? `/api/v1/teams/${teamId}/applications/${appId}/deployments?environmentId=${environmentId}`
      : null;
  return useApiFetch<DeploymentHistoryEntry[]>(path);
}
