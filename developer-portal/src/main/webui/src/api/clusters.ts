import { apiFetch } from './client';
import type { Cluster, CreateClusterRequest, UpdateClusterRequest } from '../types/cluster';

export function fetchClusters(): Promise<Cluster[]> {
  return apiFetch<Cluster[]>('/api/v1/admin/clusters');
}

export function createCluster(request: CreateClusterRequest): Promise<Cluster> {
  return apiFetch<Cluster>('/api/v1/admin/clusters', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function updateCluster(clusterId: number, request: UpdateClusterRequest): Promise<Cluster> {
  return apiFetch<Cluster>(`/api/v1/admin/clusters/${clusterId}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

export function deleteCluster(clusterId: number): Promise<void> {
  return apiFetch<void>(`/api/v1/admin/clusters/${clusterId}`, {
    method: 'DELETE',
  });
}
