import { apiFetch } from './client';
import type { TeamSummary } from '../types/team';

export type { TeamSummary } from '../types/team';

export function fetchTeams(): Promise<TeamSummary[]> {
  return apiFetch<TeamSummary[]>('/api/v1/teams');
}
