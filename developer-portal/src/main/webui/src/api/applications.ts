import { apiFetch } from './client';
import type { ApplicationSummary } from '../types/application';

export function fetchApplications(teamId: number): Promise<ApplicationSummary[]> {
  return apiFetch<ApplicationSummary[]>(`/api/v1/teams/${teamId}/applications`);
}
