import {
  EmptyState,
  EmptyStateBody,
  EmptyStateFooter,
  EmptyStateActions,
  Button,
} from '@patternfly/react-core';
import { useNavigate, useParams } from 'react-router-dom';

export function NoApplicationsEmptyState() {
  const navigate = useNavigate();
  const { teamId } = useParams();

  return (
    <EmptyState titleText="No applications onboarded yet" headingLevel="h2">
      <EmptyStateBody>
        Your team is recognized — get started by onboarding your first
        application.
      </EmptyStateBody>
      <EmptyStateFooter>
        <EmptyStateActions>
          <Button
            variant="primary"
            onClick={() => navigate(`/teams/${teamId}/onboard`)}
          >
            Onboard Application
          </Button>
        </EmptyStateActions>
      </EmptyStateFooter>
    </EmptyState>
  );
}
