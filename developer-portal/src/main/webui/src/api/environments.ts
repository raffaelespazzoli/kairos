import { apiFetch } from './client';
import type { EnvironmentChainResponse } from '../types/environment';

export function fetchEnvironmentChain(
  teamId: string,
  appId: string,
): Promise<EnvironmentChainResponse> {
  return apiFetch<EnvironmentChainResponse>(
    `/api/v1/teams/${teamId}/applications/${appId}/environments`,
  );
}
