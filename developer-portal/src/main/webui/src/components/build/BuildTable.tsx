import { useState, useCallback } from 'react';
import { Alert, Button, Label } from '@patternfly/react-core';
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
import { CreateReleaseModal } from './CreateReleaseModal';
import { DeepLinkButton } from '../shared/DeepLinkButton';
import { createRelease } from '../../api/releases';
import { fetchBuildDetail } from '../../api/builds';
import { ApiError } from '../../api/client';
import type { BuildSummary } from '../../types/build';

interface BuildTableProps {
  builds: BuildSummary[];
  teamId: string;
  appId: string;
  onReleaseCreated?: () => void;
}

interface ReleaseRowState {
  status: 'idle' | 'creating' | 'released' | 'error';
  version?: string;
  error?: string;
  commitSha?: string | null;
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function BuildTable({ builds, teamId, appId, onReleaseCreated }: BuildTableProps) {
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());
  const [releaseState, setReleaseState] = useState<Map<string, ReleaseRowState>>(new Map());
  const [modalBuild, setModalBuild] = useState<BuildSummary | null>(null);
  const [modalCommitSha, setModalCommitSha] = useState<string | null>(null);

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

  const getReleaseState = (buildId: string): ReleaseRowState =>
    releaseState.get(buildId) ?? { status: 'idle' };

  const handleOpenModal = async (build: BuildSummary) => {
    try {
      const detail = await fetchBuildDetail(teamId, appId, build.buildId);
      setModalCommitSha(detail.commitSha);
    } catch {
      setModalCommitSha(null);
    }
    setModalBuild(build);
  };

  const handleCloseModal = () => {
    setModalBuild(null);
    setModalCommitSha(null);
  };

  const handleCreateRelease = async (version: string) => {
    if (!modalBuild) return;
    const buildId = modalBuild.buildId;

    setReleaseState((prev) => new Map(prev).set(buildId, { status: 'creating' }));

    try {
      await createRelease(teamId, appId, { buildId, version });
      setReleaseState((prev) =>
        new Map(prev).set(buildId, { status: 'released', version }),
      );
      handleCloseModal();
      onReleaseCreated?.();
    } catch (e) {
      const message =
        e instanceof ApiError
          ? e.portalError.message
          : 'An unexpected error occurred';
      setReleaseState((prev) =>
        new Map(prev).set(buildId, { status: 'error', error: message }),
      );
      handleCloseModal();
    }
  };

  const columnNames = {
    build: 'Build',
    status: 'Status',
    started: 'Started',
    duration: 'Duration',
    artifact: 'Artifact',
    actions: 'Actions',
  };

  return (
    <>
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
          const isReleasable = isPassed && build.imageReference != null;
          const isExpanded = expandedRows.has(build.buildId);
          const isExpandable = isFailed;
          const rowRelease = getReleaseState(build.buildId);

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
                  {isReleasable && rowRelease.status === 'released' && (
                    <Label color="green">Released {rowRelease.version}</Label>
                  )}
                  {isReleasable && rowRelease.status !== 'released' && (
                    <Button
                      variant="primary"
                      size="sm"
                      onClick={() => handleOpenModal(build)}
                      isDisabled={rowRelease.status === 'creating'}
                      isLoading={rowRelease.status === 'creating'}
                    >
                      Create Release
                    </Button>
                  )}
                  <DeepLinkButton href={build.tektonDeepLink} toolName="Tekton" />
                </Td>
              </Tr>
              {rowRelease.status === 'error' && (
                <Tr>
                  <Td colSpan={7}>
                    <Alert
                      variant="danger"
                      title={rowRelease.error ?? 'Release creation failed'}
                      isInline
                    />
                  </Td>
                </Tr>
              )}
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

      {modalBuild && (
        <CreateReleaseModal
          isOpen={true}
          build={modalBuild}
          commitSha={modalCommitSha}
          onClose={handleCloseModal}
          onSubmit={handleCreateRelease}
          isSubmitting={getReleaseState(modalBuild.buildId).status === 'creating'}
        />
      )}
    </>
  );
}
