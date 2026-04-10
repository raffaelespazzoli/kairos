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
} from '@patternfly/react-core';
import { useParams } from 'react-router-dom';
import { useApplications } from '../contexts/ApplicationsContext';
import { useEnvironments } from '../hooks/useEnvironments';
import { useReleases } from '../hooks/useReleases';
import { EnvironmentChain } from '../components/environment/EnvironmentChain';
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
            <RefreshButton onRefresh={refreshEnv} isRefreshing={envLoading} />
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
            onDeploymentInitiated={refreshEnv}
          />
        )}
      </PageSection>

      <PageSection>
        <Grid hasGutter>
          <GridItem span={6}>
            <Card>
              <CardTitle>Recent Builds</CardTitle>
              <CardBody>
                <Content component="p">
                  Build history coming in Epic 4.
                </Content>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem span={6}>
            <Card>
              <CardTitle>Activity</CardTitle>
              <CardBody>
                <Content component="p">
                  Activity feed coming in Epic 7.
                </Content>
              </CardBody>
            </Card>
          </GridItem>
        </Grid>
      </PageSection>
    </>
  );
}
