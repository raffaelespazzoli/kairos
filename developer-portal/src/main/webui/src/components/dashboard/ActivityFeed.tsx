import {
  DataList,
  DataListItem,
  DataListItemRow,
  DataListItemCells,
  DataListCell,
  Label,
  Content,
} from '@patternfly/react-core';
import BuildIcon from '@patternfly/react-icons/dist/esm/icons/build-icon';
import RocketIcon from '@patternfly/react-icons/dist/esm/icons/rocket-icon';
import TagIcon from '@patternfly/react-icons/dist/esm/icons/tag-icon';
import { useNavigate, useParams } from 'react-router-dom';
import type { TeamActivityEventDto } from '../../types/dashboard';

const EVENT_ICONS: Record<string, React.ComponentType> = {
  build: BuildIcon,
  deployment: RocketIcon,
  release: TagIcon,
};

const STATUS_LABEL_COLORS: Record<string, 'green' | 'red' | 'blue' | 'gold' | 'grey'> = {
  Passed: 'green',
  Failed: 'red',
  Released: 'blue',
  Deployed: 'green',
  'In Progress': 'gold',
};

export function formatRelativeTime(isoTimestamp: string): string {
  const then = new Date(isoTimestamp).getTime();
  if (Number.isNaN(then)) return isoTimestamp;
  const diffMs = Date.now() - then;
  if (diffMs < 0) return 'just now';
  const diffMinutes = Math.floor(diffMs / 60000);
  if (diffMinutes < 1) return 'just now';
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}d ago`;
}

function getNavigationPath(event: TeamActivityEventDto, teamId: string): string {
  const base = `/teams/${teamId}/apps/${event.applicationId}`;
  switch (event.eventType) {
    case 'build':
    case 'release':
      return `${base}/delivery`;
    case 'deployment':
      return `${base}/overview`;
    default:
      return `${base}/overview`;
  }
}

function buildAriaLabel(event: TeamActivityEventDto, relativeTime: string): string {
  const parts = [event.eventType, event.applicationName, event.status.toLowerCase()];
  if (event.eventType === 'deployment' && event.environmentName) {
    parts.push(`→ ${event.environmentName}`);
  }
  parts.push(relativeTime);
  return parts.join(', ');
}

interface ActivityFeedProps {
  events: TeamActivityEventDto[];
  emptyMessage?: string;
}

export function ActivityFeed({ events, emptyMessage }: ActivityFeedProps) {
  const navigate = useNavigate();
  const { teamId } = useParams();

  if (events.length === 0) {
    return (
      <Content component="p" style={{ color: 'var(--pf-t--global--text--color--subtle)', textAlign: 'center' }}>
        {emptyMessage ?? 'No recent activity across team applications'}
      </Content>
    );
  }

  const handleSelect = (_event: React.MouseEvent | React.KeyboardEvent, id: string) => {
    const index = parseInt(id.split('-').pop() ?? '', 10);
    const eventItem = events[index];
    if (eventItem && teamId) {
      navigate(getNavigationPath(eventItem, teamId));
    }
  };

  return (
    <DataList aria-label="Recent activity" onSelectDataListItem={handleSelect} isCompact>
      {events.map((event, index) => {
        const Icon = EVENT_ICONS[event.eventType] ?? BuildIcon;
        const relativeTime = formatRelativeTime(event.timestamp);
        const itemId = `activity-${event.eventType}-${event.applicationId}-${index}`;
        const ariaLabel = buildAriaLabel(event, relativeTime);
        const labelColor = STATUS_LABEL_COLORS[event.status] ?? 'grey';

        return (
          <DataListItem
            key={itemId}
            id={itemId}
            aria-label={ariaLabel}
          >
            <DataListItemRow>
              <DataListItemCells
                dataListCells={[
                  <DataListCell key="icon" isFilled={false} style={{ flex: '0 0 auto' }}>
                    <Icon />
                  </DataListCell>,
                  <DataListCell key="details">
                    <span style={{ fontWeight: 'var(--pf-t--global--font--weight--body--bold)' }}>
                      {event.applicationName}
                    </span>
                    {' '}
                    <span>{event.reference}</span>
                    {event.eventType === 'deployment' && event.environmentName && (
                      <span style={{ color: 'var(--pf-t--global--text--color--subtle)' }}>
                        {' '}→ {event.environmentName}
                      </span>
                    )}
                  </DataListCell>,
                  <DataListCell key="status" isFilled={false} style={{ flex: '0 0 auto' }}>
                    <Label color={labelColor}>{event.status}</Label>
                  </DataListCell>,
                  <DataListCell key="meta" isFilled={false} style={{ flex: '0 0 auto' }}>
                    <span style={{ color: 'var(--pf-t--global--text--color--subtle)', fontSize: 'var(--pf-t--global--font--size--sm)' }}>
                      {event.actor} · {relativeTime}
                    </span>
                  </DataListCell>,
                ]}
              />
            </DataListItemRow>
          </DataListItem>
        );
      })}
    </DataList>
  );
}
