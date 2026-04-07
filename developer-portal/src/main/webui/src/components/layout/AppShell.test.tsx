import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AppShell } from './AppShell';

vi.mock('../../hooks/useApiFetch', () => ({
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

function renderShell(route = '/teams/1') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/teams/:teamId" element={<div>Team Page</div>} />
          <Route
            path="/teams/:teamId/apps/:appId/*"
            element={<div>App Page</div>}
          />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AppShell', () => {
  it('renders the portal logo/name in the masthead', () => {
    renderShell();
    expect(screen.getByText('Developer Portal')).toBeInTheDocument();
  });

  it('renders the user avatar with team name in the masthead', () => {
    renderShell();
    const mastheadContent = document.querySelector(
      '.pf-v6-c-masthead__content',
    );
    expect(mastheadContent).toHaveTextContent('My Team');
  });

  it('renders the user avatar placeholder', () => {
    renderShell();
    expect(document.querySelector('.portal-user-avatar')).toBeInTheDocument();
  });

  it('renders the sidebar toggle button', () => {
    renderShell();
    expect(screen.getByLabelText('Global navigation')).toBeInTheDocument();
  });

  it('toggles sidebar when hamburger is clicked', async () => {
    const user = userEvent.setup();
    renderShell();
    const toggle = screen.getByLabelText('Global navigation');
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
  });

  it('renders the breadcrumb with team name', () => {
    renderShell();
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toBeInTheDocument();
    expect(breadcrumb).toHaveTextContent('My Team');
  });

  it('renders the sidebar with team name and onboard button', () => {
    renderShell();
    expect(screen.getByText('+ Onboard Application')).toBeInTheDocument();
  });

  it('renders main content area from route outlet', () => {
    renderShell();
    expect(screen.getByText('Team Page')).toBeInTheDocument();
  });
});
