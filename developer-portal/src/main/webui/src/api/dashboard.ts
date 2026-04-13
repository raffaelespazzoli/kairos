import { apiFetch } from './client';
import type { TeamDashboardResponse } from '../types/dashboard';

export function fetchTeamDashboard(
  teamId: string,
): Promise<TeamDashboardResponse> {
  return apiFetch<TeamDashboardResponse>(
    `/api/v1/teams/${teamId}/dashboard`,
  );
}
