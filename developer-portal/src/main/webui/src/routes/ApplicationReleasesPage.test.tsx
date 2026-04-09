import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ApplicationReleasesPage } from './ApplicationReleasesPage';
import { ApplicationLayout } from '../components/layout/ApplicationLayout';
import { ApplicationsProvider } from '../contexts/ApplicationsContext';
import type { ApplicationSummary } from '../types/application';
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
    devSpacesDeepLink: null,
  },
];

const sampleReleases: ReleaseSummary[] = [
  {
    version: 'v1.2.0',
    createdAt: '2026-04-07T10:00:00Z',
    buildId: null,
    commitSha: 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
    imageReference: 'registry.example.com/team/app:v1.2.0',
  },
  {
    version: 'v1.0.0',
    createdAt: '2026-04-01T09:00:00Z',
    buildId: null,
    commitSha: 'c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4',
    imageReference: null,
  },
];

let mockReleasesResult: {
  data: ReleaseSummary[] | null;
  error: PortalError | null;
  isLoading: boolean;
  refresh: ReturnType<typeof vi.fn>;
};

vi.mock('../hooks/useReleases', () => ({
  useReleases: () => mockReleasesResult,
}));

function renderPage(route = '/teams/1/apps/42/releases') {
  return render(
    <ApplicationsProvider value={{ applications: sampleApps, isLoading: false, error: null }}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/teams/:teamId/apps/:appId" element={<ApplicationLayout />}>
            <Route path="releases" element={<ApplicationReleasesPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </ApplicationsProvider>,
  );
}

beforeEach(() => {
  mockReleasesResult = {
    data: sampleReleases,
    error: null,
    isLoading: false,
    refresh: vi.fn(),
  };
});

describe('ApplicationReleasesPage', () => {
  describe('loading state', () => {
    it('shows loading spinner while releases are loading', () => {
      mockReleasesResult = { data: null, error: null, isLoading: true, refresh: vi.fn() };
      renderPage();
      expect(screen.getByRole('progressbar')).toBeInTheDocument();
    });
  });

  describe('error state', () => {
    it('shows inline error alert when releases fail to load', () => {
      mockReleasesResult = {
        data: null,
        error: {
          error: 'integration-error',
          message: 'Failed to load releases from Git',
          timestamp: '2026-04-08T10:00:00Z',
        },
        isLoading: false,
        refresh: vi.fn(),
      };
      renderPage();
      expect(screen.getByText('Failed to load releases from Git')).toBeInTheDocument();
    });
  });

  describe('empty state', () => {
    it('shows empty state when no releases exist', () => {
      mockReleasesResult = { data: [], error: null, isLoading: false, refresh: vi.fn() };
      renderPage();
      expect(screen.getByText('No releases yet')).toBeInTheDocument();
      expect(
        screen.getByText('Create a release from a successful build to start deploying.'),
      ).toBeInTheDocument();
    });
  });

  describe('releases table', () => {
    it('renders releases table with data', () => {
      renderPage();
      expect(screen.getByRole('grid', { name: 'Releases table' })).toBeInTheDocument();
    });

    it('renders all release rows', () => {
      renderPage();
      expect(screen.getByText('v1.2.0')).toBeInTheDocument();
      expect(screen.getByText('v1.0.0')).toBeInTheDocument();
    });

    it('shows truncated commit SHAs', () => {
      renderPage();
      expect(screen.getByText('a1b2c3d')).toBeInTheDocument();
      expect(screen.getByText('c3d4e5f')).toBeInTheDocument();
    });
  });

  describe('page header', () => {
    it('renders Releases heading', () => {
      renderPage();
      expect(screen.getByRole('heading', { name: 'Releases' })).toBeInTheDocument();
    });

    it('renders Refresh button', () => {
      renderPage();
      expect(screen.getByRole('button', { name: /Refresh/i })).toBeInTheDocument();
    });
  });

  describe('Releases tab selection', () => {
    it('Releases tab is shown as selected', () => {
      renderPage();
      const releasesTab = screen.getByRole('tab', { name: 'Releases' });
      expect(releasesTab).toHaveAttribute('aria-selected', 'true');
    });
  });
});
