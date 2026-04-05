import { Button } from '@patternfly/react-core';
import { SyncIcon } from '@patternfly/react-icons';

interface RefreshButtonProps {
  onRefresh: () => void;
  isRefreshing: boolean;
  'aria-label'?: string;
}

export function RefreshButton({
  onRefresh,
  isRefreshing,
  'aria-label': ariaLabel = 'Refresh',
}: RefreshButtonProps) {
  return (
    <Button
      variant="plain"
      onClick={onRefresh}
      isDisabled={isRefreshing}
      aria-label={ariaLabel}
    >
      <SyncIcon className={isRefreshing ? 'pf-v6-u-spin' : undefined} />
    </Button>
  );
}
