import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { EnvironmentCard } from './EnvironmentCard';
import type { EnvironmentChainEntry } from '../../types/environment';
import type { DeploymentHistoryEntry } from '../../types/deployment';
import type { ReleaseSummary } from '../../types/release';
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

const mockTriggerDeployment = vi.fn();
vi.mock('../../api/deployments', () => ({
  triggerDeployment: (...args: unknown[]) => mockTriggerDeployment(...args),
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

function makeReleases(): ReleaseSummary[] {
  return [
    {
      version: 'v2.1.1',
      createdAt: new Date(Date.now() - 7200000).toISOString(),
      buildId: 'build-1',
      commitSha: 'abc123',
      imageReference: 'registry/app:v2.1.1',
    },
    {
      version: 'v2.1.0',
      createdAt: new Date(Date.now() - 86400000).toISOString(),
      buildId: 'build-2',
      commitSha: 'def456',
      imageReference: 'registry/app:v2.1.0',
    },
  ];
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
    mockTriggerDeployment.mockReset();
    mockTriggerDeployment.mockResolvedValue({
      deploymentId: 'dep-1',
      releaseVersion: 'v2.1.1',
      environmentName: 'dev',
      status: 'Deploying',
      startedAt: new Date().toISOString(),
    });
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

  it('shows promote button for healthy env with next env and nextEnvironmentId', () => {
    render(
      <EnvironmentCard
        entry={makeEntry()}
        nextEnvName="staging"
        nextEnvironmentId={99}
        teamId="1"
        appId="42"
      />,
    );

    expect(
      screen.getByRole('button', { name: /Promote to staging/i }),
    ).toBeInTheDocument();
  });

  it('does not show promote button for last environment (no nextEnvName)', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ promotionOrder: 2 })}
        teamId="1"
        appId="42"
      />,
    );

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
      <EnvironmentCard
        entry={makeEntry({ status: 'DEPLOYING' })}
        releases={makeReleases()}
      />,
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

  it('shows ArgoCD deep link on UNHEALTHY card footer without promote button', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'UNHEALTHY', deployedVersion: 'v1.3.0' })}
        nextEnvName="staging"
        nextEnvironmentId={99}
      />,
    );

    expect(
      screen.getByRole('link', { name: 'Open dev in ArgoCD' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /Promote/i }),
    ).not.toBeInTheDocument();
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

  // --- Deploy & Promote action tests ---

  it('shows Deploy dropdown when NOT_DEPLOYED and releases exist', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'NOT_DEPLOYED', deployedVersion: null, lastDeployedAt: null })}
        isFirstNotDeployed
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    expect(screen.getByTestId('deploy-toggle')).toBeInTheDocument();
    expect(screen.getByText('Deploy')).toBeInTheDocument();
  });

  it('does not show Deploy dropdown when NOT_DEPLOYED and no releases', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'NOT_DEPLOYED', deployedVersion: null })}
        isFirstNotDeployed
        teamId="1"
        appId="42"
        releases={[]}
      />,
    );

    expect(screen.queryByTestId('deploy-toggle')).not.toBeInTheDocument();
  });

  it('shows both Deploy dropdown and Promote button when HEALTHY with releases and nextEnv', () => {
    render(
      <EnvironmentCard
        entry={makeEntry()}
        nextEnvName="staging"
        nextEnvironmentId={99}
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    expect(screen.getByTestId('deploy-toggle')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Promote to staging/i })).toBeInTheDocument();
  });

  it('shows Deploy dropdown for HEALTHY last env (no promote)', () => {
    render(
      <EnvironmentCard
        entry={makeEntry()}
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    expect(screen.getByTestId('deploy-toggle')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Promote/i })).not.toBeInTheDocument();
  });

  it('Deploy dropdown shows release versions with relative timestamps', async () => {
    const user = userEvent.setup();
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'NOT_DEPLOYED', deployedVersion: null })}
        isFirstNotDeployed
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    await user.click(screen.getByTestId('deploy-toggle'));

    expect(screen.getByText(/v2\.1\.1/)).toBeInTheDocument();
    expect(screen.getByText(/v2\.1\.0/)).toBeInTheDocument();
  });

  it('selecting a release calls triggerDeployment with correct params', async () => {
    const user = userEvent.setup();
    const onDeploymentInitiated = vi.fn();
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'NOT_DEPLOYED', deployedVersion: null, environmentId: 42 })}
        isFirstNotDeployed
        teamId="1"
        appId="42"
        releases={makeReleases()}
        onDeploymentInitiated={onDeploymentInitiated}
      />,
    );

    await user.click(screen.getByTestId('deploy-toggle'));
    await user.click(screen.getByText(/v2\.1\.1/));

    await waitFor(() => {
      expect(mockTriggerDeployment).toHaveBeenCalledWith('1', '42', {
        releaseVersion: 'v2.1.1',
        environmentId: 42,
      });
    });
    await waitFor(() => {
      expect(onDeploymentInitiated).toHaveBeenCalled();
    });
  });

  it('promote button click calls triggerDeployment with entry.deployedVersion and nextEnvironmentId', async () => {
    const user = userEvent.setup();
    const onDeploymentInitiated = vi.fn();
    render(
      <EnvironmentCard
        entry={makeEntry({ deployedVersion: 'v1.4.2', environmentId: 42 })}
        nextEnvName="staging"
        nextEnvironmentId={99}
        teamId="1"
        appId="42"
        onDeploymentInitiated={onDeploymentInitiated}
      />,
    );

    await user.click(screen.getByRole('button', { name: /Promote to staging/i }));

    await waitFor(() => {
      expect(mockTriggerDeployment).toHaveBeenCalledWith('1', '42', {
        releaseVersion: 'v1.4.2',
        environmentId: 99,
      });
    });
    await waitFor(() => {
      expect(onDeploymentInitiated).toHaveBeenCalled();
    });
  });

  it('shows inline Alert on deployment failure', async () => {
    mockTriggerDeployment.mockRejectedValueOnce(new Error('Environment not found'));
    const user = userEvent.setup();
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'NOT_DEPLOYED', deployedVersion: null, environmentId: 42 })}
        isFirstNotDeployed
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    await user.click(screen.getByTestId('deploy-toggle'));
    await user.click(screen.getByText(/v2\.1\.1/));

    await waitFor(() => {
      expect(screen.getByText('Environment not found')).toBeInTheDocument();
    });
  });

  it('shows inline Alert on promote failure', async () => {
    mockTriggerDeployment.mockRejectedValueOnce(new Error('Deployment rejected'));
    const user = userEvent.setup();
    render(
      <EnvironmentCard
        entry={makeEntry({ deployedVersion: 'v1.4.2', environmentId: 42 })}
        nextEnvName="staging"
        nextEnvironmentId={99}
        teamId="1"
        appId="42"
      />,
    );

    await user.click(screen.getByRole('button', { name: /Promote to staging/i }));

    await waitFor(() => {
      expect(screen.getByText('Deployment rejected')).toBeInTheDocument();
    });
  });

  it('does not show Deploy or Promote for UNKNOWN status', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'UNKNOWN', deployedVersion: null })}
        nextEnvName="staging"
        nextEnvironmentId={99}
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    expect(screen.queryByTestId('deploy-toggle')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Promote/i })).not.toBeInTheDocument();
  });

  it('does not show Deploy for NOT_DEPLOYED environments that are not first in chain', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'NOT_DEPLOYED', deployedVersion: null })}
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    expect(screen.queryByTestId('deploy-toggle')).not.toBeInTheDocument();
  });

  it('does not show Deploy when environmentId is missing', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ status: 'NOT_DEPLOYED', deployedVersion: null, environmentId: null })}
        isFirstNotDeployed
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    expect(screen.queryByTestId('deploy-toggle')).not.toBeInTheDocument();
  });

  it('does not show Promote when deployedVersion is missing', () => {
    render(
      <EnvironmentCard
        entry={makeEntry({ deployedVersion: null })}
        nextEnvName="staging"
        nextEnvironmentId={99}
        teamId="1"
        appId="42"
      />,
    );

    expect(
      screen.queryByRole('button', { name: /Promote to staging/i }),
    ).not.toBeInTheDocument();
  });

  it('keeps the Promote button mounted with spinner while promote is pending', async () => {
    let resolveDeployment: (() => void) | undefined;
    mockTriggerDeployment.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveDeployment = () => resolve({
            deploymentId: 'dep-2',
            releaseVersion: 'v1.4.2',
            environmentName: 'staging',
            status: 'Deploying',
            startedAt: new Date().toISOString(),
          });
        }),
    );

    const user = userEvent.setup();
    render(
      <EnvironmentCard
        entry={makeEntry()}
        nextEnvName="staging"
        nextEnvironmentId={99}
        teamId="1"
        appId="42"
        releases={makeReleases()}
      />,
    );

    await user.click(screen.getByRole('button', { name: /Promote to staging/i }));

    expect(screen.getByRole('button', { name: /Promoting/i })).toBeInTheDocument();
    expect(screen.getByTestId('deploy-toggle')).toHaveTextContent('Deploy');

    resolveDeployment?.();
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /Promote to staging/i }),
      ).toBeInTheDocument();
    });
  });
});
