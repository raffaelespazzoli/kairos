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
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        padding: 'var(--pf-t--global--spacer--xl)',
      }}
    >
      <Spinner aria-label="Loading" />
      {showSystemHint && systemName && (
        <Content component="p">Fetching status from {systemName}...</Content>
      )}
    </div>
  );
}
