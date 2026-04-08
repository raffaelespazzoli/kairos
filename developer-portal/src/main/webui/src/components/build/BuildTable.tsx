import { useState, useCallback } from 'react';
import { Button, Tooltip } from '@patternfly/react-core';
import {
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
  ExpandableRowContent,
} from '@patternfly/react-table';
import { BuildStatusBadge } from './BuildStatusBadge';
import { FailedBuildDetail } from './FailedBuildDetail';
import { DeepLinkButton } from '../shared/DeepLinkButton';
import type { BuildSummary } from '../../types/build';

interface BuildTableProps {
  builds: BuildSummary[];
  teamId: string;
  appId: string;
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function BuildTable({ builds, teamId, appId }: BuildTableProps) {
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

  const toggleRow = useCallback((buildId: string) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(buildId)) {
        next.delete(buildId);
      } else {
        next.add(buildId);
      }
      return next;
    });
  }, []);

  const columnNames = {
    build: 'Build',
    status: 'Status',
    started: 'Started',
    duration: 'Duration',
    artifact: 'Artifact',
    actions: 'Actions',
  };

  return (
    <Table aria-label="Builds table" variant="compact">
      <Thead>
        <Tr>
          <Th screenReaderText="Row expansion" />
          <Th>{columnNames.build}</Th>
          <Th>{columnNames.status}</Th>
          <Th>{columnNames.started}</Th>
          <Th>{columnNames.duration}</Th>
          <Th>{columnNames.artifact}</Th>
          <Th>{columnNames.actions}</Th>
        </Tr>
      </Thead>
      {builds.map((build, rowIndex) => {
        const isFailed = build.status === 'Failed';
        const isPassed = build.status === 'Passed';
        const isExpanded = expandedRows.has(build.buildId);
        const isExpandable = isFailed;

        return (
          <Tbody key={build.buildId} isExpanded={isExpanded}>
            <Tr
              className={isFailed ? 'pf-v6-u-background-color-danger-100' : undefined}
            >
              <Td
                expand={
                  isExpandable
                    ? {
                        rowIndex,
                        isExpanded,
                        onToggle: () => toggleRow(build.buildId),
                        expandId: `build-expand-${build.buildId}`,
                      }
                    : undefined
                }
              />
              <Td dataLabel={columnNames.build}>{build.buildId}</Td>
              <Td dataLabel={columnNames.status}>
                <BuildStatusBadge status={build.status} />
              </Td>
              <Td dataLabel={columnNames.started}>{formatTime(build.startedAt)}</Td>
              <Td dataLabel={columnNames.duration}>{build.duration ?? '—'}</Td>
              <Td dataLabel={columnNames.artifact}>
                {build.imageReference ? (
                  <code className="pf-v6-u-font-family-mono-vf">{build.imageReference}</code>
                ) : (
                  '—'
                )}
              </Td>
              <Td dataLabel={columnNames.actions} isActionCell>
                {isPassed && (
                  <Tooltip content="Release creation will be available in Story 4.4">
                    <Button variant="secondary" size="sm" isAriaDisabled>
                      Create Release
                    </Button>
                  </Tooltip>
                )}
                <DeepLinkButton href={build.tektonDeepLink} toolName="Tekton" />
              </Td>
            </Tr>
            {isExpandable && (
              <Tr isExpanded={isExpanded}>
                <Td colSpan={7}>
                  <ExpandableRowContent>
                    {isExpanded && (
                      <FailedBuildDetail
                        teamId={teamId}
                        appId={appId}
                        buildId={build.buildId}
                      />
                    )}
                  </ExpandableRowContent>
                </Td>
              </Tr>
            )}
          </Tbody>
        );
      })}
    </Table>
  );
}
