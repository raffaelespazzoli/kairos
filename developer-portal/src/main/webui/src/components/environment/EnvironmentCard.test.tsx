import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { EnvironmentCard } from './EnvironmentCard';
import type { EnvironmentChainEntry } from '../../types/environment';
import type { DeploymentHistoryEntry } from '../../types/deployment';
import type { PortalError } from '../../types/error';

type MockDeploymentsResult = {
  data: DeploymentHistoryEntry[] | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

let mockDeploymentsResult: MockDeploymentsResult = {
  data: null,
  error: null,
  isLoading: false,
  refresh: vi.fn(),
};

vi.mock('../../hooks/useDeployments', () => ({
  useDeployments: () => mockDeploymentsResult,
}));

function makeEntry(overrides: Partial<EnvironmentChainEntry> = {}): EnvironmentChainEntry {
  return {
    environmentName: 'dev',
    clusterName: 'ocp-dev-01',
    namespace: 'payments-dev',
    promotionOrder: 0,
    status: 'HEALTHY',
    deployedVersion: 'v1.4.2',
    lastDeployedAt: new Date(Date.now() - 7200000).toISOString(),
    argocdDeepLink: 'https://argocd/applications/payments-run-dev',
    vaultDeepLink: 'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-dev/static-secrets',
    grafanaDeepLink: null,
    environmentId: 42,
    ...overrides,
  };
}

function makeDeployments(): DeploymentHistoryEntry[] {
  return [
    {
      deploymentId: 'sha1abc',
      releaseVersion: 'v1.4.2',
      status: 'Deployed',
      startedAt: new Date(Date.now() - 3600000).toISOString(),
      completedAt: new Date(Date.now() - 3500000).toISOString(),
      deployedBy: 'marco',
      environmentName: 'dev',
      argocdDeepLink: 'https://argocd/applications/payments-run-dev',
    },
    {
      deploymentId: 'sha2bcd',
      releaseVersion: 'v1.4.1',
      status: 'Deployed',
      startedAt: new Date(Date.now() - 86400000).toISOString(),
      completedAt: new Date(Date.now() - 86300000).toISOString(),
      deployedBy: 'anna',
      environmentName: 'dev',
      argocdDeepLink: 'https://argocd/applications/payments-run-dev',
    },
  ];
}

describe('EnvironmentCard', () => {
  beforeEach(() => {
    mockDeploymentsResult = {
      data: null,
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };
  });

  it('renders healthy status with success label', () => {
    render(<EnvironmentCard entry={makeEntry()} nextEnvName="staging" />);

    expect(screen.getByText('✓ Healthy')).toBeInTheDocument();
    expect(screen.getByText('v1.4.2')).toBeInTheDocument();
    expect(screen.getByText('dev')).toBeInTheDocument();
  });

  it('renders unhealthy status with danger label', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'UNHEALTHY', deployedVersion: 'v1.3.0' })}
      />,
    );

    expect(screen.getByText('✕ Unhealthy')).toBeInTheDocument();
  });

  it('renders deploying status with warning label', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'DEPLOYING', deployedVersion: 'v1.4.2' })}
      />,
    );

    expect(screen.getByText(/⟳ Deploying v1\.4\.2/)).toBeInTheDocument();
  });

  it('renders not deployed status with grey label', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({
          status: 'NOT_DEPLOYED',
          deployedVersion: null,
          lastDeployedAt: null,
        })}
      />,
    );

    expect(screen.getByText('— Not deployed')).toBeInTheDocument();
  });

  it('renders unknown status for ArgoCD unreachable', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({
          status: 'UNKNOWN',
          deployedVersion: null,
          lastDeployedAt: null,
          argocdDeepLink: null,
        })}
      />,
    );

    expect(screen.getByText('Status unavailable')).toBeInTheDocument();
  });

  it('shows promote button for healthy env with next env', () => {
    render(<EnvironmentCard entry={makeEntry()} nextEnvName="staging" />);

    expect(
      screen.getByRole('button', { name: /Promote to staging/i }),
    ).toBeInTheDocument();
  });

  it('does not show promote button for last environment', () => {
    render(<EnvironmentCard entry={makeEntry({ promotionOrder: 2 })} />);

    expect(
      screen.queryByRole('button', { name: /Promote to/i }),
    ).not.toBeInTheDocument();
  });

  it('does not show deploy button when release data is unavailable', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'NOT_DEPLOYED', deployedVersion: null })}
      />,
    );

    expect(screen.queryByRole('button', { name: /Deploy/i })).not.toBeInTheDocument();
  });

  it('shows no action buttons while deploying', () => {
    render(
      <EnvironmentCard entry={makeEntry({ status: 'DEPLOYING' })} />,
    );

    expect(screen.queryByRole('button', { name: /Promote/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Deploy/i })).not.toBeInTheDocument();
  });

  it('expands to show details on click', async () => {
    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} nextEnvName="staging" />);

    expect(screen.queryByText(/Namespace:/)).not.toBeInTheDocument();

    await user.click(screen.getByText('dev'));

    expect(screen.getByText(/Namespace: payments-dev/)).toBeInTheDocument();
    expect(screen.getByText(/Cluster: ocp-dev-01/)).toBeInTheDocument();
    expect(screen.getByText(/Open in ArgoCD ↗/)).toBeInTheDocument();
    expect(screen.getByText(/View in Grafana ↗/)).toBeInTheDocument();
  });

  it('shows aria-label on expanded ArgoCD deep link', async () => {
    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} nextEnvName="staging" />);

    await user.click(screen.getByText('dev'));

    expect(
      screen.getByRole('link', { name: 'Open dev in ArgoCD' }),
    ).toBeInTheDocument();
  });

  it('shows aria-label on UNHEALTHY card footer deep link', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'UNHEALTHY', deployedVersion: 'v1.3.0' })}
      />,
    );

    expect(
      screen.getByRole('link', { name: 'Open dev in ArgoCD' }),
    ).toBeInTheDocument();
  });

  it('deep link buttons are reachable via Tab in expanded card', async () => {
    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} nextEnvName="staging" />);

    await user.click(screen.getByText('dev'));

    screen.getByLabelText(/dev environment/).focus();

    const deepLink = screen.getByRole('link', { name: 'Open dev in ArgoCD' });
    let reached = false;
    for (let i = 0; i < 10 && !reached; i++) {
      await user.tab();
      if (document.activeElement === deepLink) {
        reached = true;
      }
    }
    expect(reached).toBe(true);
  });

  it('has correct aria-label', () => {
    render(<EnvironmentCard entry={makeEntry()} />);

    expect(
      screen.getByLabelText('dev environment, version v1.4.2, healthy'),
    ).toBeInTheDocument();
  });

  it('has correct aria-label for unknown status', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({
          status: 'UNKNOWN',
          deployedVersion: null,
        })}
      />,
    );

    expect(
      screen.getByLabelText('dev environment, version none, status unavailable'),
    ).toBeInTheDocument();
  });

  // --- Deployment history tests ---

  it('shows loading spinner when deployment history is loading', async () => {
    mockDeploymentsResult = {
      data: null,
      error: null,
      isLoading: true,
      refresh: vi.fn(),
    };

    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} teamId="1" appId="42" />);
    await user.click(screen.getByText('dev'));

    expect(screen.getByText('Loading deployment history...')).toBeInTheDocument();
  });

  it('renders deployment history table with correct columns', async () => {
    mockDeploymentsResult = {
      data: makeDeployments(),
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };

    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} teamId="1" appId="42" />);
    await user.click(screen.getByText('dev'));

    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('When')).toBeInTheDocument();
    expect(screen.getByText('By')).toBeInTheDocument();
    expect(screen.getByText('v1.4.1')).toBeInTheDocument();
    expect(screen.getByText('marco')).toBeInTheDocument();
    expect(screen.getByText('anna')).toBeInTheDocument();
    expect(screen.getAllByText('Deployed')).toHaveLength(2);
  });

  it('shows correct status label variants', async () => {
    mockDeploymentsResult = {
      data: [
        { ...makeDeployments()[0], status: 'Failed', argocdDeepLink: 'https://argocd/app' },
      ],
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };

    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} teamId="1" appId="42" />);
    await user.click(screen.getByText('dev'));

    expect(screen.getByText('Failed')).toBeInTheDocument();
  });

  it('shows ArgoCD deep link for failed deployments', async () => {
    mockDeploymentsResult = {
      data: [
        { ...makeDeployments()[0], status: 'Failed', argocdDeepLink: 'https://argocd/app' },
      ],
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };

    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} teamId="1" appId="42" />);
    await user.click(screen.getByText('dev'));

    expect(
      screen.getByRole('link', { name: /Investigate v1\.4\.2 failure in ArgoCD/i }),
    ).toBeInTheDocument();
  });

  it('shows empty state when no deployments exist', async () => {
    mockDeploymentsResult = {
      data: [],
      error: null,
      isLoading: false,
      refresh: vi.fn(),
    };

    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} teamId="1" appId="42" />);
    await user.click(screen.getByText('dev'));

    expect(screen.getByText('No deployments yet')).toBeInTheDocument();
  });

  it('shows error alert when deployment fetch fails', async () => {
    mockDeploymentsResult = {
      data: null,
      error: {
        error: 'unknown',
        message: 'Failed to fetch',
        timestamp: new Date().toISOString(),
      },
      isLoading: false,
      refresh: vi.fn(),
    };

    const user = userEvent.setup();
    render(<EnvironmentCard entry={makeEntry()} teamId="1" appId="42" />);
    await user.click(screen.getByText('dev'));

    expect(screen.getByText('Failed to load deployment history')).toBeInTheDocument();
  });
});
