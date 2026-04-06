import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { TeamDashboardPage } from './TeamDashboardPage';
import { ApplicationsProvider } from '../contexts/ApplicationsContext';
import type { ApplicationSummary } from '../types/application';
import type { PortalError } from '../types/error';

function renderPage(
  applications: ApplicationSummary[] = [],
  isLoading = false,
  error: PortalError | null = null,
) {
  return render(
    <ApplicationsProvider value={{ applications, isLoading, error }}>
      <MemoryRouter initialEntries={['/teams/1']}>
        <Routes>
          <Route path="/teams/:teamId" element={<TeamDashboardPage />} />
        </Routes>
      </MemoryRouter>
    </ApplicationsProvider>,
  );
}

const sampleApps: ApplicationSummary[] = [
  {
    id: 1,
    name: 'alpha-api',
    runtimeType: 'quarkus',
    onboardedAt: '2026-04-01T10:00:00Z',
    onboardingPrUrl: 'https://github.com/org/infra/pull/1',
  },
  {
    id: 2,
    name: 'beta-service',
    runtimeType: 'spring-boot',
    onboardedAt: '2026-04-02T10:00:00Z',
    onboardingPrUrl: '',
  },
];

describe('TeamDashboardPage', () => {
  it('shows loading spinner while applications are loading', () => {
    renderPage([], true);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows error alert when fetch fails', () => {
    renderPage([], false, {
      error: 'unknown',
      message: 'Failed to load applications',
      timestamp: '2026-04-06T00:00:00Z',
    });
    expect(screen.getByText('Failed to load applications')).toBeInTheDocument();
  });

  it('shows empty state when no applications exist', () => {
    renderPage([]);
    expect(
      screen.getByText('No applications onboarded yet'),
    ).toBeInTheDocument();
  });

  it('shows dashboard content when applications exist', () => {
    renderPage(sampleApps);
    expect(screen.getByText('My Team Dashboard')).toBeInTheDocument();
  });

  it('shows correct application count for multiple apps', () => {
    renderPage(sampleApps);
    expect(screen.getByText('2 applications onboarded')).toBeInTheDocument();
  });

  it('uses singular form for single application', () => {
    renderPage([sampleApps[0]]);
    expect(screen.getByText('1 application onboarded')).toBeInTheDocument();
  });
});
