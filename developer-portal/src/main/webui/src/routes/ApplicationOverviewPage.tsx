import {
  PageSection,
  Title,
  Content,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  Button,
  Grid,
  GridItem,
  Card,
  CardBody,
  CardTitle,
  Flex,
  FlexItem,
  Alert,
  Spinner,
} from '@patternfly/react-core';
import { useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useApplications } from '../contexts/ApplicationsContext';
import { useEnvironments } from '../hooks/useEnvironments';
import { useHealth } from '../hooks/useHealth';
import { useReleases } from '../hooks/useReleases';
import { useBuilds } from '../hooks/useBuilds';
import { useAppActivity } from '../hooks/useDashboard';
import { EnvironmentChain } from '../components/environment/EnvironmentChain';
import { BuildTable } from '../components/build/BuildTable';
import { ActivityFeed } from '../components/dashboard/ActivityFeed';
import { DeepLinkButton } from '../components/shared/DeepLinkButton';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';

export function ApplicationOverviewPage() {
  const { teamId, appId } = useParams();
  const { applications, isLoading: appsLoading, error: appsError } = useApplications();
  const {
    data: envData,
    error: envError,
    isLoading: envLoading,
    refresh: refreshEnv,
  } = useEnvironments(teamId, appId);
  const { data: releases, error: releasesError } = useReleases(teamId, appId);
  const { data: healthData, refresh: refreshHealth } = useHealth(teamId, appId);
  const {
    data: builds,
    error: buildsError,
    isLoading: buildsLoading,
    refresh: refreshBuilds,
  } = useBuilds(teamId, appId);
  const {
    data: activity,
    error: activityError,
    isLoading: activityLoading,
    refresh: refreshActivity,
  } = useAppActivity(teamId, appId);

  const refreshAll = useCallback(() => {
    refreshEnv();
    refreshHealth();
    refreshBuilds();
    refreshActivity();
  }, [refreshEnv, refreshHealth, refreshBuilds, refreshActivity]);

  if (appsLoading) return <LoadingSpinner systemName="Portal" />;
  if (appsError) return <ErrorAlert error={appsError} />;

  const app = applications.find((a) => String(a.id) === appId);

  if (!app) {
    return (
      <ErrorAlert
        error={{
          error: 'not-found',
          message: 'Application not found',
          timestamp: new Date().toISOString(),
        }}
      />
    );
  }

  return (
    <>
      <PageSection>
        <Flex>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">{app.name}</Title>
          </FlexItem>
          {app.devSpacesDeepLink && (
            <FlexItem>
              <DeepLinkButton href={app.devSpacesDeepLink} toolName="DevSpaces" />
            </FlexItem>
          )}
          <FlexItem>
            <RefreshButton onRefresh={refreshAll} isRefreshing={envLoading} />
          </FlexItem>
        </Flex>

        <DescriptionList isHorizontal className="pf-v6-u-mt-md">
          <DescriptionListGroup>
            <DescriptionListTerm>Runtime</DescriptionListTerm>
            <DescriptionListDescription>{app.runtimeType}</DescriptionListDescription>
          </DescriptionListGroup>
          {app.onboardedAt && (
            <DescriptionListGroup>
              <DescriptionListTerm>Onboarded</DescriptionListTerm>
              <DescriptionListDescription>
                {new Date(app.onboardedAt).toLocaleDateString()}
              </DescriptionListDescription>
            </DescriptionListGroup>
          )}
          {app.onboardingPrUrl && (
            <DescriptionListGroup>
              <DescriptionListTerm>Onboarding PR</DescriptionListTerm>
              <DescriptionListDescription>
                <Button
                  variant="link"
                  component="a"
                  href={app.onboardingPrUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  View onboarding PR
                </Button>
              </DescriptionListDescription>
            </DescriptionListGroup>
          )}
        </DescriptionList>
      </PageSection>

      <PageSection>
        {envLoading && <LoadingSpinner systemName="ArgoCD" />}
        {envError && <ErrorAlert error={envError} />}
        {releasesError && <ErrorAlert error={releasesError} />}
        {envData && (
          <EnvironmentChain
            environments={envData.environments}
            argocdError={envData.argocdError}
            teamId={teamId}
            appId={appId}
            releases={releases}
            healthData={healthData}
            onDeploymentInitiated={refreshAll}
          />
        )}
      </PageSection>

      <PageSection>
        <Grid hasGutter>
          <GridItem span={6}>
            <Card>
              <CardTitle>
                <Flex>
                  <FlexItem grow={{ default: 'grow' }}>Recent Builds</FlexItem>
                  <FlexItem>
                    <Button variant="link" component={(props) => <Link {...props} to="builds" />}>
                      View all builds
                    </Button>
                  </FlexItem>
                </Flex>
              </CardTitle>
              <CardBody>
                {buildsLoading && <Spinner size="lg" aria-label="Loading builds" />}
                {buildsError && <Alert variant="warning" title="Could not load builds" isInline />}
                {!buildsLoading && !buildsError && builds && builds.length === 0 && (
                  <Content component="p" style={{ color: 'var(--pf-t--global--text--color--subtle)', textAlign: 'center' }}>
                    No builds yet
                  </Content>
                )}
                {!buildsLoading && !buildsError && builds && builds.length > 0 && teamId && appId && (
                  <BuildTable builds={builds.slice(0, 5)} teamId={teamId} appId={appId} />
                )}
              </CardBody>
            </Card>
          </GridItem>
          <GridItem span={6}>
            <Card>
              <CardTitle>Recent Activity</CardTitle>
              <CardBody>
                {activityLoading && <Spinner size="lg" aria-label="Loading activity" />}
                {activityError && <Alert variant="warning" title="Could not load activity" isInline />}
                {!activityLoading && !activityError && activity?.error && (
                  <Alert variant="warning" title="Some activity sources unavailable" isInline />
                )}
                {!activityLoading && !activityError && activity && (
                  <ActivityFeed events={activity.events} emptyMessage="No recent activity" />
                )}
              </CardBody>
            </Card>
          </GridItem>
        </Grid>
      </PageSection>
    </>
  );
}
