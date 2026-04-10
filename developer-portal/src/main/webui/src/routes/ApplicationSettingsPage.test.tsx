import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { ApplicationSettingsPage } from './ApplicationSettingsPage';
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
      argocdDeepLink: null,
      vaultDeepLink:
        'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-dev/static-secrets',
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
      vaultDeepLink:
        'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-staging/static-secrets',
      grafanaDeepLink: null,
      environmentId: 2,
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
    },
  ],
  argocdError: null,
};

let mockEnvResult = {
  data: envDataWithVault as EnvironmentChainResponse | null,
  error: null as PortalError | null,
  isLoading: false,
  refresh: vi.fn(),
};

vi.mock('../hooks/useEnvironments', () => ({
  useEnvironments: () => mockEnvResult,
}));

function renderPage(route = '/teams/1/apps/42/settings') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route
          path="/teams/:teamId/apps/:appId/settings"
          element={<ApplicationSettingsPage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ApplicationSettingsPage', () => {
  it('renders Settings heading', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByRole('heading', { name: 'Settings' })).toBeInTheDocument();
  });

  it('renders Secrets Management card', () => {
    mockEnvResult = { data: envDataWithVault, error: null, isLoading: false, refresh: vi.fn() };
    renderPage();
    expect(screen.getByText('Secrets Management')).toBeInTheDocument();
  });

  it('renders Vault deep link per environment when vault links are present', () => {
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

  it('shows unconfigured message when no vault links present', () => {
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
});
