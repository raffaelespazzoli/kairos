import {
  PageSection,
  Title,
  Flex,
  FlexItem,
  EmptyState,
  EmptyStateBody,
} from '@patternfly/react-core';
import { useParams } from 'react-router-dom';
import { useReleases } from '../hooks/useReleases';
import { ReleaseTable } from '../components/release/ReleaseTable';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';

export function ApplicationReleasesPage() {
  const { teamId, appId } = useParams();
  const { data: releases, error, isLoading, refresh } = useReleases(teamId, appId);

  return (
    <>
      <PageSection>
        <Flex>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">Releases</Title>
          </FlexItem>
          <FlexItem>
            <RefreshButton onRefresh={refresh} isRefreshing={isLoading} />
          </FlexItem>
        </Flex>
      </PageSection>

      <PageSection isFilled>
        {isLoading && <LoadingSpinner systemName="Git" />}

        {error && <ErrorAlert error={error} />}

        {!isLoading && !error && releases && releases.length === 0 && (
          <EmptyState headingLevel="h3" titleText="No releases yet">
            <EmptyStateBody>
              Create a release from a successful build to start deploying.
            </EmptyStateBody>
          </EmptyState>
        )}

        {!isLoading && !error && releases && releases.length > 0 && (
          <ReleaseTable releases={releases} />
        )}
      </PageSection>
    </>
  );
}
