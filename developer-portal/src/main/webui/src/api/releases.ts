import { apiFetch } from './client';
import type { ReleaseSummary, CreateReleaseRequest } from '../types/release';

export function createRelease(
  teamId: string,
  appId: string,
  request: CreateReleaseRequest,
): Promise<ReleaseSummary> {
  return apiFetch<ReleaseSummary>(
    `/api/v1/teams/${teamId}/applications/${appId}/releases`,
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
  );
}
