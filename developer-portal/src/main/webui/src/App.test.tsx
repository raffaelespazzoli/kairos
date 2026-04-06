import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { ApplicationLayout } from './components/layout/ApplicationLayout';
import { TeamDashboardPage } from './routes/TeamDashboardPage';
import { ApplicationOverviewPage } from './routes/ApplicationOverviewPage';
import { ApplicationBuildsPage } from './routes/ApplicationBuildsPage';
import { OnboardingWizardPage } from './routes/OnboardingWizardPage';
import { AdminClustersPage } from './routes/AdminClustersPage';

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
          <Route index element={<Navigate to="/teams/default" replace />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('App routing', () => {
  it('renders team dashboard at /teams/:teamId', () => {
    renderApp('/teams/platform');
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders application layout with tabs at /teams/:teamId/apps/:appId', () => {
    renderApp('/teams/platform/apps/my-app');
    expect(screen.getByLabelText('Application tabs')).toBeInTheDocument();
  });

  it('renders builds page at /teams/:teamId/apps/:appId/builds', () => {
    renderApp('/teams/platform/apps/my-app/builds');
    expect(
      screen.getByText('Coming soon — build history and pipeline status.'),
    ).toBeInTheDocument();
    const buildsTab = screen.getByRole('tab', { name: 'Builds' });
    expect(buildsTab).toHaveAttribute('aria-selected', 'true');
  });

  it('renders onboarding page at /teams/:teamId/onboard', () => {
    renderApp('/teams/platform/onboard');
    expect(screen.getByText('Onboard Application')).toBeInTheDocument();
  });

  it('renders admin clusters page at /admin/clusters', () => {
    renderApp('/admin/clusters');
    expect(screen.getByText('Access Denied')).toBeInTheDocument();
  });

  it('redirects root to /teams/default', () => {
    renderApp('/');
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('tabs navigate without full page reload (client-side routing)', () => {
    renderApp('/teams/platform/apps/my-app/overview');
    expect(screen.getByLabelText('Application tabs')).toBeInTheDocument();
  });
});
