import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProvisioningProgressTracker } from './ProvisioningProgressTracker';
import type { ProvisioningStep } from '../../types/onboarding';

const baseSteps: ProvisioningStep[] = [
  { id: 'branch', label: 'Creating branch in infra repo', status: 'pending' },
  { id: 'commit', label: 'Committing manifests', status: 'pending' },
  { id: 'pr', label: 'Creating pull request', status: 'pending' },
];

describe('ProvisioningProgressTracker', () => {
  it('shows all steps pending with 0/3 counter', () => {
    render(
      <ProvisioningProgressTracker steps={baseSteps} totalSteps={3} onRetry={vi.fn()} />,
    );
    expect(screen.getByText(/0\/3 complete/)).toBeInTheDocument();
  });

  it('shows 1/3 when first step completes', () => {
    const steps: ProvisioningStep[] = [
      { ...baseSteps[0], status: 'completed' },
      { ...baseSteps[1], status: 'in-progress' },
      baseSteps[2],
    ];
    render(
      <ProvisioningProgressTracker steps={steps} totalSteps={3} onRetry={vi.fn()} />,
    );
    expect(screen.getByText(/1\/3 complete/)).toBeInTheDocument();
  });

  it('shows 3/3 when all steps complete', () => {
    const steps: ProvisioningStep[] = baseSteps.map((s) => ({
      ...s,
      status: 'completed' as const,
    }));
    render(
      <ProvisioningProgressTracker steps={steps} totalSteps={3} onRetry={vi.fn()} />,
    );
    expect(screen.getByText(/3\/3 complete/)).toBeInTheDocument();
  });

  it('shows retry button when a step fails', () => {
    const failedSteps: ProvisioningStep[] = [
      { ...baseSteps[0], status: 'completed' },
      { ...baseSteps[1], status: 'failed', error: 'Git server error' },
      baseSteps[2],
    ];
    render(
      <ProvisioningProgressTracker
        steps={failedSteps}
        totalSteps={3}
        onRetry={vi.fn()}
      />,
    );
    expect(screen.getByText('Git server error')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('does not show retry button when no step has failed', () => {
    render(
      <ProvisioningProgressTracker steps={baseSteps} totalSteps={3} onRetry={vi.fn()} />,
    );
    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
  });

  it('calls onRetry when retry button is clicked', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const failedSteps: ProvisioningStep[] = [
      { ...baseSteps[0], status: 'completed' },
      { ...baseSteps[1], status: 'failed', error: 'Error' },
      baseSteps[2],
    ];
    render(
      <ProvisioningProgressTracker
        steps={failedSteps}
        totalSteps={3}
        onRetry={onRetry}
      />,
    );
    await user.click(screen.getByRole('button', { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('has aria-live region', () => {
    const { container } = render(
      <ProvisioningProgressTracker steps={baseSteps} totalSteps={3} onRetry={vi.fn()} />,
    );
    expect(container.querySelector('[aria-live="polite"]')).toBeInTheDocument();
  });

  it('renders step labels', () => {
    render(
      <ProvisioningProgressTracker steps={baseSteps} totalSteps={3} onRetry={vi.fn()} />,
    );
    expect(screen.getByText('Creating branch in infra repo')).toBeInTheDocument();
    expect(screen.getByText('Committing manifests')).toBeInTheDocument();
    expect(screen.getByText('Creating pull request')).toBeInTheDocument();
  });
});
