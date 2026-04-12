import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { DoraTrendChart } from './DoraTrendChart';
import type { DoraMetricDto } from '../../types/dora';

vi.mock('@patternfly/react-charts/victory', () => ({
  Chart: ({ children }: { children: React.ReactNode }) => <div data-testid="chart">{children}</div>,
  ChartAxis: () => <div data-testid="chart-axis" />,
  ChartGroup: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  ChartLine: () => <div data-testid="chart-line" />,
  ChartVoronoiContainer: () => <div />,
}));

const metricsWithData: DoraMetricDto[] = [
  {
    type: 'DEPLOYMENT_FREQUENCY',
    currentValue: 4.2,
    previousValue: 3.6,
    trend: 'IMPROVING',
    percentageChange: 16.7,
    unit: '/wk',
    timeSeries: [
      { timestamp: 1712345678, value: 4.0 },
      { timestamp: 1712432078, value: 4.2 },
    ],
  },
  {
    type: 'LEAD_TIME',
    currentValue: 2.1,
    previousValue: 2.8,
    trend: 'IMPROVING',
    percentageChange: -25.0,
    unit: 'h',
    timeSeries: [
      { timestamp: 1712345678, value: 2.5 },
      { timestamp: 1712432078, value: 2.1 },
    ],
  },
  {
    type: 'CHANGE_FAILURE_RATE',
    currentValue: 2.3,
    previousValue: 3.1,
    trend: 'IMPROVING',
    percentageChange: -25.8,
    unit: '%',
    timeSeries: [
      { timestamp: 1712345678, value: 3.0 },
      { timestamp: 1712432078, value: 2.3 },
    ],
  },
  {
    type: 'MTTR',
    currentValue: 45,
    previousValue: 48,
    trend: 'STABLE',
    percentageChange: -6.3,
    unit: 'm',
    timeSeries: [
      { timestamp: 1712345678, value: 46 },
      { timestamp: 1712432078, value: 45 },
    ],
  },
];

const metricsNoData: DoraMetricDto[] = metricsWithData.map((m) => ({
  ...m,
  timeSeries: [],
}));

describe('DoraTrendChart', () => {
  it('renders chart components when data is provided', () => {
    render(<DoraTrendChart metrics={metricsWithData} timeRange="30d" />);
    const charts = screen.getAllByTestId('chart');
    expect(charts.length).toBe(4);
  });

  it('renders nothing when no time series data', () => {
    const { container } = render(<DoraTrendChart metrics={metricsNoData} timeRange="30d" />);
    expect(container.firstChild).toBeNull();
  });

  it('renders chart titles', () => {
    render(<DoraTrendChart metrics={metricsWithData} timeRange="30d" />);
    expect(screen.getByText('Deploy Frequency')).toBeInTheDocument();
    expect(screen.getByText('Lead Time')).toBeInTheDocument();
    expect(screen.getByText('Change Failure Rate')).toBeInTheDocument();
    expect(screen.getByText('MTTR')).toBeInTheDocument();
  });
});
