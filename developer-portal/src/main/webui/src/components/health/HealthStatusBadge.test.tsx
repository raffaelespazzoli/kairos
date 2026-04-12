import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HealthStatusBadge } from './HealthStatusBadge';

describe('HealthStatusBadge', () => {
  it('renders HEALTHY with success label and check icon text', () => {
    render(<HealthStatusBadge status="HEALTHY" />);
    expect(screen.getByText('✓ Healthy')).toBeInTheDocument();
  });

  it('renders UNHEALTHY with danger label', () => {
    render(<HealthStatusBadge status="UNHEALTHY" />);
    expect(screen.getByText('✕ Unhealthy')).toBeInTheDocument();
  });

  it('renders DEGRADED with warning label', () => {
    render(<HealthStatusBadge status="DEGRADED" />);
    expect(screen.getByText('⟳ Degraded')).toBeInTheDocument();
  });

  it('renders NO_DATA with grey label', () => {
    render(<HealthStatusBadge status="NO_DATA" />);
    expect(screen.getByText('No Data')).toBeInTheDocument();
  });
});
