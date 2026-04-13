import type { DoraMetricsResponse } from './dora';

export type EnvironmentDotStatus =
  | 'HEALTHY'
  | 'UNHEALTHY'
  | 'DEPLOYING'
  | 'NOT_DEPLOYED'
  | 'UNKNOWN';

export interface DashboardEnvironmentEntryDto {
  environmentName: string;
  status: EnvironmentDotStatus;
  deployedVersion: string;
  lastDeploymentAt: string | null;
  statusDetail: string | null;
}

export interface ApplicationHealthSummaryDto {
  applicationId: number;
  applicationName: string;
  runtimeType: string;
  environments: DashboardEnvironmentEntryDto[];
}

export interface TeamActivityEventDto {
  eventType: 'build' | 'release' | 'deployment';
  applicationId: number;
  applicationName: string;
  reference: string;
  timestamp: string;
  status: string;
  actor: string;
  environmentName: string | null;
}

export interface TeamDashboardResponse {
  applications: ApplicationHealthSummaryDto[];
  dora: DoraMetricsResponse;
  recentActivity: TeamActivityEventDto[];
  healthError: string | null;
  doraError: string | null;
  activityError: string | null;
}
