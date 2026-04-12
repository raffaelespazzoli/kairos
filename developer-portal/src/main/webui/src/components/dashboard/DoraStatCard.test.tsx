import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DoraStatCard, formatDoraValue } from './DoraStatCard';
import type { DoraMetricDto } from '../../types/dora';

const deployFreqMetric: DoraMetricDto = {
  type: 'DEPLOYMENT_FREQUENCY',
  currentValue: 4.2,
  previousValue: 3.6,
  trend: 'IMPROVING',
  percentageChange: 16.7,
  unit: '/wk',
  timeSeries: [],
};

const leadTimeMetric: DoraMetricDto = {
  type: 'LEAD_TIME',
  currentValue: 2.1,
  previousValue: 2.8,
  trend: 'IMPROVING',
  percentageChange: -25.0,
  unit: 'h',
  timeSeries: [],
};

const cfrMetric: DoraMetricDto = {
  type: 'CHANGE_FAILURE_RATE',
  currentValue: 5.0,
  previousValue: 3.1,
  trend: 'DECLINING',
  percentageChange: 61.3,
  unit: '%',
  timeSeries: [],
};

const mttrMetric: DoraMetricDto = {
  type: 'MTTR',
  currentValue: 45,
  previousValue: 48,
  trend: 'STABLE',
  percentageChange: -6.3,
  unit: 'm',
  timeSeries: [],
};

describe('DoraStatCard', () => {
  it('renders metric name and formatted value', () => {
    render(<DoraStatCard metric={deployFreqMetric} type="DEPLOYMENT_FREQUENCY" />);
    expect(screen.getByText('Deploy Frequency')).toBeInTheDocument();
    expect(screen.getByText('4.2/wk')).toBeInTheDocument();
  });

  it('renders improving trend with green color and correct arrow for higher-is-better', () => {
    render(<DoraStatCard metric={deployFreqMetric} type="DEPLOYMENT_FREQUENCY" />);
    const trendEl = screen.getByText(/\+17% from last month/);
    expect(trendEl).toBeInTheDocument();
    expect(trendEl.style.color).toContain('status--success');
  });

  it('renders improving trend with down arrow for lower-is-better', () => {
    render(<DoraStatCard metric={leadTimeMetric} type="LEAD_TIME" />);
    const card = screen.getByLabelText(/lead time/i);
    expect(card).toBeInTheDocument();
    expect(screen.getByText(/↓/)).toBeInTheDocument();
  });

  it('renders declining trend with red color', () => {
    render(<DoraStatCard metric={cfrMetric} type="CHANGE_FAILURE_RATE" />);
    const trendEl = screen.getByText(/\+61% from last month/);
    expect(trendEl).toBeInTheDocument();
    expect(trendEl.style.color).toContain('status--danger');
  });

  it('renders stable trend with grey color', () => {
    render(<DoraStatCard metric={mttrMetric} type="MTTR" />);
    const trendEl = screen.getByText(/-6% from last month/);
    expect(trendEl).toBeInTheDocument();
    expect(trendEl.style.color).toContain('nonstatus--gray');
  });

  it('renders insufficient data state', () => {
    render(<DoraStatCard metric={null} type="DEPLOYMENT_FREQUENCY" />);
    expect(screen.getByText('—')).toBeInTheDocument();
    expect(screen.getByText('Insufficient data')).toBeInTheDocument();
    expect(screen.getByText('Available after 7 days of activity')).toBeInTheDocument();
  });

  it('has correct aria-label for deploy frequency', () => {
    render(<DoraStatCard metric={deployFreqMetric} type="DEPLOYMENT_FREQUENCY" />);
    const card = screen.getByLabelText(/deploy frequency, 4.2 per week, improving/i);
    expect(card).toBeInTheDocument();
  });

  it('has correct aria-label for lead time', () => {
    render(<DoraStatCard metric={leadTimeMetric} type="LEAD_TIME" />);
    const card = screen.getByLabelText(/lead time, 2.1 hours, improving/i);
    expect(card).toBeInTheDocument();
  });

  it('has correct aria-label for insufficient data', () => {
    render(<DoraStatCard metric={null} type="CHANGE_FAILURE_RATE" />);
    const card = screen.getByLabelText(/change failure rate, insufficient data/i);
    expect(card).toBeInTheDocument();
  });

  it('renders MTTR in minutes', () => {
    render(<DoraStatCard metric={mttrMetric} type="MTTR" />);
    expect(screen.getByText('45m')).toBeInTheDocument();
  });
});

describe('formatDoraValue', () => {
  it('formats deployment frequency', () => {
    expect(formatDoraValue(deployFreqMetric)).toBe('4.2/wk');
  });

  it('formats lead time in hours', () => {
    expect(formatDoraValue(leadTimeMetric)).toBe('2.1h');
  });

  it('formats MTTR in minutes', () => {
    expect(formatDoraValue(mttrMetric)).toBe('45m');
  });

  it('formats change failure rate as percentage', () => {
    expect(formatDoraValue(cfrMetric)).toBe('5.0%');
  });
});
