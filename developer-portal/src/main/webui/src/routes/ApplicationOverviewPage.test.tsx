import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ApplicationOverviewPage } from './ApplicationOverviewPage';
import { ApplicationLayout } from '../components/layout/ApplicationLayout';
import { ApplicationsProvider } from '../contexts/ApplicationsContext';
import type { ApplicationSummary } from '../types/application';
import type { PortalError } from '../types/error';

const sampleApps: ApplicationSummary[] = [
  {
    id: 42,
    name: 'payments-api',
    runtimeType: 'quarkus',
    onboardedAt: '2026-04-01T10:00:00Z',
    onboardingPrUrl: 'https://github.com/org/infra/pull/123',
  },
  {
    id: 99,
    name: 'no-pr-app',
    runtimeType: 'spring-boot',
    onboardedAt: '',
    onboardingPrUrl: '',
  },
];

function renderPage(
  route: string,
  applications: ApplicationSummary[] = sampleApps,
  isLoading = false,
  error: PortalError | null = null,
) {
  return render(
    <ApplicationsProvider value={{ applications, isLoading, error }}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/teams/:teamId/apps/:appId" element={<ApplicationLayout />}>
            <Route index element={<ApplicationOverviewPage />} />
            <Route path="overview" element={<ApplicationOverviewPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ApplicationsProvider>,
  );
}

describe('ApplicationOverviewPage', () => {
  it('shows loading spinner while applications are loading', () => {
    renderPage('/teams/1/apps/42', [], true);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows error alert when fetch fails', () => {
    renderPage('/teams/1/apps/42', [], false, {
      error: 'unknown',
      message: 'Failed to load applications',
      timestamp: '2026-04-06T00:00:00Z',
    });
    expect(screen.getByText('Failed to load applications')).toBeInTheDocument();
  });

  it('shows error when application is not found', () => {
    renderPage('/teams/1/apps/9999');
    expect(screen.getByText('Application not found')).toBeInTheDocument();
  });

  it('renders application name as heading', () => {
    renderPage('/teams/1/apps/42');
    expect(
      screen.getByRole('heading', { name: 'payments-api' }),
    ).toBeInTheDocument();
  });

  it('displays runtime type', () => {
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Runtime')).toBeInTheDocument();
    expect(screen.getByText('quarkus')).toBeInTheDocument();
  });

  it('displays onboarded date when present', () => {
    renderPage('/teams/1/apps/42');
    expect(screen.getByText('Onboarded')).toBeInTheDocument();
  });

  it('displays onboarding PR link when present', () => {
    renderPage('/teams/1/apps/42');
    const prLink = screen.getByText('View onboarding PR');
    expect(prLink.closest('a')).toHaveAttribute(
      'href',
      'https://github.com/org/infra/pull/123',
    );
    expect(prLink.closest('a')).toHaveAttribute('target', '_blank');
  });

  it('hides onboarding PR section when URL is empty', () => {
    renderPage('/teams/1/apps/99');
    expect(screen.queryByText('View onboarding PR')).not.toBeInTheDocument();
  });

  it('shows environment chain coming soon message', () => {
    renderPage('/teams/1/apps/42');
    expect(
      screen.getByText('Environment chain visualization coming in Story 2.8.'),
    ).toBeInTheDocument();
  });
});
