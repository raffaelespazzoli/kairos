export type DeploymentStatus = 'Deploying' | 'Deployed' | 'Failed';

export interface DeployRequest {
  releaseVersion: string;
  environmentId: number;
}

export interface DeploymentResponse {
  deploymentId: string;
  releaseVersion: string;
  environmentName: string;
  status: string;
  startedAt: string;
}

export interface DeploymentHistoryEntry {
  deploymentId: string;
  releaseVersion: string;
  status: DeploymentStatus;
  startedAt: string;
  completedAt: string | null;
  deployedBy: string;
  environmentName: string;
  argocdDeepLink: string | null;
}
