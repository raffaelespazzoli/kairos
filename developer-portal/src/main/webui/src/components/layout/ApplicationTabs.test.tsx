import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ApplicationTabs } from './ApplicationTabs';

function renderTabs(route = '/teams/default/apps/my-app/overview') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route
          path="/teams/:teamId/apps/:appId/:view"
          element={<ApplicationTabs />}
        />
        <Route
          path="/teams/:teamId/apps/:appId"
          element={<ApplicationTabs />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ApplicationTabs', () => {
  it('renders all 3 tabs', () => {
    renderTabs();
    expect(screen.getByText('Overview')).toBeInTheDocument();
    expect(screen.getByText('Delivery')).toBeInTheDocument();
    expect(screen.getByText('Metrics')).toBeInTheDocument();
    expect(screen.queryByText('Builds')).not.toBeInTheDocument();
    expect(screen.queryByText('Releases')).not.toBeInTheDocument();
  });

  it('has Application tabs aria-label', () => {
    renderTabs();
    expect(screen.getByLabelText('Application tabs')).toBeInTheDocument();
  });

  it('highlights the active tab based on URL path', () => {
    renderTabs('/teams/default/apps/my-app/delivery');
    const deliveryTab = screen.getByText('Delivery').closest('button');
    expect(deliveryTab).toHaveAttribute('aria-selected', 'true');
  });

  it('defaults to overview tab when no view in URL', () => {
    renderTabs('/teams/default/apps/my-app');
    const overviewTab = screen.getByText('Overview').closest('button');
    expect(overviewTab).toHaveAttribute('aria-selected', 'true');
  });

  it('navigates when a tab is clicked', async () => {
    const user = userEvent.setup();
    renderTabs('/teams/default/apps/my-app/overview');
    const deliveryTab = screen.getByText('Delivery');
    await user.click(deliveryTab);
    const deliveryButton = screen.getByText('Delivery').closest('button');
    expect(deliveryButton).toHaveAttribute('aria-selected', 'true');
  });
});
