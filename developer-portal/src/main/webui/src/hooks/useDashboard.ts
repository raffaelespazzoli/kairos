import { useApiFetch } from './useApiFetch';
import type { TeamDashboardResponse } from '../types/dashboard';

export function useDashboard(teamId: string | undefined) {
  const path = teamId ? `/api/v1/teams/${teamId}/dashboard` : null;
  return useApiFetch<TeamDashboardResponse>(path);
}
