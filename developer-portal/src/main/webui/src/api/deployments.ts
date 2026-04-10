import { apiFetch } from './client';
import type { DeploymentHistoryEntry } from '../types/deployment';

export function fetchDeploymentHistory(
  teamId: string,
  appId: string,
  environmentId: number,
): Promise<DeploymentHistoryEntry[]> {
  return apiFetch<DeploymentHistoryEntry[]>(
    `/api/v1/teams/${teamId}/applications/${appId}/deployments?environmentId=${environmentId}`,
  );
}
