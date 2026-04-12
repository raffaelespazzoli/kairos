export type DoraMetricType = 'DEPLOYMENT_FREQUENCY' | 'LEAD_TIME' | 'CHANGE_FAILURE_RATE' | 'MTTR';
export type TrendDirection = 'IMPROVING' | 'STABLE' | 'DECLINING';

export interface TimeSeriesPoint {
  timestamp: number;
  value: number;
}

export interface DoraMetricDto {
  type: DoraMetricType;
  currentValue: number;
  previousValue: number;
  trend: TrendDirection;
  percentageChange: number;
  unit: string;
  timeSeries: TimeSeriesPoint[];
}

export interface DoraMetricsResponse {
  metrics: DoraMetricDto[];
  timeRange: string;
  hasData: boolean;
}
