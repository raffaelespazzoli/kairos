import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { ApplicationLayout } from './components/layout/ApplicationLayout';
import { TeamDashboardPage } from './routes/TeamDashboardPage';
import { ApplicationOverviewPage } from './routes/ApplicationOverviewPage';
import { ApplicationBuildsPage } from './routes/ApplicationBuildsPage';
import { ApplicationReleasesPage } from './routes/ApplicationReleasesPage';
import { ApplicationEnvironmentsPage } from './routes/ApplicationEnvironmentsPage';
import { ApplicationHealthPage } from './routes/ApplicationHealthPage';
import { ApplicationSettingsPage } from './routes/ApplicationSettingsPage';
import { OnboardingWizardPage } from './routes/OnboardingWizardPage';
import { AdminClustersPage } from './routes/AdminClustersPage';
import { setTokenAccessor } from './api/client';
import { useAuth } from './hooks/useAuth';

function App() {
  const { token } = useAuth();

  useEffect(() => {
    setTokenAccessor(() => token);
  }, [token]);

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/teams/:teamId" element={<TeamDashboardPage />} />
          <Route
            path="/teams/:teamId/dashboard"
            element={<TeamDashboardPage />}
          />

          <Route
            path="/teams/:teamId/apps/:appId"
            element={<ApplicationLayout />}
          >
            <Route index element={<ApplicationOverviewPage />} />
            <Route path="overview" element={<ApplicationOverviewPage />} />
            <Route path="builds" element={<ApplicationBuildsPage />} />
            <Route path="releases" element={<ApplicationReleasesPage />} />
            <Route
              path="environments"
              element={<ApplicationEnvironmentsPage />}
            />
            <Route path="health" element={<ApplicationHealthPage />} />
            <Route path="settings" element={<ApplicationSettingsPage />} />
          </Route>

          <Route
            path="/teams/:teamId/onboard"
            element={<OnboardingWizardPage />}
          />

          <Route path="/admin/clusters" element={<AdminClustersPage />} />

          <Route index element={<Navigate to="/teams/default" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
