import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EnvironmentChain } from './EnvironmentChain';
import type { EnvironmentChainEntry } from '../../types/environment';
import type { ReleaseSummary } from '../../types/release';

vi.mock('../../hooks/useDeployments', () => ({
  useDeployments: () => ({ data: null, error: null, isLoading: false, refresh: vi.fn() }),
}));

const mockTriggerDeployment = vi.fn();
vi.mock('../../api/deployments', () => ({
  triggerDeployment: (...args: unknown[]) => mockTriggerDeployment(...args),
}));

const threeEnvs: EnvironmentChainEntry[] = [
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
    status: 'DEPLOYING',
    deployedVersion: 'v1.4.2',
    lastDeployedAt: null,
    argocdDeepLink: 'https://argocd/applications/payments-run-staging',
    vaultDeepLink: 'https://vault.example.com/ui/vault/secrets/applications/team/team-payments-staging/static-secrets',
    grafanaDeepLink: null,
    environmentId: 2,
  },
  {
    environmentName: 'prod',
    clusterName: 'ocp-prod',
    namespace: 'payments-prod',
    promotionOrder: 2,
    status: 'NOT_DEPLOYED',
    deployedVersion: null,
    lastDeployedAt: null,
    argocdDeepLink: null,
    vaultDeepLink: null,
    grafanaDeepLink: null,
    environmentId: 3,
  },
];

describe('EnvironmentChain', () => {
  it('renders one card per environment', () => {
    render(<EnvironmentChain environments={threeEnvs} />);

    expect(screen.getByText('dev')).toBeInTheDocument();
    expect(screen.getByText('staging')).toBeInTheDocument();
    expect(screen.getByText('prod')).toBeInTheDocument();
  });

  it('renders arrow connectors between cards', () => {
    render(<EnvironmentChain environments={threeEnvs} />);

    const list = screen.getByRole('list', { name: /Environment promotion chain/i });
    const arrowSeparators = Array.from(list.children).filter(
      (child) => child.getAttribute('aria-hidden') === 'true',
    );
    expect(arrowSeparators).toHaveLength(2);
  });

  it('renders list semantics for accessibility', () => {
    render(<EnvironmentChain environments={threeEnvs} />);

    expect(
      screen.getByRole('list', { name: /Environment promotion chain/i }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
  });

  it('shows warning alert when argocdError is provided', () => {
    render(
      <EnvironmentChain
        environments={threeEnvs}
        argocdError="Deployment status unavailable — ArgoCD is unreachable"
      />,
    );

    expect(
      screen.getByText('Deployment status unavailable — ArgoCD is unreachable'),
    ).toBeInTheDocument();
  });

  it('does not show alert when argocdError is null', () => {
    render(<EnvironmentChain environments={threeEnvs} argocdError={null} />);

    expect(
      screen.queryByText(/ArgoCD is unreachable/),
    ).not.toBeInTheDocument();
  });

  it('supports keyboard navigation with ArrowRight', async () => {
    const user = userEvent.setup();
    render(<EnvironmentChain environments={threeEnvs} />);

    const firstCard = screen.getByLabelText(/dev environment/);
    firstCard.focus();

    await user.keyboard('{ArrowRight}');

    expect(screen.getByLabelText(/staging environment/)).toHaveFocus();
  });

  it('supports keyboard navigation with ArrowLeft', async () => {
    const user = userEvent.setup();
    render(<EnvironmentChain environments={threeEnvs} />);

    const secondCard = screen.getByLabelText(/staging environment/);
    secondCard.focus();

    await user.keyboard('{ArrowLeft}');

    expect(screen.getByLabelText(/dev environment/)).toHaveFocus();
  });

  it('does not navigate past boundaries', async () => {
    const user = userEvent.setup();
    render(<EnvironmentChain environments={threeEnvs} />);

    const firstCard = screen.getByLabelText(/dev environment/);
    firstCard.focus();

    await user.keyboard('{ArrowLeft}');

    expect(firstCard).toHaveFocus();
  });

  it('renders dynamically for two environments', () => {
    render(<EnvironmentChain environments={threeEnvs.slice(0, 2)} />);

    expect(screen.getAllByRole('listitem')).toHaveLength(2);

    const list = screen.getByRole('list', { name: /Environment promotion chain/i });
    const arrowSeparators = Array.from(list.children).filter(
      (child) => child.getAttribute('aria-hidden') === 'true',
    );
    expect(arrowSeparators).toHaveLength(1);
  });

  it('handles list items with min-width for overflow', () => {
    const { container } = render(<EnvironmentChain environments={threeEnvs} />);

    const listItems = container.querySelectorAll('[role="listitem"]');
    listItems.forEach((item) => {
      expect(item).toHaveStyle({ minWidth: '180px' });
    });
  });

  it('passes releases through to EnvironmentCard (Deploy dropdown renders when releases provided)', () => {
    const releases: ReleaseSummary[] = [
      {
        version: 'v2.1.1',
        createdAt: new Date(Date.now() - 3600000).toISOString(),
        buildId: 'b1',
        commitSha: 'abc',
        imageReference: null,
      },
    ];

    const envsWithNotDeployed: EnvironmentChainEntry[] = [
      { ...threeEnvs[0], status: 'NOT_DEPLOYED', deployedVersion: null },
    ];

    render(
      <EnvironmentChain
        environments={envsWithNotDeployed}
        teamId="1"
        appId="42"
        releases={releases}
      />,
    );

    expect(screen.getByTestId('deploy-toggle')).toBeInTheDocument();
  });

  it('shows Deploy only on the first NOT_DEPLOYED environment in the chain', () => {
    const releases: ReleaseSummary[] = [
      {
        version: 'v2.1.1',
        createdAt: new Date(Date.now() - 3600000).toISOString(),
        buildId: 'b1',
        commitSha: 'abc',
        imageReference: null,
      },
    ];
    const environments: EnvironmentChainEntry[] = [
      { ...threeEnvs[0], status: 'NOT_DEPLOYED', deployedVersion: null },
      { ...threeEnvs[1], status: 'NOT_DEPLOYED', deployedVersion: null },
      { ...threeEnvs[2], status: 'NOT_DEPLOYED', deployedVersion: null },
    ];

    render(
      <EnvironmentChain
        environments={environments}
        teamId="1"
        appId="42"
        releases={releases}
      />,
    );

    expect(screen.getAllByTestId('deploy-toggle')).toHaveLength(1);
  });

  it('computes nextEnvironmentId correctly from environments array', () => {
    mockTriggerDeployment.mockReset();
    mockTriggerDeployment.mockResolvedValue({});
    const releases: ReleaseSummary[] = [
      {
        version: 'v2.1.1',
        createdAt: new Date(Date.now() - 3600000).toISOString(),
        buildId: 'b1',
        commitSha: 'abc',
        imageReference: null,
      },
    ];

    render(
      <EnvironmentChain
        environments={threeEnvs}
        teamId="1"
        appId="42"
        releases={releases}
      />,
    );

    return userEvent.click(
      screen.getByRole('button', { name: /Promote to staging/i }),
    ).then(() => {
      expect(mockTriggerDeployment).toHaveBeenCalledWith('1', '42', {
        releaseVersion: 'v1.4.2',
        environmentId: 2,
      });
    });
  });
});
