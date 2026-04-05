import { PageSection, Title } from '@patternfly/react-core';
import { useApiFetch } from '../hooks/useApiFetch';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { RefreshButton } from '../components/shared/RefreshButton';
import { NoApplicationsEmptyState } from '../components/shared/NoApplicationsEmptyState';
import type { TeamSummary } from '../types/team';

export function TeamDashboardPage() {
  const {
    data: teams,
    error,
    isLoading,
    refresh,
  } = useApiFetch<TeamSummary[]>('/api/v1/teams');

  if (isLoading) return <LoadingSpinner systemName="Portal" />;
  if (error) return <ErrorAlert error={error} />;

  return (
    <PageSection>
      <Title headingLevel="h1">
        {teams?.[0]?.name ?? 'Dashboard'}
        <RefreshButton
          onRefresh={refresh}
          isRefreshing={isLoading}
          aria-label="Refresh teams"
        />
      </Title>
      <NoApplicationsEmptyState />
    </PageSection>
  );
}
