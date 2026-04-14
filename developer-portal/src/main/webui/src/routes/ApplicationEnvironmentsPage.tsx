import { useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  PageSection,
  Title,
  Card,
  CardTitle,
  CardBody,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  Alert,
  Flex,
  FlexItem,
} from '@patternfly/react-core';
import { useEnvironments } from '../hooks/useEnvironments';
import { useReleases } from '../hooks/useReleases';
import { useHealth } from '../hooks/useHealth';
import { EnvironmentChain } from '../components/environment/EnvironmentChain';
import { DeepLinkButton } from '../components/shared/DeepLinkButton';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';

export function ApplicationEnvironmentsPage() {
  const { teamId, appId } = useParams();
  const {
    data: envData,
    error: envError,
    isLoading: envLoading,
    refresh: refreshEnv,
  } = useEnvironments(teamId, appId);
  const { data: releases, error: releasesError } = useReleases(teamId, appId);
  const { data: healthData, error: healthError, refresh: refreshHealth } = useHealth(teamId, appId);

  const refreshAll = useCallback(() => {
    refreshEnv();
    refreshHealth();
  }, [refreshEnv, refreshHealth]);

  const hasEnvironments = envData && envData.environments.length > 0;
  const hasVaultLinks = envData?.environments.some((env) => env.vaultDeepLink);

  return (
    <>
      <PageSection>
        <Flex>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">Environments</Title>
          </FlexItem>
          <FlexItem>
            <RefreshButton onRefresh={refreshAll} isRefreshing={envLoading} />
          </FlexItem>
        </Flex>
      </PageSection>

      <PageSection>
        {envLoading && <LoadingSpinner systemName="ArgoCD" />}
        {envError && <ErrorAlert error={envError} />}
        {releasesError && <ErrorAlert error={releasesError} />}
        {healthError && (
          <Alert variant="warning" title="Health data unavailable — Prometheus may be unreachable" isInline />
        )}
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
        <Card>
          <CardTitle>Secrets Management</CardTitle>
          <CardBody>
            {hasEnvironments && !hasVaultLinks && (
              <Alert
                variant="info"
                isInline
                isPlain
                title="Vault URL not configured — contact your platform administrator"
              />
            )}
            {hasVaultLinks && (
              <DescriptionList isHorizontal>
                {envData!.environments.map(
                  (env) =>
                    env.vaultDeepLink && (
                      <DescriptionListGroup key={env.environmentName}>
                        <DescriptionListTerm>{env.environmentName}</DescriptionListTerm>
                        <DescriptionListDescription>
                          <DeepLinkButton
                            href={env.vaultDeepLink}
                            toolName="Vault"
                            label={`${env.environmentName} — Manage secrets in Vault ↗`}
                          />
                        </DescriptionListDescription>
                      </DescriptionListGroup>
                    ),
                )}
              </DescriptionList>
            )}
          </CardBody>
        </Card>
      </PageSection>
    </>
  );
}
