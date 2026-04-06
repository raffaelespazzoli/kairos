import { useNavigate } from 'react-router-dom';
import {
  EmptyState,
  EmptyStateBody,
  EmptyStateFooter,
  EmptyStateActions,
  Button,
  Content,
  Label,
  Tooltip,
} from '@patternfly/react-core';
import { CheckCircleIcon, ArrowRightIcon } from '@patternfly/react-icons';
import type { OnboardingResult } from '../../types/onboarding';

interface OnboardingCompletionPanelProps {
  result: OnboardingResult;
  teamId: string;
}

export function OnboardingCompletionPanel({
  result,
  teamId,
}: OnboardingCompletionPanelProps) {
  const navigate = useNavigate();

  return (
    <EmptyState
      icon={CheckCircleIcon}
      titleText="Application onboarded successfully"
      headingLevel="h2"
      status="success"
    >
      <EmptyStateBody>
        <Content component="p">
          Created {result.namespacesCreated} namespaces, {result.argoCdAppsCreated} ArgoCD
          applications
        </Content>
        <div className="pf-v6-u-mt-sm pf-v6-u-display-flex pf-v6-u-align-items-center pf-v6-u-justify-content-center pf-v6-u-gap-sm">
          {result.promotionChain.map((env, idx) => (
            <span
              key={env}
              className="pf-v6-u-display-flex pf-v6-u-align-items-center pf-v6-u-gap-sm"
            >
              <Label>{env}</Label>
              {idx < result.promotionChain.length - 1 && <ArrowRightIcon />}
            </span>
          ))}
        </div>
      </EmptyStateBody>
      <EmptyStateFooter>
        <EmptyStateActions>
          <Button
            variant="primary"
            onClick={() =>
              navigate(
                `/teams/${teamId}/applications/${result.applicationId}`,
              )
            }
          >
            View {result.applicationName}
          </Button>
        </EmptyStateActions>
        <EmptyStateActions>
          <Button
            variant="link"
            component="a"
            href={result.onboardingPrUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            View onboarding PR ↗
          </Button>
          <Tooltip content="Available after Epic 3">
            <Button variant="link" isDisabled>
              Open in DevSpaces ↗
            </Button>
          </Tooltip>
        </EmptyStateActions>
      </EmptyStateFooter>
    </EmptyState>
  );
}
