import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { EnvironmentCard } from './EnvironmentCard';
import type { EnvironmentChainEntry } from '../../types/environment';

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
    ...overrides,
  };
}

describe('EnvironmentCard', () => {
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
});
