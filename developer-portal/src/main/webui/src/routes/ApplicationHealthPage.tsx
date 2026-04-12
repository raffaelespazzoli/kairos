import { useState } from 'react';
import {
  PageSection,
  Title,
  Flex,
  FlexItem,
  Alert,
  Content,
  EmptyState,
  EmptyStateBody,
  Grid,
  GridItem,
  Tabs,
  Tab,
  TabTitleText,
  ExpandableSection,
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

function EnvironmentHealthSection({ env, defaultExpanded }: { env: EnvironmentHealthDto; defaultExpanded: boolean }) {
  const [isExpanded, setIsExpanded] = useState(defaultExpanded);
  const hasError = env.error != null;
  const hasHealthStatus = env.healthStatus != null;
  const isNoData = hasHealthStatus && env.healthStatus!.status === 'NO_DATA';

  const toggleContent = (
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
  );

  return (
    <ExpandableSection
      toggleContent={toggleContent}
      isExpanded={isExpanded}
      onToggle={(_event, expanded) => setIsExpanded(expanded)}
      className="pf-v6-u-mb-md"
    >
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
    </ExpandableSection>
  );
}

const DORA_METRIC_ORDER: DoraMetricType[] = [
  'DEPLOYMENT_FREQUENCY',
  'LEAD_TIME',
  'CHANGE_FAILURE_RATE',
  'MTTR',
];

function ApplicationHealthTab() {
  const { teamId, appId } = useParams();
  const { data, error, isLoading, refresh } = useHealth(teamId, appId);

  return (
    <>
      <PageSection>
        <Flex alignItems={{ default: 'alignItemsCenter' }}>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">Application Health</Title>
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
      {data && (
        <PageSection>
          {data.environments.map((env, index) => (
            <EnvironmentHealthSection
              key={env.environmentName}
              env={env}
              defaultExpanded={index === 0}
            />
          ))}
        </PageSection>
      )}
    </>
  );
}

function DoraMetricsTab({ grafanaLink }: { grafanaLink: string | null }) {
  const { teamId, appId } = useParams();
  const { data: doraData, error: doraError, isLoading: doraLoading } = useDora(teamId, appId);

  return (
    <>
      <PageSection>
        <Flex alignItems={{ default: 'alignItemsCenter' }} spaceItems={{ default: 'spaceItemsMd' }}>
          <FlexItem grow={{ default: 'grow' }}>
            <Title headingLevel="h2">Delivery Metrics (DORA)</Title>
          </FlexItem>
          <FlexItem>
            <DeepLinkButton
              href={grafanaLink}
              toolName="Grafana"
              label="View in Grafana ↗"
              ariaLabel="Open DORA metrics in Grafana"
            />
          </FlexItem>
        </Flex>
      </PageSection>

      {doraLoading && <LoadingSpinner systemName="Prometheus" />}

      {doraError && (
        <PageSection>
          <Alert
            variant="warning"
            title="Delivery metrics unavailable — metrics system is unreachable"
            isInline
          />
        </PageSection>
      )}

      {doraData && !doraData.hasData && (
        <PageSection>
          <Grid hasGutter>
            {DORA_METRIC_ORDER.map((type) => (
              <GridItem key={type} span={3} md={6} sm={12}>
                <DoraStatCard metric={null} type={type} />
              </GridItem>
            ))}
          </Grid>
        </PageSection>
      )}

      {doraData && doraData.hasData && (
        <PageSection>
          <Grid hasGutter>
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
        </PageSection>
      )}
    </>
  );
}

export function ApplicationHealthPage() {
  const [activeTab, setActiveTab] = useState<string | number>('health');
  const { teamId, appId } = useParams();
  const { data: healthData } = useHealth(teamId, appId);

  const grafanaLink = healthData?.environments
    .map((e) => e.grafanaDeepLink)
    .find((link) => link != null) ?? null;

  return (
    <>
      <PageSection variant="default" padding={{ default: 'noPadding' }}>
        <Tabs
          activeKey={activeTab}
          onSelect={(_event, tabKey) => setActiveTab(tabKey)}
          aria-label="Metrics sub-tabs"
          variant="secondary"
        >
          <Tab eventKey="health" title={<TabTitleText>Application Health</TabTitleText>} />
          <Tab eventKey="dora" title={<TabTitleText>DORA Metrics</TabTitleText>} />
        </Tabs>
      </PageSection>

      {activeTab === 'health' && <ApplicationHealthTab />}
      {activeTab === 'dora' && <DoraMetricsTab grafanaLink={grafanaLink} />}
    </>
  );
}
