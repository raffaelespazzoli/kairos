import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProvisioningPlanPreview } from './ProvisioningPlanPreview';
import type { OnboardingPlanResult } from '../../types/onboarding';
import type { Cluster } from '../../types/cluster';

function makePlan(overrides: Partial<OnboardingPlanResult> = {}): OnboardingPlanResult {
  return {
    appName: 'payment-svc',
    teamName: 'payments',
    namespaces: [
      { name: 'payments-payment-svc-build', clusterName: 'ocp-dev-01', environmentName: 'build', isBuild: true },
      { name: 'payments-payment-svc-dev', clusterName: 'ocp-dev-01', environmentName: 'dev', isBuild: false },
      { name: 'payments-payment-svc-qa', clusterName: 'ocp-qa-01', environmentName: 'qa', isBuild: false },
    ],
    argoCdApps: [
      { name: 'payment-svc-build', clusterName: 'ocp-dev-01', namespace: 'payments-payment-svc-build', chartPath: '.helm/build', valuesFile: 'values-build.yaml', isBuild: true },
      { name: 'payment-svc-run-dev', clusterName: 'ocp-dev-01', namespace: 'payments-payment-svc-dev', chartPath: '.helm/run', valuesFile: 'values-run-dev.yaml', isBuild: false },
      { name: 'payment-svc-run-qa', clusterName: 'ocp-qa-01', namespace: 'payments-payment-svc-qa', chartPath: '.helm/run', valuesFile: 'values-run-qa.yaml', isBuild: false },
    ],
    promotionChain: ['dev', 'qa'],
    generatedManifests: {},
    ...overrides,
  };
}

const mockClusters: Cluster[] = [
  { id: 1, name: 'ocp-dev-01', apiServerUrl: 'https://api.ocp-dev-01:6443', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  { id: 2, name: 'ocp-qa-01', apiServerUrl: 'https://api.ocp-qa-01:6443', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
];

describe('ProvisioningPlanPreview', () => {
  it('displays resource count', () => {
    render(
      <ProvisioningPlanPreview
        plan={makePlan()}
        clusters={mockClusters}
        onClusterChange={vi.fn()}
        onBuildClusterChange={vi.fn()}
      />,
    );

    expect(screen.getByText('3 namespaces')).toBeInTheDocument();
    expect(screen.getByText('3 ArgoCD applications')).toBeInTheDocument();
  });

  it('renders namespace list with cluster names', () => {
    render(
      <ProvisioningPlanPreview
        plan={makePlan()}
        clusters={mockClusters}
        onClusterChange={vi.fn()}
        onBuildClusterChange={vi.fn()}
      />,
    );

    expect(screen.getByText('payments-payment-svc-build')).toBeInTheDocument();
    expect(screen.getByText('payments-payment-svc-dev')).toBeInTheDocument();
    expect(screen.getByText('payments-payment-svc-qa')).toBeInTheDocument();
  });

  it('renders ArgoCD application list with chart paths', () => {
    render(
      <ProvisioningPlanPreview
        plan={makePlan()}
        clusters={mockClusters}
        onClusterChange={vi.fn()}
        onBuildClusterChange={vi.fn()}
      />,
    );

    expect(screen.getByText('ArgoCD Applications')).toBeInTheDocument();

    const listItems = screen.getAllByRole('listitem');
    const argoAppTexts = listItems.map((li) => li.textContent ?? '');
    expect(argoAppTexts.some((t) => t.includes('payment-svc-build') && t.includes('.helm/build'))).toBe(true);
    expect(argoAppTexts.some((t) => t.includes('payment-svc-run-dev') && t.includes('.helm/run'))).toBe(true);
    expect(argoAppTexts.some((t) => t.includes('payment-svc-run-qa') && t.includes('.helm/run'))).toBe(true);
  });

  it('displays promotion chain in correct order', () => {
    render(
      <ProvisioningPlanPreview
        plan={makePlan({ promotionChain: ['dev', 'qa', 'prod'] })}
        clusters={mockClusters}
        onClusterChange={vi.fn()}
        onBuildClusterChange={vi.fn()}
      />,
    );

    expect(screen.getByText('dev')).toBeInTheDocument();
    expect(screen.getByText('qa')).toBeInTheDocument();
    expect(screen.getByText('prod')).toBeInTheDocument();
  });

  it('shows app name and team name', () => {
    render(
      <ProvisioningPlanPreview
        plan={makePlan()}
        clusters={mockClusters}
        onClusterChange={vi.fn()}
        onBuildClusterChange={vi.fn()}
      />,
    );

    expect(screen.getByText('payment-svc — payments')).toBeInTheDocument();
  });

  it('triggers onClusterChange when environment cluster dropdown changes', async () => {
    const user = userEvent.setup();
    const onClusterChange = vi.fn();

    render(
      <ProvisioningPlanPreview
        plan={makePlan()}
        clusters={mockClusters}
        onClusterChange={onClusterChange}
        onBuildClusterChange={vi.fn()}
      />,
    );

    const devToggle = screen.getByRole('button', { name: /select cluster for dev/i });
    await user.click(devToggle);

    const options = screen.getAllByRole('option');
    const qaOption = options.find((o) => o.textContent?.includes('ocp-qa-01'));
    expect(qaOption).toBeTruthy();
    await user.click(qaOption!);

    expect(onClusterChange).toHaveBeenCalledWith('dev', 2);
  });

  it('triggers onBuildClusterChange when build cluster dropdown changes', async () => {
    const user = userEvent.setup();
    const onBuildClusterChange = vi.fn();

    render(
      <ProvisioningPlanPreview
        plan={makePlan()}
        clusters={mockClusters}
        onClusterChange={vi.fn()}
        onBuildClusterChange={onBuildClusterChange}
      />,
    );

    const buildToggle = screen.getByRole('button', { name: /select cluster for build/i });
    await user.click(buildToggle);

    const options = screen.getAllByRole('option');
    const qaOption = options.find((o) => o.textContent?.includes('ocp-qa-01'));
    expect(qaOption).toBeTruthy();
    await user.click(qaOption!);

    expect(onBuildClusterChange).toHaveBeenCalledWith(2);
  });
});
