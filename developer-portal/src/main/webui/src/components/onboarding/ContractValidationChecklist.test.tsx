import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ContractValidationChecklist } from './ContractValidationChecklist';
import type { ContractValidationResult } from '../../types/onboarding';

function makeResult(overrides: Partial<ContractValidationResult> = {}): ContractValidationResult {
  return {
    allPassed: true,
    checks: [
      { name: 'Helm Build Chart', passed: true, detail: 'Helm build chart found', fixInstruction: null },
      { name: 'Helm Run Chart', passed: true, detail: 'Helm run chart found', fixInstruction: null },
      { name: 'Build Values', passed: true, detail: 'Build values file found', fixInstruction: null },
      { name: 'Environment Values', passed: true, detail: '2 environment(s) detected: dev, qa', fixInstruction: null },
      { name: 'Runtime Detection', passed: true, detail: 'Runtime Detected: Quarkus/Java via pom.xml', fixInstruction: null },
    ],
    runtimeType: 'Quarkus/Java',
    detectedEnvironments: ['dev', 'qa'],
    ...overrides,
  };
}

describe('ContractValidationChecklist', () => {
  it('shows all checks passed with green header', () => {
    render(<ContractValidationChecklist result={makeResult()} onRetry={vi.fn()} isRetrying={false} />);

    expect(screen.getByText('5/5 passed')).toBeInTheDocument();
  });

  it('does not show retry button when all checks pass', () => {
    render(<ContractValidationChecklist result={makeResult()} onRetry={vi.fn()} isRetrying={false} />);

    expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
  });

  it('shows failed checks with red icons and retry button', () => {
    const result = makeResult({
      allPassed: false,
      checks: [
        { name: 'Helm Build Chart', passed: false, detail: 'Chart.yaml not found', fixInstruction: 'Create `.helm/build/` directory with a valid Chart.yaml' },
        { name: 'Helm Run Chart', passed: true, detail: 'Helm run chart found', fixInstruction: null },
        { name: 'Build Values', passed: true, detail: 'Build values file found', fixInstruction: null },
        { name: 'Environment Values', passed: true, detail: '1 environment(s) detected: dev', fixInstruction: null },
        { name: 'Runtime Detection', passed: true, detail: 'Runtime Detected: Quarkus/Java via pom.xml', fixInstruction: null },
      ],
    });

    render(<ContractValidationChecklist result={result} onRetry={vi.fn()} isRetrying={false} />);

    expect(screen.getByText('4/5 passed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('calls onRetry when retry button is clicked', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const result = makeResult({
      allPassed: false,
      checks: [
        { name: 'Helm Build Chart', passed: false, detail: 'Not found', fixInstruction: 'Create it' },
        { name: 'Helm Run Chart', passed: true, detail: 'Found', fixInstruction: null },
        { name: 'Build Values', passed: true, detail: 'Found', fixInstruction: null },
        { name: 'Environment Values', passed: true, detail: '1 env', fixInstruction: null },
        { name: 'Runtime Detection', passed: true, detail: 'Quarkus', fixInstruction: null },
      ],
    });

    render(<ContractValidationChecklist result={result} onRetry={onRetry} isRetrying={false} />);
    await user.click(screen.getByRole('button', { name: /retry/i }));

    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('renders correct aria-labels on list items', () => {
    const result = makeResult({
      allPassed: false,
      checks: [
        { name: 'Helm Build Chart', passed: false, detail: 'Not found', fixInstruction: 'Create `.helm/build/` directory with a valid Chart.yaml' },
        { name: 'Helm Run Chart', passed: true, detail: 'Helm run chart found', fixInstruction: null },
        { name: 'Build Values', passed: true, detail: 'Build values file found', fixInstruction: null },
        { name: 'Environment Values', passed: true, detail: '1 env detected', fixInstruction: null },
        { name: 'Runtime Detection', passed: true, detail: 'Runtime Detected: Quarkus/Java via pom.xml', fixInstruction: null },
      ],
    });

    render(<ContractValidationChecklist result={result} onRetry={vi.fn()} isRetrying={false} />);

    expect(screen.getByRole('listitem', {
      name: /Failed: Helm Build Chart — Create `.helm\/build\/` directory with a valid Chart.yaml/,
    })).toBeInTheDocument();

    expect(screen.getByRole('listitem', {
      name: /Passed: Helm Run Chart — Helm run chart found/,
    })).toBeInTheDocument();
  });

  it('shows detected environments', () => {
    render(<ContractValidationChecklist result={makeResult()} onRetry={vi.fn()} isRetrying={false} />);

    expect(screen.getByText(/Detected environments: dev, qa/)).toBeInTheDocument();
  });

  it('does not show detected environments when empty', () => {
    const result = makeResult({ detectedEnvironments: [] });
    render(<ContractValidationChecklist result={result} onRetry={vi.fn()} isRetrying={false} />);

    expect(screen.queryByText(/Detected environments/)).not.toBeInTheDocument();
  });
});
