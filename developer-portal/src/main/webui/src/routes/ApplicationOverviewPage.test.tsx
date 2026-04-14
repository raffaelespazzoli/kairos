import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApplicationOverviewPage } from './ApplicationOverviewPage';
import { ApplicationLayout } from '../components/layout/ApplicationLayout';
import { ApplicationsProvider } from '../contexts/ApplicationsContext';
import type { ApplicationSummary } from '../types/application';
import type { PortalError } from '../types/error';
import type { EnvironmentChainResponse } from '../types/environment';
import type { BuildSummary } from '../types/build';
import type { AppActivityResponse } from '../types/dashboard';

const sampleApps: ApplicationSummary[] = [
  {
    id: 42,
    name: 'payments-api',
    runtimeType: 'quarkus',
    onboardedAt: '2026-04-01T10:00:00Z',
    onboardingPrUrl: 'https://github.com/org/infra/pull/123',
    gitRepoUrl: 'https://github.com/org/payments-api.git',
    devSpacesDeepLink: 'https://devspaces.example.com/#/https://github.com/org/payments-api.git',
  },
  {
    id: 99,
    name: 'no-pr-app',
    runtimeType: 'spring-boot',
    onboardedAt: '',
    onboardingPrUrl: '',
    gitRepoUrl: 'https://github.com/org/no-pr-app.git',
    devSpacesDeepLink: null,
  },
];

const sampleEnvData: EnvironmentChainResponse = {
  environments: [
    {
      environmentName: 'dev',
      clusterName: 'ocp-dev',
      namespace: 'payments-dev',
      promotionOrder: 0,
      status: 'HEALTHY',
      deployedVersion: 'v1.4.2',
      lastDeployedAt: new Date(Date.now() - 7200000).toISOString(),
      argocdDeepLink: 'https://argocd/applications/payments-run-dev',
      vaultDeepLink: 'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-dev/static-secrets',
      grafanaDeepLink: null,
      environmentId: 1,
      isProduction: false,
    },
    {
      environmentName: 'staging',
      clusterName: 'ocp-staging',
      namespace: 'payments-staging',
      promotionOrder: 1,
      status: 'NOT_DEPLOYED',
      deployedVersion: null,
      lastDeployedAt: null,
      argocdDeepLink: null,
      vaultDeepLink: 'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-staging/static-secrets',
      grafanaDeepLink: null,
      isProduction: false,
      environmentId: 2,
    },
  ],
  argocdError: null,
};

let mockEnvResult = {
  data: sampleEnvData as EnvironmentChainResponse | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

let mockReleasesResult = {
  data: null as unknown[] | null,
  error: null as PortalError | null,
  isLoading: false,
};

let mockBuildsResult = {
  data: null as BuildSummary[] | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
  prepend: vi.fn(),
};

let mockActivityResult = {
  data: null as AppActivityResponse | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

vi.mock('../hooks/useEnvironments', () => ({
  useEnvironments: () => mockEnvResult,
}));

vi.mock('../hooks/useReleases', () => ({
  useReleases: () => mockReleasesResult,
}));

vi.mock('../hooks/useHealth', () => ({
  useHealth: () => ({ data: null, error: null, isLoading: false, refresh: vi.fn() }),
}));

vi.mock('../hooks/useBuilds', () => ({
  useBuilds: () => mockBuildsResult,
}));

vi.mock('../hooks/useDashboard', () => ({
  useAppActivity: () => mockActivityResult,
}));

function renderPage(
  route: string,
  applications: ApplicationSummary[] = sampleApps,
  isLoading = false,
  error: PortalError | null = null,
) {
  return render(
    <ApplicationsProvider value={{ applications, isLoading, error }}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/teams/:teamId/apps/:appId" element={<ApplicationLayout />}>
            <Route index element={<ApplicationOverviewPage />} />
            <Route path="overview" element={<ApplicationOverviewPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ApplicationsProvider>,
  );
}

describe('ApplicationOverviewPage', () => {
  beforeEach(() => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: null, error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
  });

  it('shows loading spinner while applications are loading', () => {
    mockEnvResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: null, error: null, isLoading: false };
    renderPage('/teams/1/apps/42', [], true);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows error alert when applications fetch fails', () => {
    mockEnvResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: null, error: null, isLoading: false };
    renderPage('/teams/1/apps/42', [], false, {
      error: 'unknown',
      message: 'Failed to load applications',
      timestamp: '2026-04-06T00:00:00Z',
    });
    expect(screen.getByText('Failed to load applications')).toBeInTheDocument();
  });

  it('shows error when application is not found', () => {
    mockEnvResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: null, error: null, isLoading: false };
    renderPage('/teams/1/apps/9999');
    expect(screen.getByText('Application not found')).toBeInTheDocument();
  });

  it('renders application name as heading', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    expect(
      screen.getByRole('heading', { name: 'payments-api' }),
    ).toBeInTheDocument();
  });

  it('displays runtime type', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Runtime')).toBeInTheDocument();
    expect(screen.getByText('quarkus')).toBeInTheDocument();
  });

  it('displays onboarded date when present', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Onboarded')).toBeInTheDocument();
  });

  it('displays onboarding PR link when present', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    const prLink = screen.getByText('View onboarding PR');
    expect(prLink.closest('a')).toHaveAttribute(
      'href',
      'https://github.com/org/infra/pull/123',
    );
    expect(prLink.closest('a')).toHaveAttribute('target', '_blank');
  });

  it('hides onboarding PR section when URL is empty', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/99');
    expect(screen.queryByText('View onboarding PR')).not.toBeInTheDocument();
  });

  it('renders environment chain when data loads', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('dev')).toBeInTheDocument();
    expect(screen.getByText('staging')).toBeInTheDocument();
    expect(
      screen.getByRole('list', { name: /Environment promotion chain/i }),
    ).toBeInTheDocument();
  });

  it('shows environment loading spinner', () => {
    mockEnvResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
    mockReleasesResult = { data: null, error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    expect(screen.getAllByRole('progressbar').length).toBeGreaterThanOrEqual(1);
  });

  it('shows error when environment fetch fails', () => {
    mockEnvResult = {
      data: null,
      error: {
        error: 'unknown',
        message: 'Failed to load environments',
        timestamp: '2026-04-06T00:00:00Z',
      },
      isLoading: false,
      refresh: vi.fn(),
    };
    mockReleasesResult = { data: null, error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Failed to load environments')).toBeInTheDocument();
  });

  it('shows error when releases fetch fails', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = {
      data: null,
      error: {
        error: 'unknown',
        message: 'Failed to load releases',
        timestamp: '2026-04-10T00:00:00Z',
      },
      isLoading: false,
    };

    renderPage('/teams/1/apps/42');

    expect(screen.getByText('Failed to load releases')).toBeInTheDocument();
  });

  it('renders BuildTable when builds are available', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = {
      data: [
        { buildId: 'build-001', status: 'Passed', startedAt: '2026-04-13T10:00:00Z', completedAt: '2026-04-13T10:02:15Z', duration: '2m 15s', imageReference: 'quay.io/org/app:build-001', tektonDeepLink: 'https://tekton/runs/build-001', applicationName: 'payments-api' },
        { buildId: 'build-002', status: 'Failed', startedAt: '2026-04-13T09:00:00Z', completedAt: '2026-04-13T09:01:05Z', duration: '1m 05s', imageReference: null, tektonDeepLink: 'https://tekton/runs/build-002', applicationName: 'payments-api' },
      ],
      error: null,
      isLoading: false,
      refresh: vi.fn(),
      prepend: vi.fn(),
    };
    mockActivityResult = { data: { events: [], error: null }, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByLabelText('Builds table')).toBeInTheDocument();
  });

  it('renders only 5 builds max when more are available', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    const sevenBuilds: BuildSummary[] = Array.from({ length: 7 }, (_, i) => ({
      buildId: `build-${String(i + 1).padStart(3, '0')}`,
      status: 'Passed' as const,
      startedAt: `2026-04-13T${String(10 - i).padStart(2, '0')}:00:00Z`,
      completedAt: null,
      duration: '1m 00s',
      imageReference: `quay.io/org/app:build-${i + 1}`,
      tektonDeepLink: `https://tekton/runs/build-${i + 1}`,
      applicationName: 'payments-api',
    }));
    mockBuildsResult = { data: sevenBuilds, error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = { data: { events: [], error: null }, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    const table = screen.getByLabelText('Builds table');
    const rows = table.querySelectorAll('tbody tr');
    expect(rows.length).toBe(5);
  });

  it('renders "View all builds" link', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = { data: { events: [], error: null }, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('View all builds')).toBeInTheDocument();
  });

  it('renders ActivityFeed when events are available', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = {
      data: {
        events: [
          { eventType: 'deployment', applicationId: 42, applicationName: 'payments-api', reference: 'v2.0.0-rc1', timestamp: '2026-04-13T12:00:00Z', status: 'Deployed', actor: 'dev-user', environmentName: 'dev' },
        ],
        error: null,
      },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('v2.0.0-rc1')).toBeInTheDocument();
    expect(screen.getByLabelText('Recent activity')).toBeInTheDocument();
  });

  it('shows empty state when no builds exist', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = { data: { events: [], error: null }, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('No builds yet')).toBeInTheDocument();
  });

  it('shows empty state when no activity events exist', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = { data: { events: [], error: null }, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('No recent activity')).toBeInTheDocument();
  });

  it('shows loading spinner when builds are loading', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: null, error: null, isLoading: true, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByLabelText('Loading builds')).toBeInTheDocument();
  });

  it('shows loading spinner when activity is loading', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: null, error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByLabelText('Loading activity')).toBeInTheDocument();
  });

  it('shows warning alert when build fetch fails', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = {
      data: null,
      error: { error: 'unknown', message: 'Tekton unreachable', timestamp: '2026-04-13T00:00:00Z' },
      isLoading: false,
      refresh: vi.fn(),
      prepend: vi.fn(),
    };
    mockActivityResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Could not load builds')).toBeInTheDocument();
  });

  it('shows warning alert when activity fetch fails', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: null, error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = {
      data: null,
      error: { error: 'unknown', message: 'Activity unavailable', timestamp: '2026-04-13T00:00:00Z' },
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Could not load activity')).toBeInTheDocument();
  });

  it('shows warning alert when activity sources partially fail', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
    mockActivityResult = {
      data: { events: [
        { eventType: 'deployment', applicationId: 42, applicationName: 'payments-api', reference: 'v1.0.0', timestamp: '2026-04-13T12:00:00Z', status: 'Deployed', actor: 'dev-user', environmentName: 'dev' },
      ], error: 'Build activity unavailable for payments-api' },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Some activity sources unavailable')).toBeInTheDocument();
    expect(screen.getByLabelText('Recent activity')).toBeInTheDocument();
  });

  it('renders refresh button', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    expect(screen.getByRole('button', { name: /Refresh/i })).toBeInTheDocument();
  });

  it('shows DevSpaces button when devSpacesDeepLink is present', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/42');
    const link = screen.getByText('Open in DevSpaces ↗');
    expect(link.closest('a')).toHaveAttribute(
      'href',
      'https://devspaces.example.com/#/https://github.com/org/payments-api.git',
    );
    expect(link.closest('a')).toHaveAttribute('target', '_blank');
  });

  it('hides DevSpaces button when devSpacesDeepLink is null', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    mockReleasesResult = { data: [], error: null, isLoading: false };
    renderPage('/teams/1/apps/99');
    expect(screen.queryByText('Open in DevSpaces ↗')).not.toBeInTheDocument();
  });
});
