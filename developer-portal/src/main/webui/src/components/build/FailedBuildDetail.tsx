import { useState, useEffect } from 'react';
import {
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
  Button,
  Spinner,
  Alert,
  CodeBlock,
  CodeBlockCode,
} from '@patternfly/react-core';
import { DeepLinkButton } from '../shared/DeepLinkButton';
import { useBuildDetail, useBuildLogs } from '../../hooks/useBuilds';

interface FailedBuildDetailProps {
  teamId: string;
  appId: string;
  buildId: string;
}

export function FailedBuildDetail({ teamId, appId, buildId }: FailedBuildDetailProps) {
  const {
    data: detail,
    error: detailError,
    isLoading: detailLoading,
    load: loadDetail,
  } = useBuildDetail(teamId, appId, buildId);

  const {
    logs,
    error: logsError,
    isLoading: logsLoading,
    load: loadLogs,
  } = useBuildLogs(teamId, appId, buildId);

  const [logsRequested, setLogsRequested] = useState(false);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  if (detailLoading) {
    return <Spinner size="md" aria-label="Loading build details" />;
  }

  if (detailError) {
    return <Alert variant="danger" title={detailError.message} isInline isPlain />;
  }

  if (!detail) return null;

  const handleViewLogs = () => {
    setLogsRequested(true);
    loadLogs();
  };

  const handleRetryLogs = () => {
    loadLogs();
  };

  return (
    <div className="pf-v6-u-p-md">
      <DescriptionList isHorizontal isCompact>
        {detail.failedStageName && (
          <DescriptionListGroup>
            <DescriptionListTerm>Failed Stage</DescriptionListTerm>
            <DescriptionListDescription>{detail.failedStageName}</DescriptionListDescription>
          </DescriptionListGroup>
        )}
        {detail.errorSummary && (
          <DescriptionListGroup>
            <DescriptionListTerm>Error</DescriptionListTerm>
            <DescriptionListDescription>{detail.errorSummary}</DescriptionListDescription>
          </DescriptionListGroup>
        )}
      </DescriptionList>

      <div className="pf-v6-u-mt-md pf-v6-u-display-flex pf-v6-u-gap-sm">
        {!logsRequested && (
          <Button variant="secondary" size="sm" onClick={handleViewLogs}>
            View Logs
          </Button>
        )}
        <DeepLinkButton href={detail.tektonDeepLink} toolName="Tekton" />
      </div>

      {logsRequested && (
        <div className="pf-v6-u-mt-md">
          {logsLoading && <Spinner size="md" aria-label="Loading build logs" />}
          {logsError && (
            <Alert
              variant="danger"
              title={logsError.message}
              isInline
              isPlain
              actionClose={
                <Button variant="link" size="sm" onClick={handleRetryLogs}>
                  Retry
                </Button>
              }
            />
          )}
          {logs !== null && (
            <CodeBlock>
              <CodeBlockCode>{logs}</CodeBlockCode>
            </CodeBlock>
          )}
        </div>
      )}
    </div>
  );
}
