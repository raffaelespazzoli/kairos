import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { ApplicationLayout } from './components/layout/ApplicationLayout';
import { TeamDashboardPage } from './routes/TeamDashboardPage';
import { ApplicationOverviewPage } from './routes/ApplicationOverviewPage';
import { ApplicationDeliveryPage } from './routes/ApplicationDeliveryPage';
import { ApplicationHealthPage } from './routes/ApplicationHealthPage';
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
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
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
            <Route path="delivery" element={<ApplicationDeliveryPage />} />
            <Route path="health" element={<ApplicationHealthPage />} />

            {/* Legacy URL redirects — removed tabs */}
            <Route path="environments" element={<Navigate to=".." replace />} />
            <Route path="builds" element={<Navigate to="../delivery" replace />} />
            <Route path="releases" element={<Navigate to="../delivery" replace />} />
            <Route path="settings" element={<Navigate to=".." replace />} />
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
