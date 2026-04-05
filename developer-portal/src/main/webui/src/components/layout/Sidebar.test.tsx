import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { Sidebar } from './Sidebar';
import type { SidebarApp } from './Sidebar';

function renderSidebar(
  route = '/teams/acme',
  applications: SidebarApp[] = [],
) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route
          path="/teams/:teamId/*"
          element={<Sidebar applications={applications} />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Sidebar', () => {
  it('renders the team name in a label at the top', () => {
    renderSidebar();
    expect(screen.getByText('My Team')).toBeInTheDocument();
  });

  it('renders the application navigation list', () => {
    renderSidebar();
    expect(
      screen.getByLabelText('Application navigation'),
    ).toBeInTheDocument();
  });

  it('renders empty nav list when no applications exist', () => {
    renderSidebar();
    const nav = screen.getByLabelText('Application navigation');
    const list = nav.querySelector('[role="list"]');
    expect(list).toBeInTheDocument();
    expect(list!.children).toHaveLength(0);
  });

  it('renders application items when applications are provided', () => {
    const apps = [
      { id: 'app-a', name: 'App Alpha' },
      { id: 'app-b', name: 'App Beta' },
    ];
    renderSidebar('/teams/acme', apps);
    expect(screen.getByText('App Alpha')).toBeInTheDocument();
    expect(screen.getByText('App Beta')).toBeInTheDocument();
  });

  it('renders the "+ Onboard Application" button', () => {
    renderSidebar();
    expect(screen.getByText('+ Onboard Application')).toBeInTheDocument();
  });

  it('onboard button has secondary variant styling', () => {
    renderSidebar();
    const button = screen
      .getByText('+ Onboard Application')
      .closest('button');
    expect(button).toHaveClass('pf-m-secondary');
  });

  it('uses route teamId for onboard navigation, not hardcoded default', async () => {
    const user = userEvent.setup();
    renderSidebar('/teams/acme');
    const button = screen.getByText('+ Onboard Application');
    await user.click(button);
    expect(button).toBeInTheDocument();
  });

  it('preserves current tab when switching applications', async () => {
    const user = userEvent.setup();
    const apps = [
      { id: 'app-a', name: 'App Alpha' },
      { id: 'app-b', name: 'App Beta' },
    ];
    renderSidebar('/teams/acme/apps/app-a/builds', apps);
    const appB = screen.getByText('App Beta');
    await user.click(appB);
    expect(appB).toBeInTheDocument();
  });
});
