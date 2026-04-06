import {
  PageSection,
  Title,
  Content,
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  Button,
} from '@patternfly/react-core';
import { useParams } from 'react-router-dom';
import { useApplications } from '../contexts/ApplicationsContext';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';

export function ApplicationOverviewPage() {
  const { appId } = useParams();
  const { applications, isLoading, error } = useApplications();

  if (isLoading) return <LoadingSpinner systemName="Portal" />;
  if (error) return <ErrorAlert error={error} />;

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
    <PageSection>
      <Title headingLevel="h2">{app.name}</Title>
      <DescriptionList isHorizontal>
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
      <Content component="p" className="pf-v6-u-mt-lg">
        Environment chain visualization coming in Story 2.8.
      </Content>
    </PageSection>
  );
}
