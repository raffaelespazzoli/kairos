import { Label } from '@patternfly/react-core';
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  SyncAltIcon,
  MinusCircleIcon,
} from '@patternfly/react-icons';
import type { HealthStatus } from '../../types/health';

interface HealthStatusBadgeProps {
  status: HealthStatus;
}

function getHealthStatusConfig(status: HealthStatus) {
  switch (status) {
    case 'HEALTHY':
      return {
        labelProps: { status: 'success' as const },
        icon: <CheckCircleIcon />,
        text: '✓ Healthy',
      };
    case 'UNHEALTHY':
      return {
        labelProps: { status: 'danger' as const },
        icon: <ExclamationCircleIcon />,
        text: '✕ Unhealthy',
      };
    case 'DEGRADED':
      return {
        labelProps: { status: 'warning' as const },
        icon: <SyncAltIcon />,
        text: '⟳ Degraded',
      };
    case 'NO_DATA':
      return {
        labelProps: { color: 'grey' as const },
        icon: <MinusCircleIcon />,
        text: 'No Data',
      };
  }
}

export function HealthStatusBadge({ status }: HealthStatusBadgeProps) {
  const config = getHealthStatusConfig(status);
  return (
    <Label {...config.labelProps} icon={config.icon}>
      {config.text}
    </Label>
  );
}
