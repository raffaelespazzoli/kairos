import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { NoApplicationsEmptyState } from './NoApplicationsEmptyState';

function renderEmptyState() {
  return render(
    <MemoryRouter initialEntries={['/teams/default']}>
      <Routes>
        <Route
          path="/teams/:teamId"
          element={<NoApplicationsEmptyState />}
        />
        <Route
          path="/teams/:teamId/onboard"
          element={<div>Onboard Page</div>}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('NoApplicationsEmptyState', () => {
  it('renders the exact title text', () => {
    renderEmptyState();
    expect(
      screen.getByText('No applications onboarded yet'),
    ).toBeInTheDocument();
  });

  it('renders the exact description text', () => {
    renderEmptyState();
    expect(
      screen.getByText(
        /Your team is recognized — get started by onboarding your first application\./,
      ),
    ).toBeInTheDocument();
  });

  it('renders the primary Onboard Application button', () => {
    renderEmptyState();
    const button = screen.getByRole('button', { name: 'Onboard Application' });
    expect(button).toBeInTheDocument();
    expect(button).toHaveClass('pf-m-primary');
  });

  it('navigates to onboard page when CTA is clicked', async () => {
    const user = userEvent.setup();
    renderEmptyState();
    const button = screen.getByRole('button', { name: 'Onboard Application' });
    await user.click(button);
    expect(screen.getByText('Onboard Page')).toBeInTheDocument();
  });
});
