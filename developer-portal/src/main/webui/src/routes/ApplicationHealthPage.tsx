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
  Grid,
  GridItem,
} from '@patternfly/react-core';
import { useParams } from 'react-router-dom';
import { useHealth } from '../hooks/useHealth';
import { useDora } from '../hooks/useDora';
import { HealthStatusBadge } from '../components/health/HealthStatusBadge';
import { GoldenSignalsPanel } from '../components/health/GoldenSignalsPanel';
import { DoraStatCard } from '../components/dashboard/DoraStatCard';
import { DoraTrendChart } from '../components/dashboard/DoraTrendChart';
import { DeepLinkButton } from '../components/shared/DeepLinkButton';
import { LoadingSpinner } from '../components/shared/LoadingSpinner';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { RefreshButton } from '../components/shared/RefreshButton';
import type { EnvironmentHealthDto } from '../types/health';
import type { DoraMetricType } from '../types/dora';

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

const DORA_METRIC_ORDER: DoraMetricType[] = [
  'DEPLOYMENT_FREQUENCY',
  'LEAD_TIME',
  'CHANGE_FAILURE_RATE',
  'MTTR',
];

export function ApplicationHealthPage() {
  const { teamId, appId } = useParams();
  const { data, error, isLoading, refresh } = useHealth(teamId, appId);
  const { data: doraData, error: doraError, isLoading: doraLoading } = useDora(teamId, appId);

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

      <Divider />

      <PageSection>
        <Content component="h2">Delivery Metrics (DORA)</Content>

        {doraLoading && <LoadingSpinner systemName="Prometheus" />}

        {doraError && (
          <Alert
            variant="warning"
            title="Delivery metrics unavailable — metrics system is unreachable"
            isInline
            className="pf-v6-u-mt-sm"
          />
        )}

        {doraData && !doraData.hasData && (
          <Grid hasGutter className="pf-v6-u-mt-md">
            {DORA_METRIC_ORDER.map((type) => (
              <GridItem key={type} span={3} md={6} sm={12}>
                <DoraStatCard metric={null} type={type} />
              </GridItem>
            ))}
          </Grid>
        )}

        {doraData && doraData.hasData && (
          <>
            <Grid hasGutter className="pf-v6-u-mt-md">
              {DORA_METRIC_ORDER.map((type) => {
                const metric = doraData.metrics.find((m) => m.type === type) ?? null;
                return (
                  <GridItem key={type} span={3} md={6} sm={12}>
                    <DoraStatCard metric={metric} type={type} />
                  </GridItem>
                );
              })}
            </Grid>
            <div className="pf-v6-u-mt-lg">
              <DoraTrendChart metrics={doraData.metrics} timeRange={doraData.timeRange} />
            </div>
          </>
        )}
      </PageSection>
    </>
  );
}
