import { createRef } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PromotionConfirmation } from './PromotionConfirmation';

function renderConfirmation(overrides: Record<string, unknown> = {}) {
  const triggerRef = createRef<HTMLButtonElement>();
  const defaultProps = {
    version: 'v1.4.2',
    targetEnvName: 'QA',
    targetNamespace: 'orders-orders-api-qa',
    targetCluster: 'ocp-qa-01',
    isProduction: false,
    actionType: 'promote' as const,
    isOpen: true,
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
    triggerRef,
  };
  const props = { ...defaultProps, ...overrides };
  return { ...props, ...render(<PromotionConfirmation {...props} />) };
}

describe('PromotionConfirmation', () => {
  describe('Non-production (Popover)', () => {
    it('renders popover with correct header and body', () => {
      renderConfirmation();
      expect(screen.getByText('Promote v1.4.2 to QA?')).toBeInTheDocument();
      expect(screen.getByText('→ orders-orders-api-qa on ocp-qa-01')).toBeInTheDocument();
    });

    it('"Promote" button calls onConfirm', async () => {
      const user = userEvent.setup();
      const { onConfirm } = renderConfirmation();
      await user.click(screen.getByRole('button', { name: 'Promote' }));
      expect(onConfirm).toHaveBeenCalledTimes(1);
    });

    it('"Cancel" button calls onCancel', async () => {
      const user = userEvent.setup();
      const { onCancel } = renderConfirmation();
      await user.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('does not render modal elements', () => {
      renderConfirmation();
      expect(screen.queryByText('Deploy to PRODUCTION')).not.toBeInTheDocument();
    });

    it('uses "Deploy" wording when actionType is deploy', () => {
      renderConfirmation({ actionType: 'deploy' });
      expect(screen.getByText('Deploy v1.4.2 to QA?')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Deploy' })).toBeInTheDocument();
    });
  });

  describe('Production (Modal)', () => {
    it('renders modal with correct title', () => {
      renderConfirmation({ isProduction: true });
      expect(screen.getByText('Deploy to PRODUCTION')).toBeInTheDocument();
    });

    it('shows version, target namespace, and cluster', () => {
      renderConfirmation({ isProduction: true });
      expect(screen.getByText('v1.4.2')).toBeInTheDocument();
      expect(screen.getByText('orders-orders-api-qa')).toBeInTheDocument();
      expect(screen.getByText('ocp-qa-01')).toBeInTheDocument();
    });

    it('shows production warning text', () => {
      renderConfirmation({ isProduction: true });
      expect(screen.getByText('This will deploy to production.')).toBeInTheDocument();
    });

    it('"Deploy to Prod" button calls onConfirm', async () => {
      const user = userEvent.setup();
      const { onConfirm } = renderConfirmation({ isProduction: true });
      await user.click(screen.getByRole('button', { name: 'Deploy to Prod' }));
      expect(onConfirm).toHaveBeenCalledTimes(1);
    });

    it('"Cancel" button calls onCancel', async () => {
      const user = userEvent.setup();
      const { onCancel } = renderConfirmation({ isProduction: true });
      await user.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('does not render popover elements', () => {
      renderConfirmation({ isProduction: true });
      expect(screen.queryByText(/Promote v1.4.2 to/)).not.toBeInTheDocument();
    });

    it('shows "Unknown" when cluster is null', () => {
      renderConfirmation({ isProduction: true, targetCluster: null });
      expect(screen.getByText('Unknown')).toBeInTheDocument();
    });

    it('Escape key dismisses modal', async () => {
      const user = userEvent.setup();
      const { onCancel } = renderConfirmation({ isProduction: true });
      await user.keyboard('{Escape}');
      expect(onCancel).toHaveBeenCalled();
    });

    it('returns focus to trigger button on dismiss', async () => {
      const triggerRef = { current: document.createElement('button') };
      document.body.appendChild(triggerRef.current);
      const focusSpy = vi.spyOn(triggerRef.current, 'focus');

      const user = userEvent.setup();
      renderConfirmation({ isProduction: true, triggerRef });
      await user.click(screen.getByRole('button', { name: 'Cancel' }));

      expect(focusSpy).toHaveBeenCalled();
      document.body.removeChild(triggerRef.current);
    });
  });
});
