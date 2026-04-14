import { Tabs, Tab, TabTitleText } from '@patternfly/react-core';
import { useNavigate, useParams, useLocation } from 'react-router-dom';

const APP_TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'builds', label: 'Builds' },
  { key: 'releases', label: 'Releases' },
  { key: 'environments', label: 'Environments' },
  { key: 'health', label: 'Metrics' },
] as const;

function deriveTabFromPath(pathname: string): string {
  const segments = pathname.split('/').filter(Boolean);
  const appIndex = segments.indexOf('apps');
  if (appIndex >= 0 && appIndex + 2 < segments.length) {
    return segments[appIndex + 2];
  }
  return 'overview';
}

export function ApplicationTabs() {
  const { teamId, appId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const activeTab = deriveTabFromPath(location.pathname);

  const handleTabSelect = (
    _event: React.MouseEvent<HTMLElement>,
    tabKey: string | number,
  ) => {
    navigate(`/teams/${teamId}/apps/${appId}/${tabKey}`);
  };

  return (
    <Tabs
      activeKey={activeTab}
      onSelect={handleTabSelect}
      aria-label="Application tabs"
    >
      {APP_TABS.map((tab) => (
        <Tab
          key={tab.key}
          eventKey={tab.key}
          title={<TabTitleText>{tab.label}</TabTitleText>}
        />
      ))}
    </Tabs>
  );
}
