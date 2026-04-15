import { useCallback } from 'react';
import {
  PageSection,
  Title,
  Flex,
  FlexItem,
  Alert,
  Button,
  Card,
  CardHeader,
  CardTitle,
  CardBody,
  Grid,
  GridItem,
  EmptyState,
  EmptyStateBody,
  EmptyStateFooter,
  EmptyStateActions,
} from '@patternfly/react-core';
import { useParams } from 'react-router-dom';
import { useApplications } from '../contexts/ApplicationsContext';
import { useBuilds, useTriggerBuild } from '../hooks/useBuilds';
import { useReleases } from '../hooks/useReleases';
import { useAppActivity } from '../hooks/useDashboard';
import { BuildTable } from '../components/build/BuildTable';
import { ReleaseTable } from '../components/release/ReleaseTable';
import { ActivityFeed } from '../components/dashboard/ActivityFeed';
import { DeepLinkButton } from '../components/shared/DeepLinkButton';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';

export function ApplicationDeliveryPage() {
  const { teamId, appId } = useParams<{ teamId: string; appId: string }>();
  const { applications } = useApplications();
  const {
    data: builds,
    error: buildsError,
    isLoading: buildsLoading,
    refresh: refreshBuilds,
    prepend: prependBuild,
  } = useBuilds(teamId, appId);
  const { trigger, error: triggerError, isTriggering } = useTriggerBuild(teamId, appId);
  const {
    data: releases,
    error: releasesError,
    isLoading: releasesLoading,
    refresh: refreshReleases,
  } = useReleases(teamId, appId);
  const {
    data: activity,
    error: activityError,
    isLoading: activityLoading,
    refresh: refreshActivity,
  } = useAppActivity(teamId, appId);

  const app = applications.find((a) => String(a.id) === appId);

  const refreshAll = useCallback(() => {
    refreshBuilds();
    refreshReleases();
    refreshActivity();
  }, [refreshBuilds, refreshReleases, refreshActivity]);

  const handleTriggerBuild = async () => {
    const newBuild = await trigger();
    if (newBuild) prependBuild(newBuild);
  };

  return (
    <>
      <PageSection>
        <Flex>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">Delivery</Title>
          </FlexItem>
          <FlexItem>
            <RefreshButton onRefresh={refreshAll} isRefreshing={buildsLoading || releasesLoading || activityLoading} />
          </FlexItem>
        </Flex>
      </PageSection>

      <PageSection isFilled>
        <Grid hasGutter>
          <GridItem span={4}>
            <Card aria-label="Builds">
              <CardHeader
                actions={{
                  actions: (
                    <Button
                      variant="primary"
                      size="sm"
                      onClick={handleTriggerBuild}
                      isLoading={isTriggering}
                      isDisabled={isTriggering}
                    >
                      Trigger Build
                    </Button>
                  ),
                  hasNoOffset: true,
                }}
              >
                <CardTitle>Builds</CardTitle>
              </CardHeader>
              <CardBody>
                {triggerError && (
                  <Alert variant="danger" title={triggerError.message} isInline className="pf-v6-u-mb-md" />
                )}
                {buildsLoading && <LoadingSpinner />}
                {buildsError && <ErrorAlert error={buildsError} />}
                {!buildsLoading && !buildsError && builds && builds.length === 0 && (
                  <EmptyState headingLevel="h3" titleText="No builds yet">
                    <EmptyStateBody>
                      Trigger your first build or push code to start a CI pipeline.
                    </EmptyStateBody>
                    <EmptyStateFooter>
                      <EmptyStateActions>
                        <Button
                          variant="primary"
                          onClick={handleTriggerBuild}
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
                {!buildsLoading && !buildsError && builds && builds.length > 0 && teamId && appId && (
                  <BuildTable
                    builds={builds}
                    teamId={teamId}
                    appId={appId}
                    onReleaseCreated={refreshReleases}
                  />
                )}
              </CardBody>
            </Card>
          </GridItem>

          <GridItem span={4}>
            <Card aria-label="Releases">
              <CardHeader>
                <CardTitle>Releases</CardTitle>
              </CardHeader>
              <CardBody>
                {releasesLoading && <LoadingSpinner />}
                {releasesError && <ErrorAlert error={releasesError} />}
                {!releasesLoading && !releasesError && releases && releases.length === 0 && (
                  <EmptyState headingLevel="h3" titleText="No releases yet">
                    <EmptyStateBody>
                      Create a release from a successful build to start deploying.
                    </EmptyStateBody>
                  </EmptyState>
                )}
                {!releasesLoading && !releasesError && releases && releases.length > 0 && (
                  <ReleaseTable releases={releases} />
                )}
              </CardBody>
            </Card>
          </GridItem>

          <GridItem span={4}>
            <Card aria-label="Recent Activity">
              <CardHeader>
                <CardTitle>Recent Activity</CardTitle>
              </CardHeader>
              <CardBody>
                {activityLoading && <LoadingSpinner />}
                {activityError && (
                  <Alert variant="warning" title="Unable to load activity" isInline />
                )}
                {!activityLoading && !activityError && (
                  <ActivityFeed
                    events={activity?.events ?? []}
                    emptyMessage="No recent activity"
                  />
                )}
              </CardBody>
            </Card>
          </GridItem>
        </Grid>
      </PageSection>
    </>
  );
}
