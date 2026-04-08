export interface ReleaseSummary {
  version: string;
  createdAt: string;
  buildId: string;
  commitSha: string | null;
  imageReference: string | null;
}

export interface CreateReleaseRequest {
  buildId: string;
  version: string;
}
