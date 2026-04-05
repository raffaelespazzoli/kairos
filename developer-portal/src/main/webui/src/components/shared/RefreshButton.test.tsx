import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RefreshButton } from './RefreshButton';

describe('RefreshButton', () => {
  it('renders with refresh aria-label', () => {
    render(<RefreshButton onRefresh={() => {}} isRefreshing={false} />);

    expect(screen.getByRole('button', { name: 'Refresh' })).toBeInTheDocument();
  });

  it('calls onRefresh when clicked', async () => {
    const user = userEvent.setup();
    const onRefresh = vi.fn();
    render(<RefreshButton onRefresh={onRefresh} isRefreshing={false} />);

    await user.click(screen.getByRole('button', { name: 'Refresh' }));

    expect(onRefresh).toHaveBeenCalledOnce();
  });

  it('is disabled while refreshing', () => {
    render(<RefreshButton onRefresh={() => {}} isRefreshing={true} />);

    expect(screen.getByRole('button', { name: 'Refresh' })).toBeDisabled();
  });

  it('does not call onRefresh when disabled', async () => {
    const user = userEvent.setup();
    const onRefresh = vi.fn();
    render(<RefreshButton onRefresh={onRefresh} isRefreshing={true} />);

    await user.click(screen.getByRole('button', { name: 'Refresh' }));

    expect(onRefresh).not.toHaveBeenCalled();
  });

  it('accepts custom aria-label', () => {
    render(
      <RefreshButton
        onRefresh={() => {}}
        isRefreshing={false}
        aria-label="Reload builds"
      />,
    );

    expect(
      screen.getByRole('button', { name: 'Reload builds' }),
    ).toBeInTheDocument();
  });

  it('shows spinning icon when refreshing', () => {
    const { container } = render(
      <RefreshButton onRefresh={() => {}} isRefreshing={true} />,
    );

    const icon = container.querySelector('svg');
    expect(icon).toHaveClass('pf-v6-u-spin');
  });

  it('does not show spinning icon when idle', () => {
    const { container } = render(
      <RefreshButton onRefresh={() => {}} isRefreshing={false} />,
    );

    const icon = container.querySelector('svg');
    expect(icon).not.toHaveClass('pf-v6-u-spin');
  });
});
