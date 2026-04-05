import { Alert, AlertActionLink } from '@patternfly/react-core';
import type { PortalError } from '../../types/error';

interface ErrorAlertProps {
  error: PortalError;
}

export function ErrorAlert({ error }: ErrorAlertProps) {
  return (
    <Alert
      variant="danger"
      title={error.message}
      isInline
      actionLinks={
        error.deepLink ? (
          <AlertActionLink
            component="a"
            href={error.deepLink}
            target="_blank"
            rel="noopener noreferrer"
          >
            Open in {error.system ?? 'tool'} ↗
          </AlertActionLink>
        ) : undefined
      }
    >
      {error.detail && <p>{error.detail}</p>}
    </Alert>
  );
}
