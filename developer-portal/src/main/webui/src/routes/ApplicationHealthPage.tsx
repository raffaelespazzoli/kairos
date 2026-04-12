import {
  PageSection,
  Title,
  Flex,
  FlexItem,
  Alert,
  Content,
  EmptyState,
  EmptyStateBody,
  Divider,
} from '@patternfly/react-core';
import { useParams } from 'react-router-dom';
import { useHealth } from '../hooks/useHealth';
import { HealthStatusBadge } from '../components/health/HealthStatusBadge';
import { GoldenSignalsPanel } from '../components/health/GoldenSignalsPanel';
import { DeepLinkButton } from '../components/shared/DeepLinkButton';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';
import type { EnvironmentHealthDto } from '../types/health';

function EnvironmentHealthSection({ env }: { env: EnvironmentHealthDto }) {
  const hasError = env.error != null;
  const hasHealthStatus = env.healthStatus != null;
  const isNoData = hasHealthStatus && env.healthStatus!.status === 'NO_DATA';

  return (
    <PageSection>
      <Flex
        alignItems={{ default: 'alignItemsCenter' }}
        spaceItems={{ default: 'spaceItemsMd' }}
      >
        <FlexItem>
          <Content component="h3">{env.environmentName}</Content>
        </FlexItem>
        {hasHealthStatus && (
          <FlexItem>
            <HealthStatusBadge status={env.healthStatus!.status} />
          </FlexItem>
        )}
        <FlexItem>
          <DeepLinkButton
            href={env.grafanaDeepLink}
            toolName="Grafana"
            label="View in Grafana ↗"
            ariaLabel={`Open ${env.environmentName} in Grafana`}
          />
        </FlexItem>
      </Flex>

      {hasError && (
        <Alert
          variant="warning"
          title="Health data unavailable"
          isInline
          className="pf-v6-u-mt-sm"
        >
          {env.error}
        </Alert>
      )}

      {!hasError && isNoData && (
        <EmptyState className="pf-v6-u-mt-md">
          <EmptyStateBody>
            Metrics will appear once the application receives traffic
          </EmptyStateBody>
        </EmptyState>
      )}

      {!hasError && hasHealthStatus && !isNoData && (
        <div className="pf-v6-u-mt-md">
          <GoldenSignalsPanel signals={env.healthStatus!.goldenSignals} />
        </div>
      )}
    </PageSection>
  );
}

export function ApplicationHealthPage() {
  const { teamId, appId } = useParams();
  const { data, error, isLoading, refresh } = useHealth(teamId, appId);

  return (
    <>
      <PageSection>
        <Flex alignItems={{ default: 'alignItemsCenter' }}>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">Health</Title>
          </FlexItem>
          <FlexItem>
            <RefreshButton onRefresh={refresh} isRefreshing={isLoading} />
          </FlexItem>
        </Flex>
      </PageSection>

      {isLoading && <LoadingSpinner systemName="Prometheus" />}
      {error && (
        <PageSection>
          <ErrorAlert error={error} />
        </PageSection>
      )}
      {data &&
        data.environments.map((env, index) => (
          <div key={env.environmentName}>
            {index > 0 && <Divider />}
            <EnvironmentHealthSection env={env} />
          </div>
        ))}
    </>
  );
}
