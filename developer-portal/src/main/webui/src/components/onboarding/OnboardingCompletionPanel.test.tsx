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
  devSpacesDeepLink: 'https://devspaces.example.com/#/https://github.com/org/payment-svc.git',
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

  it('shows functional DevSpaces link when configured', () => {
    renderPanel();
    const devSpacesLink = screen.getByRole('link', {
      name: /open in devspaces/i,
    });
    expect(devSpacesLink).toHaveAttribute(
      'href',
      'https://devspaces.example.com/#/https://github.com/org/payment-svc.git',
    );
    expect(devSpacesLink).toHaveAttribute('target', '_blank');
  });

  it('hides DevSpaces link when not configured', () => {
    renderPanel({
      ...mockResult,
      devSpacesDeepLink: null,
    });
    expect(screen.queryByText(/open in devspaces/i)).not.toBeInTheDocument();
  });
});
