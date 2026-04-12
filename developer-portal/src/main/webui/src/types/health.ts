export type GoldenSignalType =
  | 'LATENCY_P50'
  | 'LATENCY_P95'
  | 'LATENCY_P99'
  | 'TRAFFIC_RATE'
  | 'ERROR_RATE'
  | 'SATURATION_CPU'
  | 'SATURATION_MEMORY';

export interface GoldenSignal {
  name: string;
  value: number;
  unit: string;
  type: GoldenSignalType;
}

export type HealthStatus = 'HEALTHY' | 'UNHEALTHY' | 'DEGRADED' | 'NO_DATA';

export interface HealthStatusDto {
  status: HealthStatus;
  goldenSignals: GoldenSignal[];
  namespace: string;
}

export interface EnvironmentHealthDto {
  environmentName: string;
  healthStatus: HealthStatusDto | null;
  grafanaDeepLink: string | null;
  error: string | null;
}

export interface HealthResponse {
  environments: EnvironmentHealthDto[];
}
