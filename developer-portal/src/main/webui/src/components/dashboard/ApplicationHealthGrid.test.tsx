import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ApplicationHealthGrid } from './ApplicationHealthGrid';
import type { ApplicationHealthSummaryDto } from '../../types/dashboard';
import type { DoraMetricDto } from '../../types/dora';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@patternfly/react-charts/victory', () => ({
  ChartArea: ({ 'aria-label': ariaLabel }: Record<string, unknown>) => (
    <div data-testid="chart-area" aria-label={ariaLabel as string} />
  ),
  ChartGroup: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="chart-group">{children}</div>
  ),
  ChartVoronoiContainer: () => <div />,
}));

const sampleApps: ApplicationHealthSummaryDto[] = [
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
        environmentName: 'qa',
        status: 'DEPLOYING',
        deployedVersion: 'v2.0.9',
        lastDeploymentAt: '2026-04-10T12:00:00Z',
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
        status: 'NOT_DEPLOYED',
        deployedVersion: '',
        lastDeploymentAt: null,
        statusDetail: null,
      },
    ],
  },
];

const deployFreqMetric: DoraMetricDto = {
  type: 'DEPLOYMENT_FREQUENCY',
  currentValue: 4.2,
  previousValue: 3.6,
  trend: 'IMPROVING',
  percentageChange: 16.7,
  unit: '/wk',
  timeSeries: [
    { timestamp: 1712345678, value: 3.0 },
    { timestamp: 1712432078, value: 4.0 },
    { timestamp: 1712518478, value: 5.0 },
  ],
};

function renderGrid(
  applications: ApplicationHealthSummaryDto[] = sampleApps,
  metric: DoraMetricDto | undefined = deployFreqMetric,
) {
  return render(
    <MemoryRouter initialEntries={['/teams/1']}>
      <Routes>
        <Route
          path="/teams/:teamId"
          element={
            <ApplicationHealthGrid
              applications={applications}
              deploymentFrequencyMetric={metric}
            />
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ApplicationHealthGrid', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  it('renders correct number of rows for given applications', () => {
    renderGrid();
    expect(screen.getByText('checkout-api')).toBeInTheDocument();
    expect(screen.getByText('payments-service')).toBeInTheDocument();
  });

  it('renders environment dots with aria-label for each status', () => {
    renderGrid();
    expect(
      screen.getByLabelText('dev: HEALTHY, v2.1.0'),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('qa: DEPLOYING, v2.0.9'),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('prod: UNHEALTHY, v2.0.8'),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('dev: NOT_DEPLOYED, no version'),
    ).toBeInTheDocument();
  });

  it('renders version text beside each dot', () => {
    renderGrid();
    expect(screen.getAllByText('v2.1.0').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('v2.0.9').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('v2.0.8').length).toBeGreaterThanOrEqual(1);
  });

  it('shows dash for environments without deployed version', () => {
    renderGrid([
      {
        applicationId: 3,
        applicationName: 'no-version-app',
        runtimeType: 'quarkus',
        environments: [
          {
            environmentName: 'dev',
            status: 'NOT_DEPLOYED',
            deployedVersion: '',
            lastDeploymentAt: null,
            statusDetail: null,
          },
        ],
      },
    ]);
    const dashes = screen.getAllByText('—');
    expect(dashes.length).toBeGreaterThanOrEqual(1);
  });

  it('navigates to application overview on row click', async () => {
    const user = userEvent.setup();
    renderGrid();
    const row = screen.getByText('checkout-api').closest('tr')!;
    await user.click(row);
    expect(mockNavigate).toHaveBeenCalledWith('/teams/1/apps/1/overview');
  });

  it('navigates to application overview on Enter key', async () => {
    const user = userEvent.setup();
    renderGrid();
    const row = screen.getByText('checkout-api').closest('tr')!;
    row.focus();
    await user.keyboard('{Enter}');
    expect(mockNavigate).toHaveBeenCalledWith('/teams/1/apps/1/overview');
  });

  it('navigates to application overview on Space key', async () => {
    const user = userEvent.setup();
    renderGrid();
    const row = screen.getByText('checkout-api').closest('tr')!;
    row.focus();
    await user.keyboard(' ');
    expect(mockNavigate).toHaveBeenCalledWith('/teams/1/apps/1/overview');
  });

  it('renders sparkline with aria-label describing trend', () => {
    renderGrid();
    expect(
      screen.getByLabelText(/deployments in the last 30 days for checkout-api/),
    ).toBeInTheDocument();
  });

  it('renders dash when no deployment frequency data', () => {
    renderGrid(sampleApps, undefined);
    const dashes = screen.getAllByText('—');
    expect(dashes.length).toBeGreaterThanOrEqual(1);
  });

  it('returns null when applications array is empty', () => {
    const { container } = renderGrid([]);
    expect(container.querySelector('table')).toBeNull();
  });

  it('renders table with compact variant', () => {
    renderGrid();
    expect(screen.getByLabelText('Application health grid')).toBeInTheDocument();
  });
});
