import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ApplicationEnvironmentsPage } from './ApplicationEnvironmentsPage';
import type { PortalError } from '../types/error';
import type { EnvironmentChainResponse } from '../types/environment';

const envDataWithVault: EnvironmentChainResponse = {
  environments: [
    {
      environmentName: 'dev',
      clusterName: 'ocp-dev',
      namespace: 'payments-dev',
      promotionOrder: 0,
      status: 'HEALTHY',
      deployedVersion: 'v1.4.2',
      lastDeployedAt: '2026-04-01T10:00:00Z',
      argocdDeepLink: 'https://argocd.example.com/applications/payments-run-dev',
      vaultDeepLink:
        'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-dev/static-secrets',
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
      vaultDeepLink:
        'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-staging/static-secrets',
      grafanaDeepLink: null,
      environmentId: 2,
      isProduction: false,
    },
  ],
  argocdError: null,
};

const envDataWithoutVault: EnvironmentChainResponse = {
  environments: [
    {
      environmentName: 'dev',
      clusterName: 'ocp-dev',
      namespace: 'payments-dev',
      promotionOrder: 0,
      status: 'HEALTHY',
      deployedVersion: 'v1.4.2',
      lastDeployedAt: '2026-04-01T10:00:00Z',
      argocdDeepLink: null,
      vaultDeepLink: null,
      grafanaDeepLink: null,
      environmentId: 1,
      isProduction: false,
    },
  ],
  argocdError: null,
};

const envDataWithArgocdError: EnvironmentChainResponse = {
  environments: [
    {
      environmentName: 'dev',
      clusterName: 'ocp-dev',
      namespace: 'payments-dev',
      promotionOrder: 0,
      status: 'UNKNOWN',
      deployedVersion: null,
      lastDeployedAt: null,
      argocdDeepLink: null,
      vaultDeepLink: null,
      grafanaDeepLink: null,
      environmentId: 1,
      isProduction: false,
    },
  ],
  argocdError: 'ArgoCD is unreachable',
};

let mockEnvResult = {
  data: envDataWithVault as EnvironmentChainResponse | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

let mockReleasesResult = {
  data: null as null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

let mockHealthResult = {
  data: null as null,
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
  useHealth: () => mockHealthResult,
}));

vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({
    username: 'developer',
    teamName: 'My Team',
    teamId: '1',
    role: 'member',
    isAuthenticated: true,
    token: 'dev-token',
  }),
}));

vi.mock('../hooks/useDeployments', () => ({
  useDeployments: () => ({
    data: null,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  }),
}));

function renderPage(route = '/teams/1/apps/42/environments') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route
          path="/teams/:teamId/apps/:appId/environments"
          element={<ApplicationEnvironmentsPage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ApplicationEnvironmentsPage', () => {
  it('renders Environments heading', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByRole('heading', { name: 'Environments' })).toBeInTheDocument();
  });

  it('renders environment chain with environment cards', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(
      screen.getByRole('list', { name: 'Environment promotion chain' }),
    ).toBeInTheDocument();
  });

  it('shows loading spinner while environments load', () => {
    mockEnvResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
    renderPage();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows error alert on fetch failure', () => {
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
    renderPage();
    expect(screen.getByText('Failed to load environments')).toBeInTheDocument();
  });

  it('shows ArgoCD warning alert when argocdError is present', () => {
    mockEnvResult = {
      data: envDataWithArgocdError,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(screen.getByText(/ArgoCD is unreachable/)).toBeInTheDocument();
  });

  it('shows health warning alert when health fetch fails', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    mockHealthResult = {
      data: null,
      error: {
        error: 'unknown',
        message: 'Prometheus unreachable',
        timestamp: '2026-04-06T00:00:00Z',
      },
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(
      screen.getByText('Health data unavailable — Prometheus may be unreachable'),
    ).toBeInTheDocument();
    mockHealthResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
  });

  it('renders Secrets Management card heading', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByText('Secrets Management')).toBeInTheDocument();
  });

  it('renders Vault deep links per environment when vaultDeepLink is present', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(
      screen.getByText('dev — Manage secrets in Vault ↗'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('staging — Manage secrets in Vault ↗'),
    ).toBeInTheDocument();
  });

  it('renders Vault links with correct href and target', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    const devLink = screen.getByText('dev — Manage secrets in Vault ↗');
    expect(devLink.closest('a')).toHaveAttribute(
      'href',
      'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-dev/static-secrets',
    );
    expect(devLink.closest('a')).toHaveAttribute('target', '_blank');
  });

  it('shows info alert when environments exist but no vault links configured', () => {
    mockEnvResult = {
      data: envDataWithoutVault,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(
      screen.getByText(
        'Vault URL not configured — contact your platform administrator',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Manage secrets in Vault/)).not.toBeInTheDocument();
  });

  it('does not show unconfigured message when environment list is empty', () => {
    mockEnvResult = {
      data: { environments: [], argocdError: null },
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
    renderPage();
    expect(
      screen.queryByText(
        'Vault URL not configured — contact your platform administrator',
      ),
    ).not.toBeInTheDocument();
  });

  it('renders RefreshButton', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByRole('button', { name: /refresh/i })).toBeInTheDocument();
  });
});
