import { apiFetch } from './client';
import type { DeployRequest, DeploymentResponse, DeploymentHistoryEntry } from '../types/deployment';

export function triggerDeployment(
  teamId: string,
  appId: string,
  request: DeployRequest,
  isProduction?: boolean,
): Promise<DeploymentResponse> {
  const url = `/api/v1/teams/${teamId}/applications/${appId}/deployments${isProduction ? '?env=prod' : ''}`;
  return apiFetch<DeploymentResponse>(url, {
    method: 'POST',
    body: JSON.stringify(request),
  });
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
