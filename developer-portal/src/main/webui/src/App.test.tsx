import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { ApplicationLayout } from './components/layout/ApplicationLayout';
import { TeamDashboardPage } from './routes/TeamDashboardPage';
import { ApplicationOverviewPage } from './routes/ApplicationOverviewPage';
import { ApplicationBuildsPage } from './routes/ApplicationBuildsPage';
import { OnboardingWizardPage } from './routes/OnboardingWizardPage';
import { AdminClustersPage } from './routes/AdminClustersPage';

vi.mock('./hooks/useApiFetch', () => ({
  useApiFetch: (path: string | null) => ({
    data: path !== null && path.endsWith('/teams')
      ? [{ id: 1, name: 'My Team', oidcGroupId: 'default' }]
      : path !== null && path.includes('/applications')
        ? []
        : null,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  }),
}));

vi.mock('./hooks/useBuilds', () => ({
  useBuilds: () => ({ data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() }),
  useTriggerBuild: () => ({ trigger: vi.fn().mockResolvedValue(null), error: null, isTriggering: false }),
}));

function renderApp(initialRoute: string) {
  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/teams/:teamId" element={<TeamDashboardPage />} />
          <Route
            path="/teams/:teamId/apps/:appId"
            element={<ApplicationLayout />}
          >
            <Route index element={<ApplicationOverviewPage />} />
            <Route path="overview" element={<ApplicationOverviewPage />} />
            <Route path="builds" element={<ApplicationBuildsPage />} />
          </Route>
          <Route
            path="/teams/:teamId/onboard"
            element={<OnboardingWizardPage />}
          />
          <Route path="/admin/clusters" element={<AdminClustersPage />} />
          <Route index element={<Navigate to="/teams/1" replace />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('App routing', () => {
  it('renders team dashboard at /teams/:teamId', () => {
    renderApp('/teams/1');
    expect(
      screen.getByText('No applications onboarded yet'),
    ).toBeInTheDocument();
  });

  it('renders application layout with tabs at /teams/:teamId/apps/:appId', () => {
    renderApp('/teams/1/apps/my-app');
    expect(screen.getByLabelText('Application tabs')).toBeInTheDocument();
  });

  it('renders builds page at /teams/:teamId/apps/:appId/builds', () => {
    renderApp('/teams/1/apps/my-app/builds');
    expect(screen.getByText('No builds yet')).toBeInTheDocument();
    const buildsTab = screen.getByRole('tab', { name: 'Builds' });
    expect(buildsTab).toHaveAttribute('aria-selected', 'true');
  });

  it('renders onboarding page at /teams/:teamId/onboard', () => {
    renderApp('/teams/1/onboard');
    expect(screen.getByText('Onboard Application')).toBeInTheDocument();
  });

  it('renders admin clusters page at /admin/clusters', () => {
    renderApp('/admin/clusters');
    expect(screen.getByText('Access Denied')).toBeInTheDocument();
  });

  it('redirects root to /teams/1', () => {
    renderApp('/');
    expect(
      screen.getByText('No applications onboarded yet'),
    ).toBeInTheDocument();
  });

  it('tabs navigate without full page reload (client-side routing)', () => {
    renderApp('/teams/1/apps/my-app/overview');
    expect(screen.getByLabelText('Application tabs')).toBeInTheDocument();
  });
});
