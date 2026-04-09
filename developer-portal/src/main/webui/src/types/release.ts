export interface ReleaseSummary {
  version: string;
  createdAt: string;
  buildId: string | null;
  commitSha: string;
  imageReference: string | null;
}

export interface CreateReleaseRequest {
  buildId: string;
  version: string;
}
