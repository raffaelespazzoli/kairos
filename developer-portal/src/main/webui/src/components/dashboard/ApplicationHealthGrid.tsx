import { Tooltip } from '@patternfly/react-core';
import { Table, Thead, Tbody, Tr, Th, Td } from '@patternfly/react-table';
import { ChartArea, ChartGroup } from '@patternfly/react-charts/victory';
import { useNavigate, useParams } from 'react-router-dom';
import type {
  ApplicationHealthSummaryDto,
  DashboardEnvironmentEntryDto,
  EnvironmentDotStatus,
} from '../../types/dashboard';
import type { DoraMetricDto } from '../../types/dora';

const DOT_COLORS: Record<EnvironmentDotStatus, string> = {
  HEALTHY: 'var(--pf-t--global--color--status--success--default)',
  UNHEALTHY: 'var(--pf-t--global--color--status--danger--default)',
  DEPLOYING: 'var(--pf-t--global--color--status--warning--default)',
  NOT_DEPLOYED: 'var(--pf-t--global--color--nonstatus--gray--default)',
  UNKNOWN: 'var(--pf-t--global--color--nonstatus--gray--default)',
};

function formatTimestamp(iso: string | null): string {
  if (!iso) return 'Never';
  return new Date(iso).toLocaleString();
}

function EnvironmentDot({ env }: { env: DashboardEnvironmentEntryDto }) {
  const tooltipContent = (
    <div>
      <div><strong>{env.environmentName}</strong></div>
      <div>Status: {env.status}</div>
      {env.statusDetail && <div>{env.statusDetail}</div>}
      <div>Version: {env.deployedVersion || '—'}</div>
      <div>Last deployed: {formatTimestamp(env.lastDeploymentAt)}</div>
    </div>
  );

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', marginRight: '12px' }}>
      <Tooltip content={tooltipContent}>
        <span
          style={{
            display: 'inline-block',
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: DOT_COLORS[env.status],
          }}
          aria-label={`${env.environmentName}: ${env.status}, ${env.deployedVersion || 'no version'}`}
        />
      </Tooltip>
      <span style={{ fontSize: 'var(--pf-t--global--font--size--xs)', color: 'var(--pf-t--global--text--color--subtle)' }}>
        {env.deployedVersion || '—'}
      </span>
    </span>
  );
}

interface DeploymentSparklineProps {
  deploymentFrequencyMetric: DoraMetricDto | undefined;
  applicationName: string;
}

function DeploymentSparkline({ deploymentFrequencyMetric, applicationName }: DeploymentSparklineProps) {
  if (!deploymentFrequencyMetric || deploymentFrequencyMetric.timeSeries.length === 0) {
    return <span style={{ color: 'var(--pf-t--global--text--color--subtle)' }}>—</span>;
  }

  const data = deploymentFrequencyMetric.timeSeries.map((p) => ({ x: p.timestamp, y: p.value }));
  const totalDeployments = Math.round(
    deploymentFrequencyMetric.timeSeries.reduce((sum, p) => sum + p.value, 0),
  );

  return (
    <div
      style={{ width: '80px', height: '24px' }}
      aria-label={`${totalDeployments} deployments in the last 30 days for ${applicationName}`}
    >
      <ChartGroup height={24} width={80} padding={0}>
        <ChartArea
          data={data}
          style={{ data: { fill: 'var(--pf-t--global--color--brand--default)', fillOpacity: 0.3, stroke: 'var(--pf-t--global--color--brand--default)' } }}
        />
      </ChartGroup>
    </div>
  );
}

interface ApplicationHealthGridProps {
  applications: ApplicationHealthSummaryDto[];
  deploymentFrequencyMetric: DoraMetricDto | undefined;
}

export function ApplicationHealthGrid({ applications, deploymentFrequencyMetric }: ApplicationHealthGridProps) {
  const navigate = useNavigate();
  const { teamId } = useParams();

  if (applications.length === 0) {
    return null;
  }

  function handleRowClick(appId: number) {
    navigate(`/teams/${teamId}/apps/${appId}/overview`);
  }

  return (
    <Table aria-label="Application health grid" variant="compact">
      <Thead>
        <Tr>
          <Th>Application</Th>
          <Th>Environments</Th>
          <Th>Activity</Th>
        </Tr>
      </Thead>
      <Tbody>
        {applications.map((app) => (
          <Tr
            key={app.applicationId}
            isClickable
            onRowClick={() => handleRowClick(app.applicationId)}
          >
            <Td dataLabel="Application">
              <strong>{app.applicationName}</strong>
            </Td>
            <Td dataLabel="Environments">
              {app.environments.map((env) => (
                <EnvironmentDot key={env.environmentName} env={env} />
              ))}
            </Td>
            <Td dataLabel="Activity">
              <DeploymentSparkline
                deploymentFrequencyMetric={deploymentFrequencyMetric}
                applicationName={app.applicationName}
              />
            </Td>
          </Tr>
        ))}
      </Tbody>
    </Table>
  );
}
