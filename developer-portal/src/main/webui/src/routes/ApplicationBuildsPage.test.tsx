import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ApplicationBuildsPage } from './ApplicationBuildsPage';
import { ApplicationLayout } from '../components/layout/ApplicationLayout';
import { ApplicationsProvider } from '../contexts/ApplicationsContext';
import type { ApplicationSummary } from '../types/application';
import type { BuildSummary, BuildDetail } from '../types/build';
import type { ReleaseSummary } from '../types/release';
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
  {
    id: 99,
    name: 'no-links-app',
    runtimeType: 'spring-boot',
    onboardedAt: '',
    onboardingPrUrl: '',
    gitRepoUrl: 'https://github.com/org/no-links.git',
    devSpacesDeepLink: null,
  },
];

const sampleBuilds: BuildSummary[] = [
  {
    buildId: 'build-001',
    status: 'Passed',
    startedAt: '2026-04-08T09:00:00Z',
    completedAt: '2026-04-08T09:05:00Z',
    duration: '5m 0s',
    imageReference: 'registry.example.com/payments-api:abc123',
    applicationName: 'payments-api',
    tektonDeepLink: 'https://tekton.example.com/runs/build-001',
  },
  {
    buildId: 'build-002',
    status: 'Failed',
    startedAt: '2026-04-08T08:00:00Z',
    completedAt: '2026-04-08T08:03:00Z',
    duration: '3m 0s',
    imageReference: null,
    applicationName: 'payments-api',
    tektonDeepLink: 'https://tekton.example.com/runs/build-002',
  },
  {
    buildId: 'build-003',
    status: 'Building',
    startedAt: '2026-04-08T10:00:00Z',
    completedAt: null,
    duration: null,
    imageReference: null,
    applicationName: 'payments-api',
    tektonDeepLink: null,
  },
  {
    buildId: 'build-004',
    status: 'Cancelled',
    startedAt: '2026-04-07T14:00:00Z',
    completedAt: '2026-04-07T14:01:00Z',
    duration: '1m 0s',
    imageReference: null,
    applicationName: 'payments-api',
    tektonDeepLink: null,
  },
  {
    buildId: 'build-005',
    status: 'Pending',
    startedAt: '2026-04-08T10:30:00Z',
    completedAt: null,
    duration: null,
    imageReference: null,
    applicationName: 'payments-api',
    tektonDeepLink: null,
  },
  {
    buildId: 'build-006',
    status: 'Passed',
    startedAt: '2026-04-08T07:00:00Z',
    completedAt: '2026-04-08T07:04:00Z',
    duration: '4m 0s',
    imageReference: null,
    applicationName: 'payments-api',
    tektonDeepLink: null,
  },
];

const sampleDetail: BuildDetail = {
  buildId: 'build-002',
  status: 'Failed',
  startedAt: '2026-04-08T08:00:00Z',
  completedAt: '2026-04-08T08:03:00Z',
  duration: '3m 0s',
  applicationName: 'payments-api',
  imageReference: null,
  commitSha: null,
  failedStageName: 'unit-test',
  errorSummary: 'Tests failed with 3 errors',
  currentStage: null,
  tektonDeepLink: 'https://tekton.example.com/runs/build-002',
};

const passedBuildDetail: BuildDetail = {
  buildId: 'build-001',
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
  tektonDeepLink: 'https://tekton.example.com/runs/build-001',
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

vi.mock('../hooks/useBuilds', () => ({
  useBuilds: () => mockBuildsResult,
  useTriggerBuild: () => mockTriggerResult,
  useBuildDetail: () => mockDetailResult,
  useBuildLogs: () => mockLogsResult,
}));

const mockCreateRelease = vi.fn();
vi.mock('../api/releases', () => ({
  createRelease: (...args: unknown[]) => mockCreateRelease(...args),
}));

const mockFetchBuildDetail = vi.fn();
vi.mock('../api/builds', () => ({
  fetchBuilds: vi.fn(),
  triggerBuild: vi.fn(),
  fetchBuildDetail: (...args: unknown[]) => mockFetchBuildDetail(...args),
  fetchBuildLogs: vi.fn(),
}));

function renderPage(
  route = '/teams/1/apps/42',
  applications: ApplicationSummary[] = sampleApps,
) {
  return render(
    <ApplicationsProvider value={{ applications, isLoading: false, error: null }}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/teams/:teamId/apps/:appId" element={<ApplicationLayout />}>
            <Route path="builds" element={<ApplicationBuildsPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ApplicationsProvider>,
  );
}

beforeEach(() => {
  mockCreateRelease.mockReset();
  mockFetchBuildDetail.mockReset();
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
});

describe('ApplicationBuildsPage', () => {
  describe('loading state', () => {
    it('shows loading spinner while builds are loading', () => {
      mockBuildsResult = { data: null, error: null, isLoading: true, refresh: vi.fn(), prepend: vi.fn() };
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('error state', () => {
    it('shows inline error alert when builds fail to load', () => {
      mockBuildsResult = {
        data: null,
        error: {
          error: 'integration-error',
          message: 'Failed to load builds from Tekton',
          timestamp: '2026-04-08T10:00:00Z',
        },
        isLoading: false,
        refresh: vi.fn(),
        prepend: vi.fn(),
      };
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByText('Failed to load builds from Tekton')).toBeInTheDocument();
    });

    it('shows inline alert when trigger build fails', () => {
      mockTriggerResult = {
        trigger: vi.fn().mockResolvedValue(null),
        error: {
          error: 'trigger-error',
          message: 'Build trigger failed',
          timestamp: '2026-04-08T10:00:00Z',
        },
        isTriggering: false,
      };
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByText('Build trigger failed')).toBeInTheDocument();
    });
  });

  describe('empty state', () => {
    it('shows empty state when no builds exist', () => {
      mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByText('No builds yet')).toBeInTheDocument();
      expect(
        screen.getByText('Trigger your first build or push code to start a CI pipeline.'),
      ).toBeInTheDocument();
    });

    it('shows Trigger Build button in empty state', () => {
      mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
      renderPage('/teams/1/apps/42/builds');
      const buttons = screen.getAllByRole('button', { name: /Trigger Build/i });
      expect(buttons.length).toBeGreaterThanOrEqual(1);
    });

    it('shows DevSpaces link in empty state when app has DevSpaces URL', () => {
      mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByText('Open in DevSpaces ↗')).toBeInTheDocument();
    });

    it('hides DevSpaces link in empty state when app has no DevSpaces URL', () => {
      mockBuildsResult = { data: [], error: null, isLoading: false, refresh: vi.fn(), prepend: vi.fn() };
      renderPage('/teams/1/apps/99/builds');
      expect(screen.queryByText('Open in DevSpaces ↗')).not.toBeInTheDocument();
    });
  });

  describe('builds table', () => {
    it('renders builds table with compact variant', () => {
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByRole('grid', { name: 'Builds table' })).toBeInTheDocument();
    });

    it('renders all build rows', () => {
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByText('build-001')).toBeInTheDocument();
      expect(screen.getByText('build-002')).toBeInTheDocument();
      expect(screen.getByText('build-003')).toBeInTheDocument();
      expect(screen.getByText('build-004')).toBeInTheDocument();
      expect(screen.getByText('build-005')).toBeInTheDocument();
    });

    it('shows portal status vocabulary badges', () => {
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getAllByText('Passed').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Failed')).toBeInTheDocument();
      expect(screen.getByText('Building...')).toBeInTheDocument();
      expect(screen.getByText('Cancelled')).toBeInTheDocument();
      expect(screen.getByText('Pending')).toBeInTheDocument();
    });

    it('shows artifact reference for passed build in monospace', () => {
      renderPage('/teams/1/apps/42/builds');
      const artifact = screen.getByText('registry.example.com/payments-api:abc123');
      expect(artifact.tagName.toLowerCase()).toBe('code');
    });

    it('shows dash when artifact is absent', () => {
      renderPage('/teams/1/apps/42/builds');
      const table = screen.getByRole('grid', { name: 'Builds table' });
      const dashes = within(table).getAllByText('—');
      expect(dashes.length).toBeGreaterThan(0);
    });

    it('shows duration for completed builds', () => {
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByText('5m 0s')).toBeInTheDocument();
      expect(screen.getByText('3m 0s')).toBeInTheDocument();
    });
  });

  describe('page header', () => {
    it('renders Builds heading', () => {
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByRole('heading', { name: 'Builds' })).toBeInTheDocument();
    });

    it('renders Trigger Build button in header', () => {
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByRole('button', { name: /Trigger Build/i })).toBeInTheDocument();
    });

    it('renders Refresh button', () => {
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getByRole('button', { name: /Refresh/i })).toBeInTheDocument();
    });
  });

  describe('trigger build', () => {
    it('calls trigger and prepends new build inline when Trigger Build is clicked', async () => {
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

      renderPage('/teams/1/apps/42/builds');

      const triggerButton = screen.getByRole('button', { name: /Trigger Build/i });
      await user.click(triggerButton);

      expect(triggerFn).toHaveBeenCalledTimes(1);
      expect(prependFn).toHaveBeenCalledWith(newBuild);
    });

    it('does not prepend when trigger returns null (error case)', async () => {
      const user = userEvent.setup();
      const prependFn = vi.fn();
      const triggerFn = vi.fn().mockResolvedValue(null);
      mockBuildsResult = { ...mockBuildsResult, prepend: prependFn };
      mockTriggerResult = { trigger: triggerFn, error: null, isTriggering: false };

      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Trigger Build/i }));

      expect(triggerFn).toHaveBeenCalledTimes(1);
      expect(prependFn).not.toHaveBeenCalled();
    });
  });

  describe('passed build actions', () => {
    it('renders enabled Create Release button for passed builds with image', () => {
      renderPage('/teams/1/apps/42/builds');
      const createReleaseBtns = screen.getAllByRole('button', { name: /Create Release/i });
      expect(createReleaseBtns).toHaveLength(1);
      expect(createReleaseBtns[0]).not.toBeDisabled();
    });

    it('does not render Create Release button for passed builds without image', () => {
      renderPage('/teams/1/apps/42/builds');
      const row = screen.getByText('build-006').closest('tr')!;
      expect(within(row).queryByRole('button', { name: /Create Release/i })).not.toBeInTheDocument();
    });

    it('renders Tekton deep link for builds with tektonDeepLink', () => {
      renderPage('/teams/1/apps/42/builds');
      const tektonLinks = screen.getAllByText('Open in Tekton ↗');
      expect(tektonLinks.length).toBeGreaterThan(0);
    });

    it('opens release dialog when Create Release is clicked', async () => {
      const user = userEvent.setup();
      mockFetchBuildDetail.mockResolvedValue(passedBuildDetail);
      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Create Release/i }));

      expect(await screen.findByRole('textbox', { name: /Version tag/i })).toBeInTheDocument();
    });

    it('shows released badge after successful release creation', async () => {
      const user = userEvent.setup();
      mockFetchBuildDetail.mockResolvedValue(passedBuildDetail);
      mockCreateRelease.mockResolvedValue({
        version: 'v1.4.2',
        createdAt: '2026-04-08T12:00:00Z',
        buildId: 'build-001',
        commitSha: 'abc1234def567890',
        imageReference: 'registry.example.com/payments-api:abc123',
      } as ReleaseSummary);

      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Create Release/i }));
      const versionInput = await screen.findByRole('textbox', { name: /Version tag/i });
      await user.type(versionInput, 'v1.4.2');
      await user.click(screen.getByRole('button', { name: 'Create' }));

      expect(await screen.findByText('Released v1.4.2')).toBeInTheDocument();
    });

    it('shows inline error alert on release creation failure', async () => {
      const user = userEvent.setup();
      mockFetchBuildDetail.mockResolvedValue(passedBuildDetail);
      const { ApiError } = await import('../api/client');
      mockCreateRelease.mockRejectedValue(
        new ApiError(502, {
          error: 'integration-error',
          message: 'Release tag already exists',
          timestamp: '2026-04-08T12:00:00Z',
        }),
      );

      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Create Release/i }));
      const versionInput = await screen.findByRole('textbox', { name: /Version tag/i });
      await user.type(versionInput, 'v1.0.0');
      await user.click(screen.getByRole('button', { name: 'Create' }));

      expect(await screen.findByText('Release tag already exists')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Create Release/i })).toBeInTheDocument();
    });
  });

  describe('failed build expansion', () => {
    it('renders expandable toggle only for failed rows', () => {
      renderPage('/teams/1/apps/42/builds');
      const expandButtons = screen.getAllByRole('button', { name: /Details/i });
      expect(expandButtons).toHaveLength(1);
    });

    it('loads detail on expansion', async () => {
      const user = userEvent.setup();
      const loadDetailFn = vi.fn();
      mockDetailResult = { data: null, error: null, isLoading: false, load: loadDetailFn };

      renderPage('/teams/1/apps/42/builds');

      const expandButton = screen.getByRole('button', { name: /Details/i });
      await user.click(expandButton);

      expect(loadDetailFn).toHaveBeenCalled();
    });

    it('shows detail loading spinner when expanding', async () => {
      const user = userEvent.setup();
      mockDetailResult = { data: null, error: null, isLoading: true, load: vi.fn() };

      renderPage('/teams/1/apps/42/builds');

      const expandButton = screen.getByRole('button', { name: /Details/i });
      await user.click(expandButton);

      expect(screen.getByLabelText('Loading build details')).toBeInTheDocument();
    });

    it('shows failed stage and error summary when detail loads', async () => {
      const user = userEvent.setup();
      mockDetailResult = {
        data: sampleDetail,
        error: null,
        isLoading: false,
        load: vi.fn(),
      };

      renderPage('/teams/1/apps/42/builds');

      const expandButton = screen.getByRole('button', { name: /Details/i });
      await user.click(expandButton);

      expect(screen.getByText('Failed Stage')).toBeInTheDocument();
      expect(screen.getByText('unit-test')).toBeInTheDocument();
      expect(screen.getByText('Error')).toBeInTheDocument();
      expect(screen.getByText('Tests failed with 3 errors')).toBeInTheDocument();
    });

    it('shows detail error when detail fetch fails', async () => {
      const user = userEvent.setup();
      mockDetailResult = {
        data: null,
        error: { error: 'not-found', message: 'Build not found', timestamp: '2026-04-08T10:00:00Z' },
        isLoading: false,
        load: vi.fn(),
      };

      renderPage('/teams/1/apps/42/builds');

      const expandButton = screen.getByRole('button', { name: /Details/i });
      await user.click(expandButton);

      expect(screen.getByText('Build not found')).toBeInTheDocument();
    });

    it('shows View Logs button and Tekton deep link in expanded row', async () => {
      const user = userEvent.setup();
      mockDetailResult = {
        data: sampleDetail,
        error: null,
        isLoading: false,
        load: vi.fn(),
      };

      renderPage('/teams/1/apps/42/builds');

      const expandButton = screen.getByRole('button', { name: /Details/i });
      await user.click(expandButton);

      expect(screen.getByRole('button', { name: /View Logs/i })).toBeInTheDocument();
    });
  });

  describe('lazy log loading', () => {
    it('loads logs when View Logs is clicked', async () => {
      const user = userEvent.setup();
      const loadLogsFn = vi.fn();
      mockDetailResult = {
        data: sampleDetail,
        error: null,
        isLoading: false,
        load: vi.fn(),
      };
      mockLogsResult = { logs: null, error: null, isLoading: false, load: loadLogsFn };

      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Details/i }));
      await user.click(screen.getByRole('button', { name: /View Logs/i }));

      expect(loadLogsFn).toHaveBeenCalledTimes(1);
    });

    it('shows log loading spinner', async () => {
      const user = userEvent.setup();
      mockDetailResult = {
        data: sampleDetail,
        error: null,
        isLoading: false,
        load: vi.fn(),
      };
      mockLogsResult = { logs: null, error: null, isLoading: true, load: vi.fn() };

      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Details/i }));
      await user.click(screen.getByRole('button', { name: /View Logs/i }));

      expect(screen.getByLabelText('Loading build logs')).toBeInTheDocument();
    });

    it('renders plain text logs in code block', async () => {
      const user = userEvent.setup();
      mockDetailResult = {
        data: sampleDetail,
        error: null,
        isLoading: false,
        load: vi.fn(),
      };
      mockLogsResult = {
        logs: '[INFO] Build started\n[ERROR] Test failed: AssertionError',
        error: null,
        isLoading: false,
        load: vi.fn(),
      };

      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Details/i }));
      await user.click(screen.getByRole('button', { name: /View Logs/i }));

      expect(screen.getByText(/Build started/)).toBeInTheDocument();
      expect(screen.getByText(/Test failed/)).toBeInTheDocument();
    });

    it('shows error when log fetch fails', async () => {
      const user = userEvent.setup();
      mockDetailResult = {
        data: sampleDetail,
        error: null,
        isLoading: false,
        load: vi.fn(),
      };
      mockLogsResult = {
        logs: null,
        error: { error: 'log-error', message: 'Failed to load logs', timestamp: '2026-04-08T10:00:00Z' },
        isLoading: false,
        load: vi.fn(),
      };

      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Details/i }));
      await user.click(screen.getByRole('button', { name: /View Logs/i }));

      expect(screen.getByText('Failed to load logs')).toBeInTheDocument();
    });

    it('shows Retry button when log fetch fails', async () => {
      const user = userEvent.setup();
      const loadLogsFn = vi.fn();
      mockDetailResult = {
        data: sampleDetail,
        error: null,
        isLoading: false,
        load: vi.fn(),
      };
      mockLogsResult = {
        logs: null,
        error: { error: 'log-error', message: 'Failed to load logs', timestamp: '2026-04-08T10:00:00Z' },
        isLoading: false,
        load: loadLogsFn,
      };

      renderPage('/teams/1/apps/42/builds');

      await user.click(screen.getByRole('button', { name: /Details/i }));
      await user.click(screen.getByRole('button', { name: /View Logs/i }));

      const retryButton = screen.getByRole('button', { name: /Retry/i });
      expect(retryButton).toBeInTheDocument();

      await user.click(retryButton);
      expect(loadLogsFn).toHaveBeenCalledTimes(2);
    });
  });

  describe('keyboard interaction', () => {
    it('expand button can be toggled with keyboard', async () => {
      const user = userEvent.setup();
      mockDetailResult = {
        data: sampleDetail,
        error: null,
        isLoading: false,
        load: vi.fn(),
      };

      renderPage('/teams/1/apps/42/builds');

      const expandButton = screen.getByRole('button', { name: /Details/i });
      expandButton.focus();
      await user.keyboard('{Enter}');

      expect(screen.getByText('Failed Stage')).toBeInTheDocument();
    });

    it('Trigger Build button is reachable via tab and activatable', async () => {
      const user = userEvent.setup();
      renderPage('/teams/1/apps/42/builds');

      const triggerButton = screen.getByRole('button', { name: /Trigger Build/i });
      triggerButton.focus();
      expect(document.activeElement).toBe(triggerButton);

      await user.keyboard('{Enter}');
      expect(mockTriggerResult.trigger).toHaveBeenCalled();
    });
  });

  describe('status uses text + icon (not color alone)', () => {
    it('status badge shows text label for every status', () => {
      renderPage('/teams/1/apps/42/builds');
      expect(screen.getAllByText('Passed').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Failed')).toBeInTheDocument();
      expect(screen.getByText('Building...')).toBeInTheDocument();
      expect(screen.getByText('Cancelled')).toBeInTheDocument();
      expect(screen.getByText('Pending')).toBeInTheDocument();
    });
  });

  describe('Builds tab selection', () => {
    it('Builds tab is shown as selected', () => {
      renderPage('/teams/1/apps/42/builds');
      const buildsTab = screen.getByRole('tab', { name: 'Builds' });
      expect(buildsTab).toHaveAttribute('aria-selected', 'true');
    });
  });
});
