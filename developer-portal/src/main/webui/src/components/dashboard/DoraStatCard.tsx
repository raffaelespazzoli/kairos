import { Card, CardBody } from '@patternfly/react-core';
import type { DoraMetricDto, DoraMetricType, TrendDirection } from '../../types/dora';

const METRIC_DISPLAY_NAMES: Record<DoraMetricType, string> = {
  DEPLOYMENT_FREQUENCY: 'Deploy Frequency',
  LEAD_TIME: 'Lead Time',
  CHANGE_FAILURE_RATE: 'Change Failure Rate',
  MTTR: 'MTTR',
};

const HIGHER_IS_BETTER: Record<DoraMetricType, boolean> = {
  DEPLOYMENT_FREQUENCY: true,
  LEAD_TIME: false,
  CHANGE_FAILURE_RATE: false,
  MTTR: false,
};

const TREND_COLORS: Record<TrendDirection, string> = {
  IMPROVING: 'var(--pf-t--global--color--status--success--default)',
  DECLINING: 'var(--pf-t--global--color--status--danger--default)',
  STABLE: 'var(--pf-t--global--color--nonstatus--gray--default)',
};

function getTrendArrow(metric: DoraMetricDto): string {
  if (metric.trend === 'STABLE') return '—';
  const higherIsBetter = HIGHER_IS_BETTER[metric.type];
  if (metric.trend === 'IMPROVING') {
    return higherIsBetter ? '↑' : '↓';
  }
  return higherIsBetter ? '↓' : '↑';
}

export function formatDoraValue(metric: DoraMetricDto): string {
  switch (metric.type) {
    case 'DEPLOYMENT_FREQUENCY':
      return `${metric.currentValue.toFixed(1)}${metric.unit}`;
    case 'LEAD_TIME':
    case 'MTTR':
      return metric.unit === 'h'
        ? `${metric.currentValue.toFixed(1)}h`
        : `${Math.round(metric.currentValue)}m`;
    case 'CHANGE_FAILURE_RATE':
      return `${metric.currentValue.toFixed(1)}%`;
    default:
      return `${metric.currentValue}`;
  }
}

function formatPercentageChange(metric: DoraMetricDto): string {
  if (metric.previousValue === 0 && metric.currentValue === 0) {
    return 'No change';
  }
  const abs = Math.abs(metric.percentageChange);
  const display = abs > 999 ? '999%+' : `${abs.toFixed(0)}%`;
  const sign = metric.percentageChange >= 0 ? '+' : '-';
  return `${sign}${display} from last month`;
}

function spokenValue(metric: DoraMetricDto): string {
  switch (metric.type) {
    case 'DEPLOYMENT_FREQUENCY':
      return `${metric.currentValue.toFixed(1)} per week`;
    case 'LEAD_TIME':
      return metric.unit === 'h'
        ? `${metric.currentValue.toFixed(1)} hours`
        : `${Math.round(metric.currentValue)} minutes`;
    case 'MTTR':
      return metric.unit === 'h'
        ? `${metric.currentValue.toFixed(1)} hours`
        : `${Math.round(metric.currentValue)} minutes`;
    case 'CHANGE_FAILURE_RATE':
      return `${metric.currentValue.toFixed(1)} percent`;
    default:
      return `${metric.currentValue}`;
  }
}

const TREND_SPOKEN: Record<TrendDirection, string> = {
  IMPROVING: 'improving',
  DECLINING: 'declining',
  STABLE: 'stable',
};

function buildAriaLabel(metric: DoraMetricDto): string {
  const name = METRIC_DISPLAY_NAMES[metric.type].toLowerCase();
  const valueText = spokenValue(metric);
  const trendText = TREND_SPOKEN[metric.trend];

  const abs = Math.abs(metric.percentageChange);
  const direction = metric.percentageChange >= 0 ? 'up' : 'down';
  const changeSpoken = `${direction} ${abs.toFixed(0)} percent from last month`;

  return `${name}, ${valueText}, ${trendText}, ${changeSpoken}`;
}

function buildInsufficientAriaLabel(type: DoraMetricType): string {
  const name = METRIC_DISPLAY_NAMES[type].toLowerCase();
  return `${name}, insufficient data, available after 7 days of activity`;
}

interface DoraStatCardProps {
  metric: DoraMetricDto | null;
  type: DoraMetricType;
  isLoading?: boolean;
}

export function DoraStatCard({ metric, type, isLoading }: DoraStatCardProps) {
  const displayName = METRIC_DISPLAY_NAMES[type];

  if (isLoading) {
    return (
      <Card aria-label={`${displayName} loading`}>
        <CardBody>
          <div style={{ fontSize: 'var(--pf-t--global--font--size--heading--h2)', fontWeight: 'bold' }}>
            ...
          </div>
          <div style={{ color: 'var(--pf-t--global--text--color--subtle)' }}>
            {displayName}
          </div>
        </CardBody>
      </Card>
    );
  }

  const isInsufficient = !metric;

  if (isInsufficient) {
    return (
      <Card aria-label={buildInsufficientAriaLabel(type)}>
        <CardBody>
          <div style={{ fontSize: 'var(--pf-t--global--font--size--heading--h2)', fontWeight: 'bold', color: 'var(--pf-t--global--color--nonstatus--gray--default)' }}>
            —
          </div>
          <div style={{ color: 'var(--pf-t--global--color--nonstatus--gray--default)' }}>
            {displayName}
          </div>
          <div style={{ fontSize: 'var(--pf-t--global--font--size--sm)', color: 'var(--pf-t--global--color--nonstatus--gray--default)', marginTop: 'var(--pf-t--global--spacer--xs)' }}>
            Insufficient data
          </div>
          <div style={{ fontSize: 'var(--pf-t--global--font--size--xs)', color: 'var(--pf-t--global--color--nonstatus--gray--default)', marginTop: 'var(--pf-t--global--spacer--xs)' }}>
            Available after 7 days of activity
          </div>
        </CardBody>
      </Card>
    );
  }

  const arrow = getTrendArrow(metric);
  const trendColor = TREND_COLORS[metric.trend];
  const formattedValue = formatDoraValue(metric);
  const changeText = formatPercentageChange(metric);

  return (
    <Card aria-label={buildAriaLabel(metric)}>
      <CardBody>
        <div style={{ fontSize: 'var(--pf-t--global--font--size--heading--h2)', fontWeight: 'bold' }}>
          {formattedValue}
        </div>
        <div style={{ color: 'var(--pf-t--global--text--color--subtle)' }}>
          {displayName}
        </div>
        <div style={{ fontSize: 'var(--pf-t--global--font--size--sm)', color: trendColor, marginTop: 'var(--pf-t--global--spacer--xs)' }}>
          {arrow} {changeText}
        </div>
      </CardBody>
    </Card>
  );
}
