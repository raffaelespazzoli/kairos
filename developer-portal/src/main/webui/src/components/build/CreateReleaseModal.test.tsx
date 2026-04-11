import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { CreateReleaseModal } from './CreateReleaseModal';
import type { BuildSummary } from '../../types/build';

const sampleBuild: BuildSummary = {
  buildId: 'payment-svc-xk7f2',
  status: 'Passed',
  startedAt: '2026-04-08T09:00:00Z',
  completedAt: '2026-04-08T09:05:00Z',
  duration: '5m 0s',
  imageReference: 'registry.example.com/team/app:abc1234',
  applicationName: 'payment-svc',
  tektonDeepLink: null,
};

describe('CreateReleaseModal', () => {
  let onClose: () => void;
  let onSubmit: (version: string) => void;

  beforeEach(() => {
    onClose = vi.fn<() => void>();
    onSubmit = vi.fn<(version: string) => void>();
  });

  function renderModal(overrides: Partial<Parameters<typeof CreateReleaseModal>[0]> = {}) {
    return render(
      <CreateReleaseModal
        isOpen={true}
        build={sampleBuild}
        commitSha="abc1234def567890"
        onClose={onClose}
        onSubmit={onSubmit}
        isSubmitting={false}
        {...overrides}
      />,
    );
  }

  it('renders dialog with build context', () => {
    renderModal();
    expect(screen.getByText('Create Release')).toBeInTheDocument();
    expect(screen.getByText(/#payment-svc-xk7f2/)).toBeInTheDocument();
    expect(screen.getByText('abc1234')).toBeInTheDocument();
    expect(screen.getByText('registry.example.com/team/app:abc1234')).toBeInTheDocument();
  });

  it('shows commit SHA unavailable when null', () => {
    renderModal({ commitSha: null });
    expect(screen.getByText('Commit SHA unavailable')).toBeInTheDocument();
  });

  it('accepts valid semver version', async () => {
    const user = userEvent.setup();
    renderModal();

    const input = screen.getByRole('textbox', { name: /Version tag/i });
    await user.type(input, 'v1.4.2');

    const createBtn = screen.getByRole('button', { name: 'Create' });
    expect(createBtn).not.toBeDisabled();
  });

  it('rejects invalid version format', async () => {
    const user = userEvent.setup();
    renderModal();

    const input = screen.getByRole('textbox', { name: /Version tag/i });
    await user.type(input, 'invalid');

    expect(
      screen.getByText(/Version must start with "v" followed by numbers and dots/),
    ).toBeInTheDocument();
  });

  it('calls onSubmit with version on create click', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByRole('textbox', { name: /Version tag/i }), 'v2.0.0');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    expect(onSubmit).toHaveBeenCalledWith('v2.0.0');
  });

  it('calls onClose on cancel click', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('disables create button when submitting', () => {
    renderModal({ isSubmitting: true });
    const createBtn = screen.getByRole('button', { name: /Create/i });
    expect(createBtn).toBeDisabled();
  });

  it('disables cancel button when submitting', () => {
    renderModal({ isSubmitting: true });
    const cancelBtn = screen.getByRole('button', { name: /Cancel/i });
    expect(cancelBtn).toBeDisabled();
  });

  it('accepts pre-release versions like v1.0.0-rc1', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByRole('textbox', { name: /Version tag/i }), 'v1.0.0-rc1');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    expect(onSubmit).toHaveBeenCalledWith('v1.0.0-rc1');
  });

  it('submits on Enter key', async () => {
    const user = userEvent.setup();
    renderModal();

    const input = screen.getByRole('textbox', { name: /Version tag/i });
    await user.type(input, 'v1.0.0');
    await user.keyboard('{Enter}');

    expect(onSubmit).toHaveBeenCalledWith('v1.0.0');
  });

  it('hides image field when imageReference is null', () => {
    const buildNoImage = { ...sampleBuild, imageReference: null };
    renderModal({ build: buildNoImage });
    expect(screen.queryByText('Image')).not.toBeInTheDocument();
  });
});
