import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ApplicationHealthPage } from './ApplicationHealthPage';
import type { HealthResponse } from '../types/health';
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

let mockHealthResult: {
  data: HealthResponse | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

vi.mock('../hooks/useHealth', () => ({
  useHealth: () => mockHealthResult,
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
});

describe('ApplicationHealthPage', () => {
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

  it('renders healthy environment with golden signal metrics', () => {
    mockHealthResult = { data: healthyResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByText('dev')).toBeInTheDocument();
    expect(screen.getByText('✓ Healthy')).toBeInTheDocument();
    expect(screen.getByText('245ms')).toBeInTheDocument();
    expect(screen.getByText('42.5 req/s')).toBeInTheDocument();
    expect(screen.getByText('0.3%')).toBeInTheDocument();
  });

  it('renders unhealthy environment with danger badge', () => {
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
    expect(screen.getByText('✓ Healthy')).toBeInTheDocument();
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

  it('renders page title Health', () => {
    mockHealthResult = { data: healthyResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByRole('heading', { name: 'Health' })).toBeInTheDocument();
  });

  it('does not show Grafana link when grafanaDeepLink is null', () => {
    mockHealthResult = { data: noDataResponse, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.queryByText(/View in Grafana/)).not.toBeInTheDocument();
  });
});
