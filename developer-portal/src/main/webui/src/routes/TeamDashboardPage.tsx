import {
  PageSection,
  Title,
  Content,
  Card,
  CardTitle,
  CardBody,
  Grid,
  GridItem,
  Alert,
  Spinner,
} from '@patternfly/react-core';
import { useParams } from 'react-router-dom';
import { useTeams } from '../contexts/TeamsContext';
import { useDashboard } from '../hooks/useDashboard';
import { ErrorAlert } from '../components/shared/ErrorAlert';
import { NoApplicationsEmptyState } from '../components/shared/NoApplicationsEmptyState';
import { DoraStatCard } from '../components/dashboard/DoraStatCard';
import { ApplicationHealthGrid } from '../components/dashboard/ApplicationHealthGrid';
import { DoraTrendChart } from '../components/dashboard/DoraTrendChart';
import { ActivityFeed } from '../components/dashboard/ActivityFeed';
import type { DoraMetricType } from '../types/dora';

const DORA_METRIC_ORDER: DoraMetricType[] = [
  'DEPLOYMENT_FREQUENCY',
  'LEAD_TIME',
  'CHANGE_FAILURE_RATE',
  'MTTR',
];

export function TeamDashboardPage() {
  const { activeTeam } = useTeams();
  const teamName = activeTeam?.name ?? 'Team';
  const { teamId } = useParams();
  const { data, error, isLoading } = useDashboard(teamId);

  if (error && !data) {
    return <ErrorAlert error={error} />;
  }

  if (data && data.applications.length === 0 && !data.healthError) {
    return (
      <PageSection>
        <NoApplicationsEmptyState />
      </PageSection>
    );
  }

  const doraHasData = data?.dora?.hasData ?? false;
  const doraMetrics = data?.dora?.metrics ?? [];
  const doraError = data?.doraError ?? null;
  const healthError = data?.healthError ?? null;
  const activityError = data?.activityError ?? null;
  const applications = data?.applications ?? [];

  const deploymentFrequencyMetric = doraMetrics.find(
    (m) => m.type === 'DEPLOYMENT_FREQUENCY',
  );

  return (
    <>
      <PageSection>
        <Title headingLevel="h1">{teamName} Dashboard</Title>
      </PageSection>

      <PageSection>
        {doraError && (
          <Alert variant="warning" title="DORA metrics unavailable" isInline style={{ marginBottom: 'var(--pf-t--global--spacer--md)' }}>
            {doraError}
          </Alert>
        )}
        <Grid hasGutter>
          {DORA_METRIC_ORDER.map((type) => (
            <GridItem key={type} span={3} md={3} sm={6}>
              <DoraStatCard
                type={type}
                metric={
                  doraHasData && !doraError
                    ? (doraMetrics.find((m) => m.type === type) ?? null)
                    : null
                }
                isLoading={isLoading}
              />
            </GridItem>
          ))}
        </Grid>
      </PageSection>

      <PageSection>
        {healthError && (
          <Alert variant="warning" title="Health data unavailable" isInline style={{ marginBottom: 'var(--pf-t--global--spacer--md)' }}>
            {healthError}
          </Alert>
        )}
        {isLoading ? (
          <Card>
            <CardBody>
              <div className="pf-v6-u-display-flex pf-v6-u-flex-direction-column pf-v6-u-align-items-center pf-v6-u-p-lg">
                <Spinner aria-label="Loading health grid" size="lg" />
              </div>
            </CardBody>
          </Card>
        ) : (
          <Card>
            <CardBody style={{ padding: 0 }}>
              <ApplicationHealthGrid
                applications={applications}
                deploymentFrequencyMetric={deploymentFrequencyMetric}
              />
            </CardBody>
          </Card>
        )}
      </PageSection>

      <PageSection>
        <Grid hasGutter>
          <GridItem span={6}>
            <Card>
              <CardTitle>DORA Trends</CardTitle>
              <CardBody>
                {isLoading ? (
                  <div className="pf-v6-u-display-flex pf-v6-u-flex-direction-column pf-v6-u-align-items-center pf-v6-u-p-lg">
                    <Spinner aria-label="Loading DORA trends" size="lg" />
                  </div>
                ) : (
                  <>
                    {doraError && (
                      <Alert variant="warning" title="DORA trends unavailable" isInline style={{ marginBottom: 'var(--pf-t--global--spacer--md)' }}>
                        {doraError}
                      </Alert>
                    )}
                    {!doraError && doraHasData && doraMetrics.some((m) => m.timeSeries.length > 0) ? (
                      <DoraTrendChart metrics={doraMetrics} timeRange={data?.dora?.timeRange ?? '30d'} />
                    ) : !doraError ? (
                      <Content component="p" style={{ color: 'var(--pf-t--global--text--color--subtle)', textAlign: 'center' }}>
                        Trend data available after 7 days of activity
                      </Content>
                    ) : null}
                  </>
                )}
              </CardBody>
            </Card>
          </GridItem>
          <GridItem span={6}>
            <Card>
              <CardTitle>Recent Activity</CardTitle>
              <CardBody>
                {isLoading ? (
                  <div className="pf-v6-u-display-flex pf-v6-u-flex-direction-column pf-v6-u-align-items-center pf-v6-u-p-lg">
                    <Spinner aria-label="Loading activity feed" size="lg" />
                  </div>
                ) : (
                  <>
                    {activityError && (
                      <Alert variant="warning" title="Activity data unavailable" isInline style={{ marginBottom: 'var(--pf-t--global--spacer--md)' }}>
                        {activityError}
                      </Alert>
                    )}
                    {!activityError && <ActivityFeed events={data?.recentActivity ?? []} />}
                  </>
                )}
              </CardBody>
            </Card>
          </GridItem>
        </Grid>
      </PageSection>
    </>
  );
}
