import { apiFetch } from './client';
import type { DoraMetricsResponse } from '../types/dora';

export function fetchDora(
  teamId: string,
  appId: string,
  timeRange?: string,
): Promise<DoraMetricsResponse> {
  let path = `/api/v1/teams/${teamId}/applications/${appId}/dora`;
  if (timeRange) {
    path += `?timeRange=${encodeURIComponent(timeRange)}`;
  }
  return apiFetch<DoraMetricsResponse>(path);
}
