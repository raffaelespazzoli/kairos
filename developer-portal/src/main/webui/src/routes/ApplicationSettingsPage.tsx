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
} from '@patternfly/react-core';
import { useEnvironments } from '../hooks/useEnvironments';
import { DeepLinkButton } from '../components/shared/DeepLinkButton';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';

export function ApplicationSettingsPage() {
  const { teamId, appId } = useParams();
  const { data, error, isLoading } = useEnvironments(teamId, appId);

  const hasEnvironments = data && data.environments.length > 0;
  const hasVaultLinks = data?.environments.some((env) => env.vaultDeepLink);

  return (
    <>
      <PageSection>
        <Title headingLevel="h2">Settings</Title>
      </PageSection>

      <PageSection>
        <Card>
          <CardTitle>Secrets Management</CardTitle>
          <CardBody>
            {isLoading && <LoadingSpinner systemName="Portal" />}
            {error && <ErrorAlert error={error} />}
            {data && hasEnvironments && !hasVaultLinks && (
              <Alert
                variant="info"
                isInline
                isPlain
                title="Vault URL not configured — contact your platform administrator"
              />
            )}
            {data && hasVaultLinks && (
              <DescriptionList isHorizontal>
                {data.environments.map(
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
