import { Grid, GridItem } from '@patternfly/react-core';
import {
  Chart,
  ChartAxis,
  ChartGroup,
  ChartLine,
  ChartVoronoiContainer,
} from '@patternfly/react-charts/victory';
import type { DoraMetricDto, DoraMetricType } from '../../types/dora';

const METRIC_CHART_TITLES: Record<DoraMetricType, string> = {
  DEPLOYMENT_FREQUENCY: 'Deploy Frequency',
  LEAD_TIME: 'Lead Time',
  CHANGE_FAILURE_RATE: 'Change Failure Rate',
  MTTR: 'MTTR',
};

function formatDate(timestamp: number): string {
  return new Date(timestamp * 1000).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
}

interface SingleChartProps {
  metric: DoraMetricDto;
  timeRange: string;
}

function SingleTrendChart({ metric, timeRange }: SingleChartProps) {
  const title = METRIC_CHART_TITLES[metric.type];
  const unit = metric.unit;

  if (metric.timeSeries.length === 0) {
    return null;
  }

  const data = metric.timeSeries.map((p) => ({ x: p.timestamp, y: p.value }));

  return (
    <div>
      <div
        style={{
          fontWeight: 'bold',
          fontSize: 'var(--pf-t--global--font--size--sm)',
          marginBottom: 'var(--pf-t--global--spacer--sm)',
        }}
      >
        {title}
      </div>
      <div style={{ height: '200px' }} aria-label={`${title} trend over last ${timeRange}`}>
        <Chart
          containerComponent={
            <ChartVoronoiContainer
              labels={({ datum }: { datum: { x: number; y: number } }) =>
                `${formatDate(datum.x)}: ${datum.y.toFixed(1)}${unit}`
              }
            />
          }
          height={200}
          padding={{ top: 20, bottom: 40, left: 60, right: 20 }}
        >
          <ChartAxis
            tickFormat={(t: number) => formatDate(t)}
          />
          <ChartAxis
            dependentAxis
            tickFormat={(t: number) => `${t}${unit}`}
          />
          <ChartGroup>
            <ChartLine data={data} />
          </ChartGroup>
        </Chart>
      </div>
    </div>
  );
}

interface DoraTrendChartProps {
  metrics: DoraMetricDto[];
  timeRange: string;
}

export function DoraTrendChart({ metrics, timeRange }: DoraTrendChartProps) {
  const hasAnyData = metrics.some((m) => m.timeSeries.length > 0);

  if (!hasAnyData) {
    return null;
  }

  return (
    <Grid hasGutter>
      {metrics.map((metric) => (
        <GridItem key={metric.type} span={6} md={6} sm={12}>
          <SingleTrendChart metric={metric} timeRange={timeRange} />
        </GridItem>
      ))}
    </Grid>
  );
}
