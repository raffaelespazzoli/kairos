import { Spinner, Content } from '@patternfly/react-core';
import { useEffect, useState } from 'react';

interface LoadingSpinnerProps {
  systemName?: string;
}

export function LoadingSpinner({ systemName }: LoadingSpinnerProps) {
  const [showSystemHint, setShowSystemHint] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setShowSystemHint(true), 3000);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div className="pf-v6-u-display-flex pf-v6-u-flex-direction-column pf-v6-u-align-items-center pf-v6-u-p-xl">
      <Spinner aria-label="Loading" />
      {showSystemHint && systemName && (
        <Content component="p">Fetching status from {systemName}...</Content>
      )}
    </div>
  );
}
