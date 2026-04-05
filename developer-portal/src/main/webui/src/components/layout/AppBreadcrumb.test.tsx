import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppBreadcrumb } from './AppBreadcrumb';

function renderBreadcrumb(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/teams/:teamId" element={<AppBreadcrumb />} />
        <Route path="/teams/:teamId/apps/:appId" element={<AppBreadcrumb />} />
        <Route
          path="/teams/:teamId/apps/:appId/:view"
          element={<AppBreadcrumb />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AppBreadcrumb', () => {
  it('renders team-level breadcrumb', () => {
    renderBreadcrumb('/teams/platform');
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toBeInTheDocument();
    expect(breadcrumb).toHaveTextContent('My Team');
  });

  it('renders team + application + default Overview breadcrumb', () => {
    renderBreadcrumb('/teams/platform/apps/my-app');
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toHaveTextContent('My Team');
    expect(breadcrumb).toHaveTextContent('my-app');
    expect(breadcrumb).toHaveTextContent('Overview');
  });

  it('renders team + application + view breadcrumb', () => {
    renderBreadcrumb('/teams/platform/apps/my-app/builds');
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toHaveTextContent('My Team');
    expect(breadcrumb).toHaveTextContent('my-app');
    expect(breadcrumb).toHaveTextContent('Builds');
  });

  it('capitalizes the view name', () => {
    renderBreadcrumb('/teams/platform/apps/my-app/releases');
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toHaveTextContent('Releases');
  });

  it('team breadcrumb segment links to team page', () => {
    renderBreadcrumb('/teams/platform/apps/my-app/builds');
    const teamLink = screen.getByText('My Team');
    expect(teamLink.closest('a')).toHaveAttribute('href', '/teams/platform');
  });

  it('app breadcrumb segment links to app overview', () => {
    renderBreadcrumb('/teams/platform/apps/my-app/builds');
    const appLink = screen.getByText('my-app');
    expect(appLink.closest('a')).toHaveAttribute(
      'href',
      '/teams/platform/apps/my-app',
    );
  });

  it('current view is not a link (isActive)', () => {
    renderBreadcrumb('/teams/platform/apps/my-app/builds');
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    const items = breadcrumb.querySelectorAll('.pf-v6-c-breadcrumb__item');
    const lastItem = items[items.length - 1];
    expect(lastItem.querySelector('a')).toBeNull();
  });

  it('returns null when no teamId in route', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<AppBreadcrumb />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(container.querySelector('[aria-label="Breadcrumb"]')).toBeNull();
  });
});
