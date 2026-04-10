import { apiFetch } from './client';
import type { DeployRequest, DeploymentResponse, DeploymentHistoryEntry } from '../types/deployment';

export function triggerDeployment(
  teamId: string,
  appId: string,
  request: DeployRequest,
): Promise<DeploymentResponse> {
  return apiFetch<DeploymentResponse>(
    `/api/v1/teams/${teamId}/applications/${appId}/deployments`,
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
  );
}

export function fetchDeploymentHistory(
  teamId: string,
  appId: string,
  environmentId: number,
): Promise<DeploymentHistoryEntry[]> {
  return apiFetch<DeploymentHistoryEntry[]>(
    `/api/v1/teams/${teamId}/applications/${appId}/deployments?environmentId=${environmentId}`,
  );
}
