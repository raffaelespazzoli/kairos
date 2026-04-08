export type BuildStatus = 'Passed' | 'Failed' | 'Building' | 'Cancelled' | 'Pending';

export interface BuildSummary {
  buildId: string;
  status: BuildStatus;
  startedAt: string;
  completedAt: string | null;
  duration: string | null;
  imageReference: string | null;
  applicationName: string;
  tektonDeepLink: string | null;
}

export interface BuildDetail {
  buildId: string;
  status: BuildStatus;
  startedAt: string;
  completedAt: string | null;
  duration: string | null;
  applicationName: string;
  imageReference: string | null;
  failedStageName: string | null;
  errorSummary: string | null;
  currentStage: string | null;
  tektonDeepLink: string | null;
}
