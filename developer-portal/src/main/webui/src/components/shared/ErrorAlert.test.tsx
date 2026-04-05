import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErrorAlert } from './ErrorAlert';
import type { PortalError } from '../../types/error';

describe('ErrorAlert', () => {
  const baseError: PortalError = {
    error: 'integration-error',
    message: 'ArgoCD sync failed: connection timeout',
    timestamp: '2026-04-04T10:00:00Z',
  };

  it('renders the error message as alert title', () => {
    render(<ErrorAlert error={baseError} />);

    expect(
      screen.getByText('ArgoCD sync failed: connection timeout'),
    ).toBeInTheDocument();
  });

  it('uses danger variant for inline alert', () => {
    const { container } = render(<ErrorAlert error={baseError} />);

    const alert = container.querySelector('.pf-v6-c-alert');
    expect(alert).toHaveClass('pf-m-danger');
    expect(alert).toHaveClass('pf-m-inline');
  });

  it('displays detail text when present', () => {
    const errorWithDetail: PortalError = {
      ...baseError,
      detail: 'Pod readiness probe failed after 120s',
    };
    render(<ErrorAlert error={errorWithDetail} />);

    expect(
      screen.getByText('Pod readiness probe failed after 120s'),
    ).toBeInTheDocument();
  });

  it('does not display detail when absent', () => {
    render(<ErrorAlert error={baseError} />);

    const paragraphs = document.querySelectorAll('.pf-v6-c-alert__description p');
    expect(paragraphs).toHaveLength(0);
  });

  it('shows deep link button when deepLink and system are present', () => {
    const errorWithLink: PortalError = {
      ...baseError,
      system: 'argocd',
      deepLink: 'https://argocd.internal/applications/my-app',
    };
    const { container } = render(<ErrorAlert error={errorWithLink} />);

    expect(screen.getByText('Open in argocd ↗')).toBeInTheDocument();
    const link = container.querySelector('a[href="https://argocd.internal/applications/my-app"]');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('shows "Open in tool" when deepLink present but system absent', () => {
    const errorWithLinkNoSystem: PortalError = {
      ...baseError,
      deepLink: 'https://example.com/debug',
    };
    render(<ErrorAlert error={errorWithLinkNoSystem} />);

    expect(screen.getByText('Open in tool ↗')).toBeInTheDocument();
  });

  it('does not show deep link button when deepLink is absent', () => {
    render(<ErrorAlert error={baseError} />);

    expect(screen.queryByText(/Open in/)).not.toBeInTheDocument();
  });
});
