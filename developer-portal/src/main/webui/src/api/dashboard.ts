import { apiFetch } from './client';
import type { TeamDashboardResponse, AppActivityResponse } from '../types/dashboard';

export function fetchTeamDashboard(
  teamId: string,
): Promise<TeamDashboardResponse> {
  return apiFetch<TeamDashboardResponse>(
    `/api/v1/teams/${teamId}/dashboard`,
  );
}

export function fetchAppActivity(
  teamId: string,
  appId: string,
): Promise<AppActivityResponse> {
  return apiFetch<AppActivityResponse>(
    `/api/v1/teams/${teamId}/applications/${appId}/activity`,
  );
}
