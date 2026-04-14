import { useApiFetch } from './useApiFetch';
import type { TeamDashboardResponse, AppActivityResponse } from '../types/dashboard';

export function useDashboard(teamId: string | undefined) {
  const path = teamId ? `/api/v1/teams/${teamId}/dashboard` : null;
  return useApiFetch<TeamDashboardResponse>(path);
}

export function useAppActivity(teamId: string | undefined, appId: string | undefined) {
  const path = teamId && appId
    ? `/api/v1/teams/${teamId}/applications/${appId}/activity`
    : null;
  return useApiFetch<AppActivityResponse>(path);
}
