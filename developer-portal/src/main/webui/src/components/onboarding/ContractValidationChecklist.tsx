import { Button, Label, List, ListItem, Content } from '@patternfly/react-core';
import { CheckCircleIcon, TimesCircleIcon } from '@patternfly/react-icons';
import type { ContractValidationResult } from '../../types/onboarding';

interface ContractValidationChecklistProps {
  result: ContractValidationResult;
  onRetry: () => void;
  isRetrying: boolean;
}

export function ContractValidationChecklist({
  result,
  onRetry,
  isRetrying,
}: ContractValidationChecklistProps) {
  const passedCount = result.checks.filter((c) => c.passed).length;
  const totalCount = result.checks.length;

  return (
    <div>
      <div className="pf-v6-u-mb-md">
        <Label color={result.allPassed ? 'green' : 'red'}>
          {passedCount}/{totalCount} passed
        </Label>
      </div>

      <List role="list">
        {result.checks.map((check) => (
          <ListItem
            key={check.name}
            role="listitem"
            aria-label={`${check.passed ? 'Passed' : 'Failed'}: ${check.name} — ${check.passed ? check.detail : check.fixInstruction}`}
          >
            {check.passed ? (
              <CheckCircleIcon color="var(--pf-t--global--color--status--success--default)" />
            ) : (
              <TimesCircleIcon color="var(--pf-t--global--color--status--danger--default)" />
            )}{' '}
            <strong>{check.name}</strong>
            {' — '}
            {check.passed ? check.detail : check.fixInstruction}
          </ListItem>
        ))}
      </List>

      {result.detectedEnvironments.length > 0 && (
        <Content component="p" className="pf-v6-u-mt-md">
          Detected environments: {result.detectedEnvironments.join(', ')}
        </Content>
      )}

      {!result.allPassed && (
        <Button
          variant="secondary"
          onClick={onRetry}
          isDisabled={isRetrying}
          isLoading={isRetrying}
          className="pf-v6-u-mt-md"
        >
          Retry Validation
        </Button>
      )}
    </div>
  );
}
