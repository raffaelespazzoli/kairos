import { Tooltip } from '@patternfly/react-core';
import {
  Table,
  Thead,
  Tbody,
  Tr,
  Th,
  Td,
} from '@patternfly/react-table';
import type { ReleaseSummary } from '../../types/release';

interface ReleaseTableProps {
  releases: ReleaseSummary[];
}

const monoStyle = { fontFamily: 'var(--pf-v6-global--FontFamily--monospace)' };

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }) + ' ' + d.toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function ReleaseTable({ releases }: ReleaseTableProps) {
  return (
    <Table aria-label="Releases table" variant="compact">
      <Thead>
        <Tr>
          <Th>Version</Th>
          <Th>Created</Th>
          <Th>Commit</Th>
          <Th>Image</Th>
        </Tr>
      </Thead>
      <Tbody>
        {releases.map((release) => (
          <Tr key={release.version}>
            <Td dataLabel="Version">
              <strong>{release.version}</strong>
            </Td>
            <Td dataLabel="Created">{formatDateTime(release.createdAt)}</Td>
            <Td dataLabel="Commit">
              <Tooltip content={release.commitSha}>
                <span style={monoStyle}>{release.commitSha.substring(0, 7)}</span>
              </Tooltip>
            </Td>
            <Td dataLabel="Image">
              {release.imageReference ? (
                <span style={monoStyle}>{release.imageReference}</span>
              ) : (
                '—'
              )}
            </Td>
          </Tr>
        ))}
      </Tbody>
    </Table>
  );
}
