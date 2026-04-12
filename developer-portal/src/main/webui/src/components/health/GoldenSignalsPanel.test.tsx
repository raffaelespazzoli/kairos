import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { GoldenSignalsPanel, formatMetricValue } from './GoldenSignalsPanel';
import type { GoldenSignal } from '../../types/health';

const healthySignals: GoldenSignal[] = [
  { name: 'Latency p50', value: 0.045, unit: 'seconds', type: 'LATENCY_P50' },
  { name: 'Latency p95', value: 0.245, unit: 'seconds', type: 'LATENCY_P95' },
  { name: 'Latency p99', value: 0.89, unit: 'seconds', type: 'LATENCY_P99' },
  { name: 'Traffic', value: 42.5, unit: 'req/s', type: 'TRAFFIC_RATE' },
  { name: 'Error Rate', value: 0.3, unit: '%', type: 'ERROR_RATE' },
  { name: 'CPU', value: 45, unit: '%', type: 'SATURATION_CPU' },
  { name: 'Memory', value: 62, unit: '%', type: 'SATURATION_MEMORY' },
];

const criticalSignals: GoldenSignal[] = [
  { name: 'Latency p95', value: 1.5, unit: 'seconds', type: 'LATENCY_P95' },
  { name: 'Traffic', value: 10.0, unit: 'req/s', type: 'TRAFFIC_RATE' },
  { name: 'Error Rate', value: 8.5, unit: '%', type: 'ERROR_RATE' },
  { name: 'CPU', value: 95, unit: '%', type: 'SATURATION_CPU' },
  { name: 'Memory', value: 92, unit: '%', type: 'SATURATION_MEMORY' },
];

const warningSignals: GoldenSignal[] = [
  { name: 'Latency p95', value: 0.7, unit: 'seconds', type: 'LATENCY_P95' },
  { name: 'Traffic', value: 30.0, unit: 'req/s', type: 'TRAFFIC_RATE' },
  { name: 'Error Rate', value: 2.1, unit: '%', type: 'ERROR_RATE' },
  { name: 'CPU', value: 75, unit: '%', type: 'SATURATION_CPU' },
  { name: 'Memory', value: 60, unit: '%', type: 'SATURATION_MEMORY' },
];

describe('GoldenSignalsPanel', () => {
  it('renders four metric cards', () => {
    render(<GoldenSignalsPanel signals={healthySignals} />);
    expect(screen.getByText('Latency (p95)')).toBeInTheDocument();
    expect(screen.getByText('Traffic')).toBeInTheDocument();
    expect(screen.getByText('Errors')).toBeInTheDocument();
    expect(screen.getByText('Saturation')).toBeInTheDocument();
  });

  it('formats latency from seconds to milliseconds', () => {
    render(<GoldenSignalsPanel signals={healthySignals} />);
    expect(screen.getByText('245ms')).toBeInTheDocument();
  });

  it('formats latency above 1s in seconds', () => {
    render(<GoldenSignalsPanel signals={criticalSignals} />);
    expect(screen.getByText('1.50s')).toBeInTheDocument();
  });

  it('formats traffic rate', () => {
    render(<GoldenSignalsPanel signals={healthySignals} />);
    expect(screen.getByText('42.5 req/s')).toBeInTheDocument();
  });

  it('formats error rate as percentage', () => {
    render(<GoldenSignalsPanel signals={healthySignals} />);
    expect(screen.getByText('0.3%')).toBeInTheDocument();
  });

  it('formats saturation showing CPU and memory', () => {
    render(<GoldenSignalsPanel signals={healthySignals} />);
    expect(screen.getByText('CPU 45%, Mem 62%')).toBeInTheDocument();
  });

  it('shows secondary latency text with p50 and p99', () => {
    render(<GoldenSignalsPanel signals={healthySignals} />);
    expect(screen.getByText('p50: 45ms, p99: 890ms')).toBeInTheDocument();
  });

  it('applies green color for healthy metric ranges', () => {
    render(<GoldenSignalsPanel signals={healthySignals} />);
    const latencyCard = screen.getByLabelText('Latency p95, 245 milliseconds');
    expect(latencyCard.style.borderTop).toContain('status--success');
  });

  it('applies red color for critical metric ranges', () => {
    render(<GoldenSignalsPanel signals={criticalSignals} />);
    const errorCard = screen.getByLabelText('Error rate, 8.5 percent');
    expect(errorCard.style.borderTop).toContain('status--danger');
  });

  it('applies yellow color for warning metric ranges', () => {
    render(<GoldenSignalsPanel signals={warningSignals} />);
    const errorCard = screen.getByLabelText('Error rate, 2.1 percent');
    expect(errorCard.style.borderTop).toContain('status--warning');
  });

  it('applies blue color for traffic (informational)', () => {
    render(<GoldenSignalsPanel signals={healthySignals} />);
    const trafficCard = screen.getByLabelText('Traffic, 42.5 requests per second');
    expect(trafficCard.style.borderTop).toContain('brand--default');
  });

  it('handles empty signals array gracefully', () => {
    render(<GoldenSignalsPanel signals={[]} />);
    expect(screen.getByText('Latency (p95)')).toBeInTheDocument();
    expect(screen.getByText('Traffic')).toBeInTheDocument();
    const dashes = screen.getAllByText('—');
    expect(dashes.length).toBeGreaterThanOrEqual(3);
  });
});

describe('formatMetricValue', () => {
  it('converts sub-second latency to milliseconds', () => {
    expect(formatMetricValue({ name: '', value: 0.245, unit: 's', type: 'LATENCY_P95' })).toBe('245ms');
  });

  it('keeps above-second latency in seconds with 2 decimals', () => {
    expect(formatMetricValue({ name: '', value: 1.5, unit: 's', type: 'LATENCY_P95' })).toBe('1.50s');
  });

  it('formats traffic rate', () => {
    expect(formatMetricValue({ name: '', value: 42.5, unit: 'req/s', type: 'TRAFFIC_RATE' })).toBe('42.5 req/s');
  });

  it('formats error rate', () => {
    expect(formatMetricValue({ name: '', value: 0.3, unit: '%', type: 'ERROR_RATE' })).toBe('0.3%');
  });

  it('formats saturation CPU', () => {
    expect(formatMetricValue({ name: '', value: 45.7, unit: '%', type: 'SATURATION_CPU' })).toBe('46%');
  });

  it('formats saturation memory', () => {
    expect(formatMetricValue({ name: '', value: 62.3, unit: '%', type: 'SATURATION_MEMORY' })).toBe('62%');
  });
});
