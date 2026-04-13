import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { TeamDashboardPage } from './TeamDashboardPage';
import { TeamsProvider } from '../contexts/TeamsContext';
import type { TeamDashboardResponse } from '../types/dashboard';
import type { PortalError } from '../types/error';

vi.mock('@patternfly/react-charts/victory', () => ({
  Chart: ({ children, ...props }: any) => <div data-testid="chart" {...props}>{children}</div>,
  ChartArea: ({ 'aria-label': ariaLabel }: Record<string, unknown>) => (
    <div data-testid="chart-area" aria-label={ariaLabel as string} />
  ),
  ChartAxis: () => <div />,
  ChartGroup: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="chart-group">{children}</div>
  ),
  ChartLine: () => <div data-testid="chart-line" />,
  ChartVoronoiContainer: () => <div />,
}));

const activeTeam = { id: 1, name: 'My Team', oidcGroupId: 'default' };

let mockDashboardResult: {
  data: TeamDashboardResponse | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

vi.mock('../hooks/useDashboard', () => ({
  useDashboard: () => mockDashboardResult,
}));

const fullDashboardResponse: TeamDashboardResponse = {
  applications: [
    {
      applicationId: 1,
      applicationName: 'checkout-api',
      runtimeType: 'quarkus',
      environments: [
        {
          environmentName: 'dev',
          status: 'HEALTHY',
          deployedVersion: 'v2.1.0',
          lastDeploymentAt: '2026-04-10T14:30:00Z',
          statusDetail: null,
        },
        {
          environmentName: 'prod',
          status: 'UNHEALTHY',
          deployedVersion: 'v2.0.8',
          lastDeploymentAt: '2026-04-09T10:00:00Z',
          statusDetail: 'High error rate',
        },
      ],
    },
    {
      applicationId: 2,
      applicationName: 'payments-service',
      runtimeType: 'spring-boot',
      environments: [
        {
          environmentName: 'dev',
          status: 'HEALTHY',
          deployedVersion: 'v1.0.0',
          lastDeploymentAt: '2026-04-08T09:00:00Z',
          statusDetail: null,
        },
      ],
    },
  ],
  dora: {
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
  },
  recentActivity: [
    {
      eventType: 'build' as const,
      applicationId: 1,
      applicationName: 'checkout-api',
      reference: '#142',
      timestamp: '2026-04-13T14:00:00Z',
      status: 'Passed',
      actor: 'Marco',
      environmentName: null,
    },
    {
      eventType: 'deployment' as const,
      applicationId: 1,
      applicationName: 'checkout-api',
      reference: 'v2.1.0',
      timestamp: '2026-04-13T12:00:00Z',
      status: 'Deployed',
      actor: 'Alice',
      environmentName: 'qa',
    },
  ],
  healthError: null,
  doraError: null,
  activityError: null,
};

function renderPage() {
  return render(
    <TeamsProvider value={{ teams: [activeTeam], activeTeamId: 1, activeTeam }}>
      <MemoryRouter initialEntries={['/teams/1']}>
        <Routes>
          <Route path="/teams/:teamId" element={<TeamDashboardPage />} />
        </Routes>
      </MemoryRouter>
    </TeamsProvider>,
  );
}

beforeEach(() => {
  mockDashboardResult = {
    data: null,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  };
});

describe('TeamDashboardPage', () => {
  it('shows section-level loading states while dashboard is loading', () => {
    mockDashboardResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
    renderPage();
    expect(screen.getByLabelText('Loading health grid')).toBeInTheDocument();
  });

  it('shows error alert when fetch fails completely', () => {
    mockDashboardResult = {
      data: null,
      error: {
        error: 'unknown',
        message: 'Failed to load dashboard',
        timestamp: '2026-04-13T00:00:00Z',
      },
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('Failed to load dashboard')).toBeInTheDocument();
  });

  it('shows empty state when no applications exist', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        applications: [],
        healthError: null,
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(
      screen.getByText('No applications onboarded yet'),
    ).toBeInTheDocument();
  });

  it('renders DORA stat cards with correct values', () => {
    mockDashboardResult = {
      data: fullDashboardResponse,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getAllByText('Deploy Frequency').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Lead Time').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Change Failure Rate').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('MTTR').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('4.2/wk')).toBeInTheDocument();
  });

  it('renders application health grid with applications', () => {
    mockDashboardResult = {
      data: fullDashboardResponse,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getAllByText('checkout-api').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('payments-service').length).toBeGreaterThanOrEqual(1);
  });

  it('renders page title with team name', () => {
    mockDashboardResult = {
      data: fullDashboardResponse,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('My Team Dashboard')).toBeInTheDocument();
  });

  it('shows DORA warning alert and insufficient cards when doraError is present', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        doraError: 'Prometheus unreachable',
        dora: { metrics: [], timeRange: '30d', hasData: false },
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('DORA metrics unavailable')).toBeInTheDocument();
    expect(screen.getAllByText('Prometheus unreachable').length).toBeGreaterThanOrEqual(1);
    const insufficientLabels = screen.getAllByText('Insufficient data');
    expect(insufficientLabels.length).toBe(4);
  });

  it('shows health error alert alongside grid when healthError present with apps', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        healthError: 'ArgoCD connection failed',
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('Health data unavailable')).toBeInTheDocument();
    expect(screen.getByText('ArgoCD connection failed')).toBeInTheDocument();
    expect(screen.getAllByText('checkout-api').length).toBeGreaterThanOrEqual(1);
  });

  it('renders DORA cards while health section shows error (partial failure)', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        healthError: 'ArgoCD timeout',
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('Health data unavailable')).toBeInTheDocument();
    expect(screen.getAllByText('Deploy Frequency').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('4.2/wk')).toBeInTheDocument();
  });

  it('renders health grid while DORA section shows error (partial failure)', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        doraError: 'DORA metrics failed',
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('DORA metrics unavailable')).toBeInTheDocument();
    expect(screen.getAllByText('checkout-api').length).toBeGreaterThanOrEqual(1);
  });

  it('renders bottom section with two-column layout containing DORA trends and activity feed', () => {
    mockDashboardResult = {
      data: fullDashboardResponse,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('DORA Trends')).toBeInTheDocument();
    expect(screen.getByText('Recent Activity')).toBeInTheDocument();
  });

  it('renders DORA trend charts when dora.hasData is true', () => {
    mockDashboardResult = {
      data: fullDashboardResponse,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getAllByTestId('chart-line').length).toBeGreaterThan(0);
  });

  it('shows insufficient data message when dora.hasData is false', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        dora: { metrics: [], timeRange: '30d', hasData: false },
        doraError: null,
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(
      screen.getByText('Trend data available after 7 days of activity'),
    ).toBeInTheDocument();
  });

  it('renders activity feed events from dashboard response', () => {
    mockDashboardResult = {
      data: fullDashboardResponse,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('#142')).toBeInTheDocument();
    expect(screen.getAllByText('v2.1.0').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Passed')).toBeInTheDocument();
    expect(screen.getByText('Deployed')).toBeInTheDocument();
  });

  it('shows activity feed empty state when no events', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        recentActivity: [],
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(
      screen.getByText('No recent activity across team applications'),
    ).toBeInTheDocument();
  });

  it('shows doraError inline Alert in DORA column while activity feed still renders', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        doraError: 'Prometheus connection failed',
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('DORA trends unavailable')).toBeInTheDocument();
    expect(screen.getAllByText('Prometheus connection failed').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('#142')).toBeInTheDocument();
  });

  it('shows activityError inline Alert in activity column while DORA trends still render', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        activityError: 'Activity service timeout',
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText('Activity data unavailable')).toBeInTheDocument();
    expect(screen.getByText('Activity service timeout')).toBeInTheDocument();
    expect(screen.getAllByTestId('chart-line').length).toBeGreaterThan(0);
  });

  it('shows loading spinners in bottom section columns while loading', () => {
    mockDashboardResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
    renderPage();
    expect(screen.getByLabelText('Loading DORA trends')).toBeInTheDocument();
    expect(screen.getByLabelText('Loading activity feed')).toBeInTheDocument();
  });

  it('renders DORA cards with insufficient data when hasData is false', () => {
    mockDashboardResult = {
      data: {
        ...fullDashboardResponse,
        dora: { metrics: [], timeRange: '30d', hasData: false },
        doraError: null,
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    const insufficientLabels = screen.getAllByText('Insufficient data');
    expect(insufficientLabels.length).toBe(4);
  });
});
