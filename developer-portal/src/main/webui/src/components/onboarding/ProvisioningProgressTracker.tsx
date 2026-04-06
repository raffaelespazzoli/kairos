import {
  Button,
  ProgressStepper,
  ProgressStep,
  Content,
} from '@patternfly/react-core';
import type { ProvisioningStep } from '../../types/onboarding';

interface ProvisioningProgressTrackerProps {
  steps: ProvisioningStep[];
  totalSteps: number;
  onRetry: () => void;
}

function getStepVariant(
  status: ProvisioningStep['status'],
): 'success' | 'danger' | 'info' | 'pending' {
  switch (status) {
    case 'completed':
      return 'success';
    case 'failed':
      return 'danger';
    case 'in-progress':
      return 'info';
    default:
      return 'pending';
  }
}

export function ProvisioningProgressTracker({
  steps,
  totalSteps,
  onRetry,
}: ProvisioningProgressTrackerProps) {
  const completedCount = steps.filter((s) => s.status === 'completed').length;
  const hasFailed = steps.some((s) => s.status === 'failed');

  return (
    <div>
      <Content component="p" className="pf-v6-u-mb-md">
        {completedCount}/{totalSteps} complete
      </Content>

      <div aria-live="polite">
        <ProgressStepper isVertical>
          {steps.map((step) => (
            <ProgressStep
              key={step.id}
              id={step.id}
              titleId={`${step.id}-title`}
              variant={getStepVariant(step.status)}
              description={step.status === 'failed' ? step.error : undefined}
            >
              {step.label}
            </ProgressStep>
          ))}
        </ProgressStepper>
      </div>

      {hasFailed && (
        <Button
          variant="secondary"
          onClick={onRetry}
          className="pf-v6-u-mt-md"
        >
          Retry
        </Button>
      )}
    </div>
  );
}
