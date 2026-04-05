import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { LoadingSpinner } from './LoadingSpinner';

describe('LoadingSpinner', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders a spinner', () => {
    render(<LoadingSpinner />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('does not show system text initially', () => {
    render(<LoadingSpinner systemName="ArgoCD" />);

    expect(screen.queryByText(/Fetching status from/)).not.toBeInTheDocument();
  });

  it('shows system identification text after 3 seconds', () => {
    vi.useFakeTimers();
    render(<LoadingSpinner systemName="ArgoCD" />);

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(
      screen.getByText('Fetching status from ArgoCD...'),
    ).toBeInTheDocument();
  });

  it('does not show system text after 3 seconds if no systemName', () => {
    vi.useFakeTimers();
    render(<LoadingSpinner />);

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(screen.queryByText(/Fetching status from/)).not.toBeInTheDocument();
  });

  it('cleans up timer on unmount', () => {
    vi.useFakeTimers();
    const clearTimeoutSpy = vi.spyOn(global, 'clearTimeout');

    const { unmount } = render(<LoadingSpinner systemName="Tekton" />);
    unmount();

    expect(clearTimeoutSpy).toHaveBeenCalled();
    clearTimeoutSpy.mockRestore();
  });
});
