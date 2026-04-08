import {
  PageSection,
  Title,
  Button,
  Flex,
  FlexItem,
  Alert,
  EmptyState,
  EmptyStateBody,
  EmptyStateFooter,
  EmptyStateActions,
} from '@patternfly/react-core';
import { useParams } from 'react-router-dom';
import { useApplications } from '../contexts/ApplicationsContext';
import { useBuilds, useTriggerBuild } from '../hooks/useBuilds';
import { BuildTable } from '../components/build/BuildTable';
import { DeepLinkButton } from '../components/shared/DeepLinkButton';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';

export function ApplicationBuildsPage() {
  const { teamId, appId } = useParams();
  const { applications } = useApplications();
  const { data: builds, error, isLoading, refresh, prepend } = useBuilds(teamId, appId);
  const { trigger, error: triggerError, isTriggering } = useTriggerBuild(teamId, appId);

  const app = applications.find((a) => String(a.id) === appId);

  const handleTriggerBuild = async () => {
    const newBuild = await trigger();
    if (newBuild) {
      prepend(newBuild);
    }
  };

  return (
    <>
      <PageSection>
        <Flex>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">Builds</Title>
          </FlexItem>
          <FlexItem>
            <Button
              variant="primary"
              onClick={handleTriggerBuild}
              isLoading={isTriggering}
              isDisabled={isTriggering}
            >
              Trigger Build
            </Button>
          </FlexItem>
          <FlexItem>
            <RefreshButton onRefresh={refresh} isRefreshing={isLoading} />
          </FlexItem>
        </Flex>
      </PageSection>

      <PageSection isFilled>
        {triggerError && (
          <Alert
            variant="danger"
            title={triggerError.message}
            isInline
            className="pf-v6-u-mb-md"
          />
        )}

        {isLoading && <LoadingSpinner />}

        {error && <ErrorAlert error={error} />}

        {!isLoading && !error && builds && builds.length === 0 && (
          <EmptyState headingLevel="h3" titleText="No builds yet">
            <EmptyStateBody>
              Trigger your first build or push code to start a CI pipeline.
            </EmptyStateBody>
            <EmptyStateFooter>
              <EmptyStateActions>
                <Button
                  variant="primary"
                  onClick={handleTriggerBuild}
                  isLoading={isTriggering}
                  isDisabled={isTriggering}
                >
                  Trigger Build
                </Button>
              </EmptyStateActions>
              {app?.devSpacesDeepLink && (
                <EmptyStateActions>
                  <DeepLinkButton href={app.devSpacesDeepLink} toolName="DevSpaces" />
                </EmptyStateActions>
              )}
            </EmptyStateFooter>
          </EmptyState>
        )}

        {!isLoading && !error && builds && builds.length > 0 && teamId && appId && (
          <BuildTable builds={builds} teamId={teamId} appId={appId} />
        )}
      </PageSection>
    </>
  );
}
