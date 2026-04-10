export type DeploymentStatus = 'Deploying' | 'Deployed' | 'Failed';

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
