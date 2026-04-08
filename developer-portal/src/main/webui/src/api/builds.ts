import { apiFetch, apiFetchText } from './client';
import type { BuildSummary, BuildDetail } from '../types/build';

export function fetchBuilds(
  teamId: string,
  appId: string,
  signal?: AbortSignal,
): Promise<BuildSummary[]> {
  return apiFetch<BuildSummary[]>(
    `/api/v1/teams/${teamId}/applications/${appId}/builds`,
    { signal },
  );
}

export function triggerBuild(teamId: string, appId: string): Promise<BuildSummary> {
  return apiFetch<BuildSummary>(`/api/v1/teams/${teamId}/applications/${appId}/builds`, {
    method: 'POST',
  });
}

export function fetchBuildDetail(
  teamId: string,
  appId: string,
  buildId: string,
  signal?: AbortSignal,
): Promise<BuildDetail> {
  return apiFetch<BuildDetail>(
    `/api/v1/teams/${teamId}/applications/${appId}/builds/${buildId}`,
    { signal },
  );
}

export function fetchBuildLogs(
  teamId: string,
  appId: string,
  buildId: string,
  signal?: AbortSignal,
): Promise<string> {
  return apiFetchText(
    `/api/v1/teams/${teamId}/applications/${appId}/builds/${buildId}/logs`,
    { signal },
  );
}
