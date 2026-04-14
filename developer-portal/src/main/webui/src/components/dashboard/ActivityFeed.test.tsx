import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ActivityFeed, formatRelativeTime } from './ActivityFeed';
import type { TeamActivityEventDto } from '../../types/dashboard';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

const sampleEvents: TeamActivityEventDto[] = [
  {
    eventType: 'build',
    applicationId: 1,
    applicationName: 'checkout-api',
    reference: '#142',
    timestamp: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    status: 'Passed',
    actor: 'Marco',
    environmentName: null,
  },
  {
    eventType: 'deployment',
    applicationId: 1,
    applicationName: 'checkout-api',
    reference: 'v2.1.0',
    timestamp: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    status: 'Deployed',
    actor: 'Alice',
    environmentName: 'qa',
  },
  {
    eventType: 'release',
    applicationId: 2,
    applicationName: 'payments-service',
    reference: 'v1.3.0',
    timestamp: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
    status: 'Released',
    actor: 'Bob',
    environmentName: null,
  },
];

function renderFeed(events: TeamActivityEventDto[] = sampleEvents) {
  return render(
    <MemoryRouter initialEntries={['/teams/1']}>
      <Routes>
        <Route
          path="/teams/:teamId"
          element={<ActivityFeed events={events} />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ActivityFeed', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  it('renders correct number of DataList items', () => {
    renderFeed();
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(3);
  });

  it('shows application name and reference for each event', () => {
    renderFeed();
    const checkoutSpans = screen.getAllByText('checkout-api');
    expect(checkoutSpans.length).toBe(2);
    expect(screen.getByText('#142')).toBeInTheDocument();
    expect(screen.getByText('v2.1.0')).toBeInTheDocument();
    expect(screen.getByText('payments-service')).toBeInTheDocument();
    expect(screen.getByText('v1.3.0')).toBeInTheDocument();
  });

  it('shows status label with correct text', () => {
    renderFeed();
    expect(screen.getByText('Passed')).toBeInTheDocument();
    expect(screen.getByText('Deployed')).toBeInTheDocument();
    expect(screen.getByText('Released')).toBeInTheDocument();
  });

  it('shows actor name for each event', () => {
    renderFeed();
    expect(screen.getByText(/Marco/)).toBeInTheDocument();
    expect(screen.getByText(/Alice/)).toBeInTheDocument();
    expect(screen.getByText(/Bob/)).toBeInTheDocument();
  });

  it('shows environment name for deployment events', () => {
    renderFeed();
    expect(screen.getByText(/→ qa/)).toBeInTheDocument();
  });

  it('does not show environment for non-deployment events', () => {
    renderFeed([sampleEvents[0]]);
    expect(screen.queryByText(/→/)).not.toBeInTheDocument();
  });

  it('click on build event navigates to builds page', async () => {
    const user = userEvent.setup();
    renderFeed([sampleEvents[0]]);
    const item = screen.getByRole('listitem');
    await user.click(item);
    expect(mockNavigate).toHaveBeenCalledWith('/teams/1/apps/1/builds');
  });

  it('click on deployment event navigates to overview page', async () => {
    const user = userEvent.setup();
    renderFeed([sampleEvents[1]]);
    const item = screen.getByRole('listitem');
    await user.click(item);
    expect(mockNavigate).toHaveBeenCalledWith('/teams/1/apps/1/overview');
  });

  it('click on release event navigates to releases page', async () => {
    const user = userEvent.setup();
    renderFeed([sampleEvents[2]]);
    const item = screen.getByRole('listitem');
    await user.click(item);
    expect(mockNavigate).toHaveBeenCalledWith('/teams/1/apps/2/releases');
  });

  it('keyboard Enter on item triggers navigation', async () => {
    const user = userEvent.setup();
    renderFeed([sampleEvents[0]]);
    const item = screen.getByRole('listitem');
    item.focus();
    await user.keyboard('{Enter}');
    expect(mockNavigate).toHaveBeenCalledWith('/teams/1/apps/1/builds');
  });

  it('each item has correct aria-label', () => {
    renderFeed();
    expect(
      screen.getByLabelText(/build, checkout-api, passed, \d+m ago/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/deployment, checkout-api, deployed, → qa, \d+h ago/i),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText(/release, payments-service, released, \d+d ago/i),
    ).toBeInTheDocument();
  });

  it('shows empty state message when no events', () => {
    renderFeed([]);
    expect(
      screen.getByText('No recent activity across team applications'),
    ).toBeInTheDocument();
  });

  it('shows custom empty message when emptyMessage prop is provided', () => {
    render(
      <MemoryRouter initialEntries={['/teams/1']}>
        <Routes>
          <Route
            path="/teams/:teamId"
            element={<ActivityFeed events={[]} emptyMessage="No recent activity" />}
          />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('No recent activity')).toBeInTheDocument();
    expect(
      screen.queryByText('No recent activity across team applications'),
    ).not.toBeInTheDocument();
  });

  it('does not render DataList when events are empty', () => {
    renderFeed([]);
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});

describe('formatRelativeTime', () => {
  it('returns "just now" for timestamps less than 1 minute ago', () => {
    const recent = new Date(Date.now() - 30 * 1000).toISOString();
    expect(formatRelativeTime(recent)).toBe('just now');
  });

  it('returns minutes for timestamps under an hour', () => {
    const fiveMinAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    expect(formatRelativeTime(fiveMinAgo)).toBe('5m ago');
  });

  it('returns hours for timestamps under a day', () => {
    const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
    expect(formatRelativeTime(twoHoursAgo)).toBe('2h ago');
  });

  it('returns days for timestamps over a day', () => {
    const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString();
    expect(formatRelativeTime(threeDaysAgo)).toBe('3d ago');
  });

  it('returns "just now" for future timestamps', () => {
    const future = new Date(Date.now() + 60 * 1000).toISOString();
    expect(formatRelativeTime(future)).toBe('just now');
  });

  it('returns raw string for invalid timestamps', () => {
    expect(formatRelativeTime('not-a-date')).toBe('not-a-date');
  });
});
