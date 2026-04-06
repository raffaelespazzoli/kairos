export interface ContractCheck {
  name: string;
  passed: boolean;
  detail: string;
  fixInstruction: string | null;
}

export interface ContractValidationResult {
  allPassed: boolean;
  checks: ContractCheck[];
  runtimeType: string | null;
  detectedEnvironments: string[];
}

export interface ValidateRepoRequest {
  gitRepoUrl: string;
}

export interface PlannedNamespace {
  name: string;
  clusterName: string;
  environmentName: string;
  isBuild: boolean;
}

export interface PlannedArgoCdApp {
  name: string;
  clusterName: string;
  namespace: string;
  chartPath: string;
  valuesFile: string;
  isBuild: boolean;
}

export interface OnboardingPlanRequest {
  gitRepoUrl: string;
  appName: string;
  runtimeType: string;
  detectedEnvironments: string[];
  environmentClusterMap: Record<string, number>;
  buildClusterId: number;
}

export interface OnboardingPlanResult {
  appName: string;
  teamName: string;
  namespaces: PlannedNamespace[];
  argoCdApps: PlannedArgoCdApp[];
  promotionChain: string[];
  generatedManifests: Record<string, string>;
}

export interface OnboardingConfirmRequest {
  appName: string;
  gitRepoUrl: string;
  runtimeType: string;
  detectedEnvironments: string[];
  environmentClusterMap: Record<string, number>;
  buildClusterId: number;
}

export interface OnboardingResult {
  applicationId: number;
  applicationName: string;
  onboardingPrUrl: string;
  namespacesCreated: number;
  argoCdAppsCreated: number;
  promotionChain: string[];
  devSpacesDeepLink: string | null;
}

export interface ProvisioningStep {
  id: string;
  label: string;
  status: 'pending' | 'in-progress' | 'completed' | 'failed';
  error?: string;
}
