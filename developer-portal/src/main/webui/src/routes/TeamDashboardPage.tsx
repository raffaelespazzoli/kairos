import { PageSection, Title, Content } from '@patternfly/react-core';
import { useAuth } from '../hooks/useAuth';
import { useApplications } from '../contexts/ApplicationsContext';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { NoApplicationsEmptyState } from '../components/shared/NoApplicationsEmptyState';

export function TeamDashboardPage() {
  const { teamName } = useAuth();
  const { applications, isLoading, error } = useApplications();

  if (isLoading) return <LoadingSpinner systemName="Portal" />;
  if (error) return <ErrorAlert error={error} />;

  if (applications.length === 0) {
    return (
      <PageSection>
        <NoApplicationsEmptyState />
      </PageSection>
    );
  }

  return (
    <PageSection>
      <Title headingLevel="h1">{teamName} Dashboard</Title>
      <Content component="p">
        {applications.length} application{applications.length !== 1 ? 's' : ''} onboarded
      </Content>
    </PageSection>
  );
}
