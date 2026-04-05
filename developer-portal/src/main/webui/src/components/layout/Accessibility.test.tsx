import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppShell } from './AppShell';
import { ApplicationLayout } from './ApplicationLayout';

function renderFullShell(route = '/teams/default/apps/my-app/overview') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/teams/:teamId" element={<div>Team Page</div>} />
          <Route
            path="/teams/:teamId/apps/:appId"
            element={<ApplicationLayout />}
          >
            <Route path=":view" element={<div>Tab Content</div>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('Accessibility', () => {
  it('sidebar navigation has aria-label', () => {
    renderFullShell();
    expect(
      screen.getByLabelText('Application navigation'),
    ).toBeInTheDocument();
    const nav = screen.getByLabelText('Application navigation');
    expect(nav.tagName).toBe('NAV');
  });

  it('breadcrumb has aria-label and is a nav element', () => {
    renderFullShell();
    const breadcrumb = screen.getByLabelText('Breadcrumb');
    expect(breadcrumb).toBeInTheDocument();
    expect(breadcrumb.tagName).toBe('NAV');
  });

  it('tabs have proper tablist role', () => {
    renderFullShell();
    const tablist = screen.getByRole('tablist');
    expect(tablist).toBeInTheDocument();
  });

  it('each tab has proper tab role', () => {
    renderFullShell();
    const tabs = screen.getAllByRole('tab');
    expect(tabs.length).toBe(6);
  });

  it('sidebar toggle has aria-label', () => {
    renderFullShell();
    expect(screen.getByLabelText('Global navigation')).toBeInTheDocument();
  });

  it('main content area uses <main> element', () => {
    renderFullShell();
    const mainElement = document.querySelector('main');
    expect(mainElement).toBeInTheDocument();
  });

  it('sidebar toggle has aria-expanded attribute', () => {
    renderFullShell();
    const toggle = screen.getByLabelText('Global navigation');
    expect(toggle).toHaveAttribute('aria-expanded');
  });

  it('application tabs have aria-label', () => {
    renderFullShell();
    expect(screen.getByLabelText('Application tabs')).toBeInTheDocument();
  });

  it('all interactive elements are buttons (not divs)', () => {
    renderFullShell();
    const sidebarToggle = screen.getByLabelText('Global navigation');
    expect(sidebarToggle.tagName).toBe('BUTTON');

    const onboardBtn = screen.getByText('+ Onboard Application').closest('button');
    expect(onboardBtn).toBeInTheDocument();

    const tabs = screen.getAllByRole('tab');
    tabs.forEach((tab) => {
      expect(tab.tagName).toBe('BUTTON');
    });
  });
});
