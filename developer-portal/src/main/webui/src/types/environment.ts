export type EnvironmentStatus =
  | 'HEALTHY'
  | 'UNHEALTHY'
  | 'DEPLOYING'
  | 'NOT_DEPLOYED'
  | 'UNKNOWN';

export interface EnvironmentChainEntry {
  environmentName: string;
  clusterName: string | null;
  namespace: string;
  promotionOrder: number;
  status: EnvironmentStatus;
  deployedVersion: string | null;
  lastDeployedAt: string | null;
  argocdDeepLink: string | null;
  vaultDeepLink: string | null;
  grafanaDeepLink: string | null;
  environmentId: number | null;
  isProduction: boolean;
}

export interface EnvironmentChainResponse {
  environments: EnvironmentChainEntry[];
  argocdError: string | null;
}
