import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { OnboardingWizardPage } from './OnboardingWizardPage';

vi.mock('../api/onboarding', () => ({
  validateRepo: vi.fn(),
  buildPlan: vi.fn(),
  confirmOnboarding: vi.fn(),
}));

vi.mock('../api/clusters', () => ({
  fetchClusters: vi.fn(),
}));

import { validateRepo } from '../api/onboarding';
const mockValidateRepo = vi.mocked(validateRepo);

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/teams/default/onboard']}>
      <Routes>
        <Route path="/teams/:teamId/onboard" element={<OnboardingWizardPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('OnboardingWizardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders wizard with step 1 by default', () => {
    renderPage();

    expect(screen.getByText('Onboard Application')).toBeInTheDocument();
    expect(screen.getByText('Git Repository URL')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /validate repository/i })).toBeInTheDocument();
  });

  it('disables validate button when URL is empty', () => {
    renderPage();

    expect(screen.getByRole('button', { name: /validate repository/i })).toBeDisabled();
  });

  it('calls validateRepo on submit', async () => {
    const user = userEvent.setup();
    mockValidateRepo.mockResolvedValue({
      allPassed: true,
      checks: [
        { name: 'Helm Build Chart', passed: true, detail: 'Found', fixInstruction: null },
        { name: 'Helm Run Chart', passed: true, detail: 'Found', fixInstruction: null },
        { name: 'Build Values', passed: true, detail: 'Found', fixInstruction: null },
        { name: 'Environment Values', passed: true, detail: '1 env', fixInstruction: null },
        { name: 'Runtime Detection', passed: true, detail: 'Java', fixInstruction: null },
      ],
      runtimeType: 'Quarkus/Java',
      detectedEnvironments: ['dev'],
    });

    renderPage();

    await user.type(screen.getByRole('textbox'), 'https://github.com/team/app');
    await user.click(screen.getByRole('button', { name: /validate repository/i }));

    await waitFor(() => {
      expect(mockValidateRepo).toHaveBeenCalledWith('default', 'https://github.com/team/app');
    });
  });

  it('displays error when repo is unreachable', async () => {
    const user = userEvent.setup();
    const { ApiError } = await import('../api/client');
    mockValidateRepo.mockRejectedValue(
      new ApiError(502, {
        error: 'integration-error',
        message: 'Cannot access repository — check the URL and ensure the portal has read access',
        timestamp: new Date().toISOString(),
      }),
    );

    renderPage();

    await user.type(screen.getByRole('textbox'), 'https://github.com/team/bad');
    await user.click(screen.getByRole('button', { name: /validate repository/i }));

    await waitFor(() => {
      expect(screen.getByText(/Cannot access repository/)).toBeInTheDocument();
    });
  });

  it('shows Provisioning Plan step in wizard navigation', () => {
    renderPage();

    expect(screen.getByText('Provisioning Plan')).toBeInTheDocument();
  });

  it('shows Create PR and Complete steps in wizard navigation', () => {
    renderPage();

    expect(screen.getByText('Create PR')).toBeInTheDocument();
    expect(screen.getByText('Complete')).toBeInTheDocument();
  });
});
