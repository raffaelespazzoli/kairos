import { Label } from '@patternfly/react-core';
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  InProgressIcon,
  BanIcon,
  PendingIcon,
} from '@patternfly/react-icons';
import type { BuildStatus } from '../../types/build';

const STATUS_CONFIG: Record<BuildStatus, {
  color: 'green' | 'red' | 'blue' | 'grey' | 'yellow';
  icon: React.ReactElement;
  label: string;
}> = {
  Passed: { color: 'green', icon: <CheckCircleIcon />, label: 'Passed' },
  Failed: { color: 'red', icon: <ExclamationCircleIcon />, label: 'Failed' },
  Building: { color: 'blue', icon: <InProgressIcon />, label: 'Building...' },
  Cancelled: { color: 'grey', icon: <BanIcon />, label: 'Cancelled' },
  Pending: { color: 'yellow', icon: <PendingIcon />, label: 'Pending' },
};

interface BuildStatusBadgeProps {
  status: BuildStatus;
}

export function BuildStatusBadge({ status }: BuildStatusBadgeProps) {
  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.Pending;

  return (
    <Label color={config.color} icon={config.icon} isCompact>
      {config.label}
    </Label>
  );
}
