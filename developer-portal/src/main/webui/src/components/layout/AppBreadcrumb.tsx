import { Breadcrumb, BreadcrumbItem } from '@patternfly/react-core';
import { Link, useParams, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

function deriveViewFromPath(
  pathname: string,
  hasAppId: boolean,
): string | null {
  const segments = pathname.split('/').filter(Boolean);
  const appIndex = segments.indexOf('apps');
  if (appIndex >= 0 && appIndex + 2 < segments.length) {
    const view = segments[appIndex + 2];
    return view.charAt(0).toUpperCase() + view.slice(1);
  }
  if (hasAppId) return 'Overview';
  return null;
}

export function AppBreadcrumb() {
  const { teamId, appId } = useParams();
  const location = useLocation();
  const { teamName } = useAuth();

  const currentView = deriveViewFromPath(location.pathname, !!appId);

  if (!teamId) return null;

  return (
    <Breadcrumb>
      <BreadcrumbItem>
        <Link to={`/teams/${teamId}`}>{teamName}</Link>
      </BreadcrumbItem>
      {appId && (
        <BreadcrumbItem>
          <Link to={`/teams/${teamId}/apps/${appId}`}>{appId}</Link>
        </BreadcrumbItem>
      )}
      {currentView && (
        <BreadcrumbItem isActive>{currentView}</BreadcrumbItem>
      )}
    </Breadcrumb>
  );
}
