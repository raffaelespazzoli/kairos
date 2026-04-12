import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ApplicationHealthPage } from './ApplicationHealthPage';
import type { HealthResponse } from '../types/health';
import type { DoraMetricsResponse } from '../types/dora';
import type { PortalError } from '../types/error';

const healthyResponse: HealthResponse = {
  environments: [
    {
      environmentName: 'dev',
      healthStatus: {
        status: 'HEALTHY',
        namespace: 'payments-dev',
        goldenSignals: [
          { name: 'Latency p50', value: 0.045, unit: 'seconds', type: 'LATENCY_P50' },
          { name: 'Latency p95', value: 0.245, unit: 'seconds', type: 'LATENCY_P95' },
          { name: 'Latency p99', value: 0.89, unit: 'seconds', type: 'LATENCY_P99' },
          { name: 'Traffic', value: 42.5, unit: 'req/s', type: 'TRAFFIC_RATE' },
          { name: 'Error Rate', value: 0.3, unit: '%', type: 'ERROR_RATE' },
          { name: 'CPU', value: 45, unit: '%', type: 'SATURATION_CPU' },
          { name: 'Memory', value: 62, unit: '%', type: 'SATURATION_MEMORY' },
        ],
      },
      grafanaDeepLink: 'https://grafana.example.com/d/abc?var-namespace=payments-dev',
      error: null,
    },
    {
      environmentName: 'staging',
      healthStatus: {
        status: 'UNHEALTHY',
        namespace: 'payments-staging',
        goldenSignals: [
          { name: 'Latency p95', value: 1.5, unit: 'seconds', type: 'LATENCY_P95' },
          { name: 'Traffic', value: 10.2, unit: 'req/s', type: 'TRAFFIC_RATE' },
          { name: 'Error Rate', value: 8.5, unit: '%', type: 'ERROR_RATE' },
          { name: 'CPU', value: 92, unit: '%', type: 'SATURATION_CPU' },
          { name: 'Memory', value: 88, unit: '%', type: 'SATURATION_MEMORY' },
        ],
      },
      grafanaDeepLink: 'https://grafana.example.com/d/abc?var-namespace=payments-staging',
      error: null,
    },
  ],
};

const degradedResponse: HealthResponse = {
  environments: [
    {
      environmentName: 'dev',
      healthStatus: {
        status: 'DEGRADED',
        namespace: 'payments-dev',
        goldenSignals: [
          { name: 'Latency p95', value: 0.7, unit: 'seconds', type: 'LATENCY_P95' },
          { name: 'Traffic', value: 30.0, unit: 'req/s', type: 'TRAFFIC_RATE' },
          { name: 'Error Rate', value: 2.1, unit: '%', type: 'ERROR_RATE' },
          { name: 'CPU', value: 75, unit: '%', type: 'SATURATION_CPU' },
          { name: 'Memory', value: 60, unit: '%', type: 'SATURATION_MEMORY' },
        ],
      },
      grafanaDeepLink: null,
      error: null,
    },
  ],
};

const noDataResponse: HealthResponse = {
  environments: [
    {
      environmentName: 'dev',
      healthStatus: {
        status: 'NO_DATA',
        namespace: 'payments-dev',
        goldenSignals: [],
      },
      grafanaDeepLink: null,
      error: null,
    },
  ],
};

const perEnvErrorResponse: HealthResponse = {
  environments: [
    {
      environmentName: 'dev',
      healthStatus: null,
      grafanaDeepLink: null,
      error: 'Prometheus connection refused for cluster ocp-dev-01',
    },
    {
      environmentName: 'staging',
      healthStatus: {
        status: 'HEALTHY',
        namespace: 'payments-staging',
        goldenSignals: [
          { name: 'Latency p95', value: 0.2, unit: 'seconds', type: 'LATENCY_P95' },
          { name: 'Traffic', value: 20.0, unit: 'req/s', type: 'TRAFFIC_RATE' },
          { name: 'Error Rate', value: 0.1, unit: '%', type: 'ERROR_RATE' },
          { name: 'CPU', value: 30, unit: '%', type: 'SATURATION_CPU' },
          { name: 'Memory', value: 40, unit: '%', type: 'SATURATION_MEMORY' },
        ],
      },
      grafanaDeepLink: 'https://grafana.example.com/d/abc?var-namespace=payments-staging',
      error: null,
    },
  ],
};

const doraResponse: DoraMetricsResponse = {
  metrics: [
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
  ],
  timeRange: '30d',
  hasData: true,
};

const doraNoDataResponse: DoraMetricsResponse = {
  metrics: [],
  timeRange: '30d',
  hasData: false,
};

let mockHealthResult: {
  data: HealthResponse | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

let mockDoraResult: {
  data: DoraMetricsResponse | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

vi.mock('../hooks/useHealth', () => ({
  useHealth: () => mockHealthResult,
}));

vi.mock('../hooks/useDora', () => ({
  useDora: () => mockDoraResult,
}));

vi.mock('@patternfly/react-charts/victory', () => ({
  Chart: ({ children }: { children: React.ReactNode }) => <div data-testid="chart">{children}</div>,
  ChartAxis: () => <div />,
  ChartGroup: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  ChartLine: () => <div />,
  ChartVoronoiContainer: () => <div />,
}));

function renderPage(route = '/teams/1/apps/42/health') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/teams/:teamId/apps/:appId/health" element={<ApplicationHealthPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mockHealthResult = {
    data: null,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  };
  mockDoraResult = {
    data: null,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  };
});

async function switchToDoraTab() {
  const doraTab = screen.getByRole('tab', { name: /DORA Metrics/i });
  await userEvent.click(doraTab);
}

describe('ApplicationHealthPage — sub-tabs', () => {
  it('renders Application Health and DORA Metrics sub-tabs', () => {
    renderPage();
    expect(screen.getByRole('tab', { name: /Application Health/i })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /DORA Metrics/i })).toBeInTheDocument();
  });

  it('defaults to the Application Health tab', () => {
    mockHealthResult = { data: healthyResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByRole('heading', { name: 'Application Health' })).toBeInTheDocument();
  });

  it('switches to DORA Metrics tab on click', async () => {
    mockDoraResult = { data: doraResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    await switchToDoraTab();
    expect(screen.getByRole('heading', { name: 'Delivery Metrics (DORA)' })).toBeInTheDocument();
  });
});

describe('ApplicationHealthPage — Application Health tab', () => {
  it('shows loading spinner while health data is loading', () => {
    mockHealthResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
    renderPage();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows error alert when health fetch fails', () => {
    mockHealthResult = {
      data: null,
      error: {
        error: 'integration-failure',
        message: 'Failed to fetch health data',
        system: 'Prometheus',
        timestamp: '2026-04-12T00:00:00Z',
      },
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('Failed to fetch health data')).toBeInTheDocument();
  });

  it('renders healthy environment with golden signal metrics (first env expanded)', () => {
    mockHealthResult = { data: healthyResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByText('dev')).toBeInTheDocument();
    expect(screen.getByText('✓ Healthy')).toBeInTheDocument();
    expect(screen.getByText('245ms')).toBeInTheDocument();
    expect(screen.getByText('42.5 req/s')).toBeInTheDocument();
    expect(screen.getByText('0.3%')).toBeInTheDocument();
  });

  it('renders second environment collapsed by default', () => {
    mockHealthResult = { data: healthyResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByText('staging')).toBeInTheDocument();
    expect(screen.getByText('✕ Unhealthy')).toBeInTheDocument();
  });

  it('renders degraded environment with warning badge', () => {
    mockHealthResult = { data: degradedResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByText('⟳ Degraded')).toBeInTheDocument();
  });

  it('shows empty state message for NO_DATA environments', () => {
    mockHealthResult = { data: noDataResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(
      screen.getByText('Metrics will appear once the application receives traffic'),
    ).toBeInTheDocument();
  });

  it('shows per-environment error with warning alert', () => {
    mockHealthResult = { data: perEnvErrorResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByText('Health data unavailable')).toBeInTheDocument();
    expect(
      screen.getByText('Prometheus connection refused for cluster ocp-dev-01'),
    ).toBeInTheDocument();
  });

  it('renders Grafana deep link with correct href', () => {
    mockHealthResult = { data: healthyResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    const grafanaLinks = screen.getAllByText(/View in Grafana/);
    expect(grafanaLinks.length).toBeGreaterThanOrEqual(1);
    const link = grafanaLinks[0].closest('a');
    expect(link).toHaveAttribute('href', 'https://grafana.example.com/d/abc?var-namespace=payments-dev');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('renders refresh button that triggers data refetch', async () => {
    const refreshMock = vi.fn();
    mockHealthResult = { data: healthyResponse, error: null, isLoading: false, refresh: refreshMock };
    renderPage();
    const refreshButton = screen.getByRole('button', { name: /Refresh/i });
    expect(refreshButton).toBeInTheDocument();
    await userEvent.click(refreshButton);
    expect(refreshMock).toHaveBeenCalled();
  });

  it('does not show Grafana link when grafanaDeepLink is null', () => {
    mockHealthResult = { data: noDataResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.queryByText(/View in Grafana/)).not.toBeInTheDocument();
  });
});

describe('ApplicationHealthPage — DORA Metrics tab', () => {
  beforeEach(() => {
    mockHealthResult = { data: healthyResponse, error: null, isLoading: false, refresh: vi.fn() };
  });

  it('renders DORA section header', async () => {
    mockDoraResult = { data: doraResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    await switchToDoraTab();
    expect(screen.getByRole('heading', { name: 'Delivery Metrics (DORA)' })).toBeInTheDocument();
  });

  it('renders DORA stat cards when data available', async () => {
    mockDoraResult = { data: doraResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    await switchToDoraTab();
    expect(screen.getAllByText('Deploy Frequency').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Lead Time').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Change Failure Rate').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('MTTR').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('4.2/wk')).toBeInTheDocument();
  });

  it('renders DORA error on DORA tab', async () => {
    mockDoraResult = {
      data: null,
      error: {
        error: 'integration-failure',
        message: 'Delivery metrics unavailable',
        system: 'Prometheus',
        timestamp: '2026-04-12T00:00:00Z',
      },
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    await switchToDoraTab();
    expect(screen.getByText(/Delivery metrics unavailable — metrics system is unreachable/)).toBeInTheDocument();
  });

  it('shows insufficient data state when hasData is false', async () => {
    mockDoraResult = { data: doraNoDataResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    await switchToDoraTab();
    const dashes = screen.getAllByText('—');
    expect(dashes.length).toBeGreaterThanOrEqual(4);
    const insufficientLabels = screen.getAllByText('Insufficient data');
    expect(insufficientLabels.length).toBe(4);
    const insufficientTexts = screen.getAllByText('Available after 7 days of activity');
    expect(insufficientTexts.length).toBe(4);
  });
});
