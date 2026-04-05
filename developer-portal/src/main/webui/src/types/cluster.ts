export interface Cluster {
  id: number;
  name: string;
  apiServerUrl: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateClusterRequest {
  name: string;
  apiServerUrl: string;
}

export interface UpdateClusterRequest {
  name: string;
  apiServerUrl: string;
}
