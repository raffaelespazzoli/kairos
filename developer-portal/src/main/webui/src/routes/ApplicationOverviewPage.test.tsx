import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ApplicationOverviewPage } from './ApplicationOverviewPage';
import { ApplicationLayout } from '../components/layout/ApplicationLayout';
import { ApplicationsProvider } from '../contexts/ApplicationsContext';
import type { ApplicationSummary } from '../types/application';
import type { PortalError } from '../types/error';
import type { EnvironmentChainResponse } from '../types/environment';

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

vi.mock('../hooks/useEnvironments', () => ({
  useEnvironments: () => mockEnvResult,
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
  it('shows loading spinner while applications are loading', () => {
    mockEnvResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42', [], true);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows error alert when applications fetch fails', () => {
    mockEnvResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42', [], false, {
      error: 'unknown',
      message: 'Failed to load applications',
      timestamp: '2026-04-06T00:00:00Z',
    });
    expect(screen.getByText('Failed to load applications')).toBeInTheDocument();
  });

  it('shows error when application is not found', () => {
    mockEnvResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/9999');
    expect(screen.getByText('Application not found')).toBeInTheDocument();
  });

  it('renders application name as heading', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(
      screen.getByRole('heading', { name: 'payments-api' }),
    ).toBeInTheDocument();
  });

  it('displays runtime type', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Runtime')).toBeInTheDocument();
    expect(screen.getByText('quarkus')).toBeInTheDocument();
  });

  it('displays onboarded date when present', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Onboarded')).toBeInTheDocument();
  });

  it('displays onboarding PR link when present', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
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
    renderPage('/teams/1/apps/99');
    expect(screen.queryByText('View onboarding PR')).not.toBeInTheDocument();
  });

  it('renders environment chain when data loads', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('dev')).toBeInTheDocument();
    expect(screen.getByText('staging')).toBeInTheDocument();
    expect(
      screen.getByRole('list', { name: /Environment promotion chain/i }),
    ).toBeInTheDocument();
  });

  it('shows environment loading spinner', () => {
    mockEnvResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
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
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Failed to load environments')).toBeInTheDocument();
  });

  it('renders placeholder cards for builds and activity', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Recent Builds')).toBeInTheDocument();
    expect(screen.getByText('Activity')).toBeInTheDocument();
    expect(screen.getByText('Build history coming in Epic 4.')).toBeInTheDocument();
    expect(screen.getByText('Activity feed coming in Epic 7.')).toBeInTheDocument();
  });

  it('renders refresh button', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
    renderPage('/teams/1/apps/42');
    expect(screen.getByRole('button', { name: /Refresh/i })).toBeInTheDocument();
  });

  it('shows DevSpaces button when devSpacesDeepLink is present', () => {
    mockEnvResult = { data: sampleEnvData, error: null, isLoading: false, refresh: vi.fn() };
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
    renderPage('/teams/1/apps/99');
    expect(screen.queryByText('Open in DevSpaces ↗')).not.toBeInTheDocument();
  });
});
