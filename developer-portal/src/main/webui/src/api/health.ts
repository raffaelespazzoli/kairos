import { apiFetch } from './client';
import type { HealthResponse } from '../types/health';

export function fetchHealth(
  teamId: string,
  appId: string,
): Promise<HealthResponse> {
  return apiFetch<HealthResponse>(
    `/api/v1/teams/${teamId}/applications/${appId}/health`,
  );
}
