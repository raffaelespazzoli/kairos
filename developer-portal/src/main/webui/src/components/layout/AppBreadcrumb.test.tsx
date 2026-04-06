import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppBreadcrumb } from './AppBreadcrumb';
import { ApplicationsProvider } from '../../contexts/ApplicationsContext';
import type { ApplicationSummary } from '../../types/application';

const testApps: ApplicationSummary[] = [
  {
    id: 42,
    name: 'Payments API',
    runtimeType: 'quarkus',
    onboardedAt: '2026-04-01T10:00:00Z',
    onboardingPrUrl: '',
    gitRepoUrl: 'https://github.com/org/payments-api.git',
    devSpacesDeepLink: null,
  },
];

function renderBreadcrumb(
  route: string,
  applications: ApplicationSummary[] = [],
) {
  return render(
    <ApplicationsProvider value={{ applications, isLoading: false, error: null }}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/teams/:teamId" element={<AppBreadcrumb />} />
          <Route
            path="/teams/:teamId/apps/:appId"
            element={<AppBreadcrumb />}
          />
          <Route
            path="/teams/:teamId/apps/:appId/:view"
            element={<AppBreadcrumb />}
          />
        </Routes>
      </MemoryRouter>
    </ApplicationsProvider>,
  );
}

describe('AppBreadcrumb', () => {
  it('renders team-level breadcrumb', () => {
    renderBreadcrumb('/teams/platform');
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toBeInTheDocument();
    expect(breadcrumb).toHaveTextContent('My Team');
  });

  it('shows application name from context instead of raw ID', () => {
    renderBreadcrumb('/teams/platform/apps/42', testApps);
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toHaveTextContent('Payments API');
    expect(breadcrumb).toHaveTextContent('Overview');
  });

  it('falls back to raw appId when app not in context', () => {
    renderBreadcrumb('/teams/platform/apps/999');
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toHaveTextContent('999');
  });

  it('renders team + application + view breadcrumb', () => {
    renderBreadcrumb('/teams/platform/apps/42/builds', testApps);
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toHaveTextContent('My Team');
    expect(breadcrumb).toHaveTextContent('Payments API');
    expect(breadcrumb).toHaveTextContent('Builds');
  });

  it('capitalizes the view name', () => {
    renderBreadcrumb('/teams/platform/apps/42/releases', testApps);
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toHaveTextContent('Releases');
  });

  it('team breadcrumb segment links to team page', () => {
    renderBreadcrumb('/teams/platform/apps/42/builds', testApps);
    const teamLink = screen.getByText('My Team');
    expect(teamLink.closest('a')).toHaveAttribute('href', '/teams/platform');
  });

  it('app breadcrumb segment links to app overview', () => {
    renderBreadcrumb('/teams/platform/apps/42/builds', testApps);
    const appLink = screen.getByText('Payments API');
    expect(appLink.closest('a')).toHaveAttribute(
      'href',
      '/teams/platform/apps/42',
    );
  });

  it('current view is not a link (isActive)', () => {
    renderBreadcrumb('/teams/platform/apps/42/builds', testApps);
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    const items = breadcrumb.querySelectorAll('.pf-v6-c-breadcrumb__item');
    const lastItem = items[items.length - 1];
    expect(lastItem.querySelector('a')).toBeNull();
  });

  it('returns null when no teamId in route', () => {
    const { container } = render(
      <ApplicationsProvider value={{ applications: [], isLoading: false, error: null }}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<AppBreadcrumb />} />
          </Routes>
        </MemoryRouter>
      </ApplicationsProvider>,
    );
    expect(container.querySelector('[aria-label="Breadcrumb"]')).toBeNull();
  });
});
