import { apiFetch } from './client';
import type { ReleaseSummary, CreateReleaseRequest } from '../types/release';

export function fetchReleases(
  teamId: string,
  appId: string,
): Promise<ReleaseSummary[]> {
  return apiFetch<ReleaseSummary[]>(
    `/api/v1/teams/${teamId}/applications/${appId}/releases`,
  );
}

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
