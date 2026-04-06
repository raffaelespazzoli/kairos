import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DeepLinkButton } from './DeepLinkButton';

describe('DeepLinkButton', () => {
  it('renders default label with toolName', () => {
    render(<DeepLinkButton href="https://example.com" toolName="ArgoCD" />);
    expect(screen.getByText('Open in ArgoCD ↗')).toBeInTheDocument();
  });

  it('renders custom label when provided', () => {
    render(
      <DeepLinkButton
        href="https://vault.example.com"
        toolName="Vault"
        label="dev — Manage secrets in Vault ↗"
      />,
    );
    expect(
      screen.getByText('dev — Manage secrets in Vault ↗'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Open in Vault ↗')).not.toBeInTheDocument();
  });

  it('sets target="_blank" and rel="noopener noreferrer"', () => {
    render(<DeepLinkButton href="https://example.com" toolName="Test" />);
    const link = screen.getByText('Open in Test ↗').closest('a');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('sets correct href', () => {
    render(
      <DeepLinkButton href="https://devspaces.example.com/#/repo" toolName="DevSpaces" />,
    );
    const link = screen.getByText('Open in DevSpaces ↗').closest('a');
    expect(link).toHaveAttribute('href', 'https://devspaces.example.com/#/repo');
  });

  it('renders nothing when href is null', () => {
    const { container } = render(
      <DeepLinkButton href={null} toolName="DevSpaces" />,
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when href is undefined', () => {
    const { container } = render(
      <DeepLinkButton href={undefined} toolName="DevSpaces" />,
    );
    expect(container.firstChild).toBeNull();
  });
});
