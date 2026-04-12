import { Card, CardTitle, CardBody, Grid, GridItem } from '@patternfly/react-core';
import type { GoldenSignal, GoldenSignalType } from '../../types/health';

type ThresholdColor = 'green' | 'yellow' | 'red' | 'blue';

function getMetricColor(signal: GoldenSignal): ThresholdColor {
  switch (signal.type) {
    case 'LATENCY_P50':
    case 'LATENCY_P95':
    case 'LATENCY_P99': {
      const ms = signal.value * 1000;
      if (ms > 1000) return 'red';
      if (ms >= 500) return 'yellow';
      return 'green';
    }
    case 'ERROR_RATE':
      if (signal.value > 5) return 'red';
      if (signal.value >= 1) return 'yellow';
      return 'green';
    case 'SATURATION_CPU':
    case 'SATURATION_MEMORY':
      if (signal.value > 90) return 'red';
      if (signal.value >= 70) return 'yellow';
      return 'green';
    case 'TRAFFIC_RATE':
      return 'blue';
  }
}

const colorToCssToken: Record<ThresholdColor, string> = {
  green: 'var(--pf-t--global--color--status--success--default)',
  yellow: 'var(--pf-t--global--color--status--warning--default)',
  red: 'var(--pf-t--global--color--status--danger--default)',
  blue: 'var(--pf-t--global--color--brand--default)',
};

export function formatMetricValue(signal: GoldenSignal): string {
  switch (signal.type) {
    case 'LATENCY_P50':
    case 'LATENCY_P95':
    case 'LATENCY_P99':
      return signal.value >= 1
        ? `${signal.value.toFixed(2)}s`
        : `${Math.round(signal.value * 1000)}ms`;
    case 'TRAFFIC_RATE':
      return `${signal.value.toFixed(1)} req/s`;
    case 'ERROR_RATE':
      return `${signal.value.toFixed(1)}%`;
    case 'SATURATION_CPU':
    case 'SATURATION_MEMORY':
      return `${Math.round(signal.value)}%`;
    default:
      return `${signal.value}`;
  }
}

function formatAriaValue(signal: GoldenSignal): string {
  switch (signal.type) {
    case 'LATENCY_P50':
    case 'LATENCY_P95':
    case 'LATENCY_P99':
      return signal.value >= 1
        ? `${signal.value.toFixed(2)} seconds`
        : `${Math.round(signal.value * 1000)} milliseconds`;
    case 'TRAFFIC_RATE':
      return `${signal.value.toFixed(1)} requests per second`;
    case 'ERROR_RATE':
      return `${signal.value.toFixed(1)} percent`;
    case 'SATURATION_CPU':
    case 'SATURATION_MEMORY':
      return `${Math.round(signal.value)} percent`;
    default:
      return `${signal.value}`;
  }
}

function formatMetricAriaLabel(signal: GoldenSignal): string {
  switch (signal.type) {
    case 'LATENCY_P95':
      return `Latency p95, ${formatAriaValue(signal)}`;
    case 'LATENCY_P50':
      return `Latency p50, ${formatAriaValue(signal)}`;
    case 'LATENCY_P99':
      return `Latency p99, ${formatAriaValue(signal)}`;
    case 'TRAFFIC_RATE':
      return `Traffic, ${formatAriaValue(signal)}`;
    case 'ERROR_RATE':
      return `Error rate, ${formatAriaValue(signal)}`;
    case 'SATURATION_CPU':
      return `CPU saturation, ${formatAriaValue(signal)}`;
    case 'SATURATION_MEMORY':
      return `Memory saturation, ${formatAriaValue(signal)}`;
  }
}

function findSignal(signals: GoldenSignal[], type: GoldenSignalType): GoldenSignal | undefined {
  return signals.find((s) => s.type === type);
}

interface MetricCardProps {
  title: string;
  value: string;
  secondaryText?: string;
  color: ThresholdColor;
  ariaLabel: string;
}

function MetricCard({ title, value, secondaryText, color, ariaLabel }: MetricCardProps) {
  return (
    <Card
      style={{ borderTop: `3px solid ${colorToCssToken[color]}` }}
      aria-label={ariaLabel}
    >
      <CardTitle>{title}</CardTitle>
      <CardBody>
        <span style={{ fontSize: 'var(--pf-t--global--font--size--heading--h2)', fontWeight: 'bold' }}>
          {value}
        </span>
        {secondaryText && (
          <div style={{ marginTop: 'var(--pf-t--global--spacer--xs)' }}>
            <span style={{ fontSize: 'var(--pf-t--global--font--size--sm)', color: 'var(--pf-t--global--text--color--subtle)' }}>
              {secondaryText}
            </span>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

interface GoldenSignalsPanelProps {
  signals: GoldenSignal[];
}

export function GoldenSignalsPanel({ signals }: GoldenSignalsPanelProps) {
  const p95 = findSignal(signals, 'LATENCY_P95');
  const p50 = findSignal(signals, 'LATENCY_P50');
  const p99 = findSignal(signals, 'LATENCY_P99');
  const traffic = findSignal(signals, 'TRAFFIC_RATE');
  const errorRate = findSignal(signals, 'ERROR_RATE');
  const cpu = findSignal(signals, 'SATURATION_CPU');
  const memory = findSignal(signals, 'SATURATION_MEMORY');

  const latencyColor = p95 ? getMetricColor(p95) : 'green';
  const trafficColor = traffic ? getMetricColor(traffic) : 'blue';
  const errorColor = errorRate ? getMetricColor(errorRate) : 'green';

  const saturationColor: ThresholdColor = (() => {
    const cpuColor = cpu ? getMetricColor(cpu) : 'green';
    const memColor = memory ? getMetricColor(memory) : 'green';
    const severity: Record<ThresholdColor, number> = { green: 0, blue: 0, yellow: 1, red: 2 };
    return severity[cpuColor] >= severity[memColor] ? cpuColor : memColor;
  })();

  const latencySecondary = [p50, p99]
    .filter((s): s is GoldenSignal => s != null)
    .map((s) => `p${s.type === 'LATENCY_P50' ? '50' : '99'}: ${formatMetricValue(s)}`)
    .join(', ');

  const saturationValue =
    cpu && memory
      ? `CPU ${Math.round(cpu.value)}%, Mem ${Math.round(memory.value)}%`
      : cpu
        ? `CPU ${Math.round(cpu.value)}%`
        : memory
          ? `Mem ${Math.round(memory.value)}%`
          : '—';

  return (
    <Grid hasGutter>
      <GridItem span={3} rowSpan={1} md={6} sm={12}>
        <MetricCard
          title="Latency (p95)"
          value={p95 ? formatMetricValue(p95) : '—'}
          secondaryText={latencySecondary || undefined}
          color={latencyColor}
          ariaLabel={p95 ? formatMetricAriaLabel(p95) : 'Latency p95, no data'}
        />
      </GridItem>
      <GridItem span={3} rowSpan={1} md={6} sm={12}>
        <MetricCard
          title="Traffic"
          value={traffic ? formatMetricValue(traffic) : '—'}
          color={trafficColor}
          ariaLabel={traffic ? formatMetricAriaLabel(traffic) : 'Traffic, no data'}
        />
      </GridItem>
      <GridItem span={3} rowSpan={1} md={6} sm={12}>
        <MetricCard
          title="Errors"
          value={errorRate ? formatMetricValue(errorRate) : '—'}
          color={errorColor}
          ariaLabel={errorRate ? formatMetricAriaLabel(errorRate) : 'Error rate, no data'}
        />
      </GridItem>
      <GridItem span={3} rowSpan={1} md={6} sm={12}>
        <MetricCard
          title="Saturation"
          value={saturationValue}
          color={saturationColor}
          ariaLabel={`Saturation, ${saturationValue}`}
        />
      </GridItem>
    </Grid>
  );
}
