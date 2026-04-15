import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ApplicationDeliveryPage } from './ApplicationDeliveryPage';
import { ApplicationLayout } from '../components/layout/ApplicationLayout';
import { ApplicationsProvider } from '../contexts/ApplicationsContext';
import type { ApplicationSummary } from '../types/application';
import type { BuildSummary, BuildDetail } from '../types/build';
import type { ReleaseSummary } from '../types/release';
import type { AppActivityResponse, TeamActivityEventDto } from '../types/dashboard';
import type { PortalError } from '../types/error';

const sampleApps: ApplicationSummary[] = [
  {
    id: 42,
    name: 'payments-api',
    runtimeType: 'quarkus',
    onboardedAt: '2026-04-01T10:00:00Z',
    onboardingPrUrl: 'https://github.com/org/infra/pull/123',
    gitRepoUrl: 'https://github.com/org/payments-api.git',
    devSpacesDeepLink: 'https://devspaces.example.com/#/https://github.com/org/payments-api.git',
  },
];

const sampleBuilds: BuildSummary[] = [
  {
    buildId: 'test-build-001',
    status: 'Passed',
    startedAt: '2026-04-08T09:00:00Z',
    completedAt: '2026-04-08T09:05:00Z',
    duration: '5m 0s',
    imageReference: 'registry.example.com/payments-api:abc123',
    applicationName: 'payments-api',
    tektonDeepLink: 'https://tekton.example.com/runs/test-build-001',
  },
];

const sampleReleases: ReleaseSummary[] = [
  {
    version: 'v9.0.0-test',
    createdAt: '2026-04-07T10:00:00Z',
    buildId: null,
    commitSha: 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
    imageReference: 'registry.example.com/team/app:v9.0.0-test',
  },
];

const sampleActivityEvents: TeamActivityEventDto[] = [
  {
    eventType: 'deployment',
    applicationId: 42,
    applicationName: 'payments-api',
    reference: 'test-activity-deploy',
    timestamp: '2026-04-08T11:00:00Z',
    status: 'Deployed',
    actor: 'ci-bot',
    environmentName: 'staging',
  },
];

const sampleActivity: AppActivityResponse = {
  events: sampleActivityEvents,
  error: null,
};

let mockBuildsResult: {
  data: BuildSummary[] | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
  prepend: ReturnType<typeof vi.fn>;
};

let mockTriggerResult: {
  trigger: ReturnType<typeof vi.fn>;
  error: PortalError | null;
  isTriggering: boolean;
};

let mockDetailResult: {
  data: BuildDetail | null;
  error: PortalError | null;
  isLoading: boolean;
  load: ReturnType<typeof vi.fn>;
};

let mockLogsResult: {
  logs: string | null;
  error: PortalError | null;
  isLoading: boolean;
  load: ReturnType<typeof vi.fn>;
};

let mockReleasesResult: {
  data: ReleaseSummary[] | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

let mockActivityResult: {
  data: AppActivityResponse | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

vi.mock('../hooks/useBuilds', () => ({
  useBuilds: () => mockBuildsResult,
  useTriggerBuild: () => mockTriggerResult,
  useBuildDetail: () => mockDetailResult,
  useBuildLogs: () => mockLogsResult,
}));

vi.mock('../hooks/useReleases', () => ({
  useReleases: () => mockReleasesResult,
}));

vi.mock('../hooks/useDashboard', () => ({
  useAppActivity: () => mockActivityResult,
}));

vi.mock('../api/releases', () => ({
  createRelease: vi.fn(),
}));

vi.mock('../api/builds', () => ({
  fetchBuilds: vi.fn(),
  triggerBuild: vi.fn(),
  fetchBuildDetail: vi.fn(),
  fetchBuildLogs: vi.fn(),
}));

function renderPage(
  route = '/teams/1/apps/42/delivery',
  applications: ApplicationSummary[] = sampleApps,
) {
  return render(
    <ApplicationsProvider value={{ applications, isLoading: false, error: null }}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/teams/:teamId/apps/:appId" element={<ApplicationLayout />}>
            <Route path="delivery" element={<ApplicationDeliveryPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ApplicationsProvider>,
  );
}

beforeEach(() => {
  mockBuildsResult = {
    data: sampleBuilds,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
    prepend: vi.fn(),
  };
  mockTriggerResult = {
    trigger: vi.fn().mockResolvedValue(null),
    error: null,
    isTriggering: false,
  };
  mockDetailResult = {
    data: null,
    error: null,
    isLoading: false,
    load: vi.fn(),
  };
  mockLogsResult = {
    logs: null,
    error: null,
    isLoading: false,
    load: vi.fn(),
  };
  mockReleasesResult = {
    data: sampleReleases,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  };
  mockActivityResult = {
    data: sampleActivity,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  };
});

describe('ApplicationDeliveryPage', () => {
  describe('3-column layout', () => {
    it('renders grid with builds, releases, and activity data', () => {
      renderPage();
      expect(screen.getByText('test-build-001')).toBeInTheDocument();
      expect(screen.getByText('v9.0.0-test')).toBeInTheDocument();
      expect(screen.getByText('test-activity-deploy')).toBeInTheDocument();
    });

    it('renders all three Card sections', () => {
      renderPage();
      expect(screen.getByLabelText('Builds')).toBeInTheDocument();
      expect(screen.getByLabelText('Releases')).toBeInTheDocument();
      expect(screen.getByLabelText('Recent Activity')).toBeInTheDocument();
    });
  });

  describe('builds loading state', () => {
    it('shows loading spinner when buildsLoading', () => {
      mockBuildsResult = { data: null, error: null, isLoading: true, refresh: vi.fn(), prepend: vi.fn() };
      renderPage();
      const buildsCard = screen.getByLabelText('Builds');
      expect(within(buildsCard).getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('releases loading state', () => {
    it('shows loading spinner when releasesLoading', () => {
      mockReleasesResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
      renderPage();
      const releasesCard = screen.getByLabelText('Releases');
      expect(within(releasesCard).getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('activity loading state', () => {
    it('shows loading spinner when activityLoading', () => {
      mockActivityResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
      renderPage();
      const activityCard = screen.getByLabelText('Recent Activity');
      expect(within(activityCard).getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('builds error state', () => {
    it('shows error alert when buildsError while sibling columns still render', () => {
      mockBuildsResult = {
        data: null,
        error: { error: 'integration-error', message: 'Tekton unavailable', timestamp: '2026-04-08T10:00:00Z' },
        isLoading: false,
        refresh: vi.fn(),
        prepend: vi.fn(),
      };
      renderPage();
      expect(screen.getByText('Tekton unavailable')).toBeInTheDocument();
      expect(screen.getByText('v9.0.0-test')).toBeInTheDocument();
      expect(screen.getByText('test-activity-deploy')).toBeInTheDocument();
    });
  });

  describe('releases error state', () => {
    it('shows error alert when releasesError while sibling columns still render', () => {
      mockReleasesResult = {
        data: null,
        error: { error: 'integration-error', message: 'Git provider unreachable', timestamp: '2026-04-08T10:00:00Z' },
        isLoading: false,
        refresh: vi.fn(),
      };
      renderPage();
      expect(screen.getByText('Git provider unreachable')).toBeInTheDocument();
      expect(screen.getByText('test-build-001')).toBeInTheDocument();
      expect(screen.getByText('test-activity-deploy')).toBeInTheDocument();
    });
  });

  describe('activity error state', () => {
    it('shows warning alert when activityError while sibling columns still render', () => {
      mockActivityResult = {
        data: null,
        error: { error: 'timeout', message: 'Activity timeout', timestamp: '2026-04-08T10:00:00Z' },
        isLoading: false,
        refresh: vi.fn(),
      };
      renderPage();
      expect(screen.getByText('Unable to load activity')).toBeInTheDocument();
      expect(screen.getByText('test-build-001')).toBeInTheDocument();
      expect(screen.getByText('v9.0.0-test')).toBeInTheDocument();
    });
  });

  describe('empty states', () => {
    it('shows "No builds yet" when builds data is empty array', () => {
      mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
      renderPage();
      expect(screen.getByText('No builds yet')).toBeInTheDocument();
    });

    it('shows "No releases yet" when releases data is empty array', () => {
      mockReleasesResult = { data: [], error: null, isLoading: false, refresh: vi.fn() };
      renderPage();
      expect(screen.getByText('No releases yet')).toBeInTheDocument();
    });

    it('shows "No recent activity" when activity data is empty', () => {
      mockActivityResult = { data: { events: [], error: null }, error: null, isLoading: false, refresh: vi.fn() };
      renderPage();
      expect(screen.getByText('No recent activity')).toBeInTheDocument();
    });

    it('shows "No recent activity" when activity data is null', () => {
      mockActivityResult = { data: null, error: null, isLoading: false, refresh: vi.fn() };
      renderPage();
      expect(screen.getByText('No recent activity')).toBeInTheDocument();
    });
  });

  describe('refresh behavior', () => {
    it('RefreshButton triggers all three refresh functions', async () => {
      const user = userEvent.setup();
      const refreshBuilds = vi.fn();
      const refreshReleases = vi.fn();
      const refreshActivity = vi.fn();
      mockBuildsResult = { ...mockBuildsResult, refresh: refreshBuilds };
      mockReleasesResult = { ...mockReleasesResult, refresh: refreshReleases };
      mockActivityResult = { ...mockActivityResult, refresh: refreshActivity };

      renderPage();
      await user.click(screen.getByRole('button', { name: /Refresh/i }));

      expect(refreshBuilds).toHaveBeenCalledTimes(1);
      expect(refreshReleases).toHaveBeenCalledTimes(1);
      expect(refreshActivity).toHaveBeenCalledTimes(1);
    });
  });

  describe('trigger build', () => {
    it('renders Trigger Build button in Builds Card header', () => {
      renderPage();
      const buildsCard = screen.getByLabelText('Builds');
      expect(within(buildsCard).getByRole('button', { name: /Trigger Build/i })).toBeInTheDocument();
    });

    it('calls trigger and prepends new build on click', async () => {
      const user = userEvent.setup();
      const prependFn = vi.fn();
      const newBuild: BuildSummary = {
        buildId: 'build-new',
        status: 'Building',
        startedAt: '2026-04-08T11:00:00Z',
        completedAt: null,
        duration: null,
        imageReference: null,
        applicationName: 'payments-api',
        tektonDeepLink: null,
      };
      const triggerFn = vi.fn().mockResolvedValue(newBuild);
      mockBuildsResult = { ...mockBuildsResult, prepend: prependFn };
      mockTriggerResult = { trigger: triggerFn, error: null, isTriggering: false };

      renderPage();
      const buildsCard = screen.getByLabelText('Builds');
      await user.click(within(buildsCard).getByRole('button', { name: /Trigger Build/i }));

      expect(triggerFn).toHaveBeenCalledTimes(1);
      expect(prependFn).toHaveBeenCalledWith(newBuild);
    });

    it('shows danger Alert in Builds column on trigger error', () => {
      mockTriggerResult = {
        trigger: vi.fn().mockResolvedValue(null),
        error: { error: 'trigger-error', message: 'Pipeline trigger failed', timestamp: '2026-04-08T10:00:00Z' },
        isTriggering: false,
      };
      renderPage();
      const buildsCard = screen.getByLabelText('Builds');
      expect(within(buildsCard).getByText('Pipeline trigger failed')).toBeInTheDocument();
    });
  });

  describe('cross-column release refresh', () => {
    it('calls refreshReleases after successful release creation via BuildTable', async () => {
      const user = userEvent.setup();
      const refreshReleases = vi.fn();
      mockReleasesResult = { ...mockReleasesResult, refresh: refreshReleases };

      const { createRelease } = await import('../api/releases');
      const { fetchBuildDetail } = await import('../api/builds');
      (fetchBuildDetail as ReturnType<typeof vi.fn>).mockResolvedValue({
        buildId: 'test-build-001',
        status: 'Passed',
        startedAt: '2026-04-08T09:00:00Z',
        completedAt: '2026-04-08T09:05:00Z',
        duration: '5m 0s',
        applicationName: 'payments-api',
        imageReference: 'registry.example.com/payments-api:abc123',
        commitSha: 'abc1234def567890',
        failedStageName: null,
        errorSummary: null,
        currentStage: null,
        tektonDeepLink: null,
      });
      (createRelease as ReturnType<typeof vi.fn>).mockResolvedValue({
        version: 'v2.0.0',
        createdAt: '2026-04-08T12:00:00Z',
        buildId: 'test-build-001',
        commitSha: 'abc1234def567890',
        imageReference: 'registry.example.com/payments-api:abc123',
      });

      renderPage();

      await user.click(screen.getByRole('button', { name: /Create Release/i }));
      const versionInput = await screen.findByRole('textbox', { name: /Version tag/i });
      await user.type(versionInput, 'v2.0.0');
      await user.click(screen.getByRole('button', { name: 'Create' }));

      await screen.findByText('Released v2.0.0');
      expect(refreshReleases).toHaveBeenCalledTimes(1);
    });
  });

  describe('Delivery tab selection', () => {
    it('Delivery tab is shown as selected', () => {
      renderPage();
      const deliveryTab = screen.getByRole('tab', { name: 'Delivery' });
      expect(deliveryTab).toHaveAttribute('aria-selected', 'true');
    });
  });
});
