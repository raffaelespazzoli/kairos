import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { OnboardingCompletionPanel } from './OnboardingCompletionPanel';
import type { OnboardingResult } from '../../types/onboarding';

const mockResult: OnboardingResult = {
  applicationId: 1,
  applicationName: 'payment-svc',
  onboardingPrUrl: 'https://github.com/org/infra/pull/42',
  namespacesCreated: 4,
  argoCdAppsCreated: 4,
  promotionChain: ['dev', 'qa', 'prod'],
};

function renderPanel(result = mockResult, teamId = '1') {
  return render(
    <MemoryRouter>
      <OnboardingCompletionPanel result={result} teamId={teamId} />
    </MemoryRouter>,
  );
}

describe('OnboardingCompletionPanel', () => {
  it('displays success message', () => {
    renderPanel();
    expect(screen.getByText(/onboarded successfully/i)).toBeInTheDocument();
  });

  it('shows PR link that opens in new tab', () => {
    renderPanel();
    const prLink = screen.getByRole('link', { name: /view onboarding pr/i });
    expect(prLink).toHaveAttribute('href', 'https://github.com/org/infra/pull/42');
    expect(prLink).toHaveAttribute('target', '_blank');
  });

  it('shows View app button with correct app name', () => {
    renderPanel();
    expect(
      screen.getByRole('button', { name: /view payment-svc/i }),
    ).toBeInTheDocument();
  });

  it('shows resource summary with correct counts', () => {
    renderPanel();
    expect(screen.getByText(/4 namespaces/)).toBeInTheDocument();
    expect(screen.getByText(/4 ArgoCD/)).toBeInTheDocument();
  });

  it('shows promotion chain', () => {
    renderPanel();
    expect(screen.getByText('dev')).toBeInTheDocument();
    expect(screen.getByText('qa')).toBeInTheDocument();
    expect(screen.getByText('prod')).toBeInTheDocument();
  });

  it('shows disabled DevSpaces button', () => {
    renderPanel();
    const devSpacesBtn = screen.getByRole('button', {
      name: /open in devspaces/i,
    });
    expect(devSpacesBtn).toBeDisabled();
  });
});
