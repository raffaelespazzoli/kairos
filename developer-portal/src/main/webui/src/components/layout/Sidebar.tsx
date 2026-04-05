import {
  PageSidebarBody,
  Nav,
  NavList,
  NavItem,
  NavExpandable,
  Button,
  Label,
} from '@patternfly/react-core';
import { UsersIcon, CogIcon } from '@patternfly/react-icons';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

function deriveTabFromPath(pathname: string): string {
  const segments = pathname.split('/').filter(Boolean);
  const appIndex = segments.indexOf('apps');
  if (appIndex >= 0 && appIndex + 2 < segments.length) {
    return segments[appIndex + 2];
  }
  return 'overview';
}

export interface SidebarApp {
  id: string;
  name: string;
}

interface SidebarProps {
  applications?: SidebarApp[];
}

/**
 * Sidebar with team selector, application nav, and onboard CTA.
 * Uses route params for team context so URLs stay consistent.
 */
export function Sidebar({ applications = [] }: SidebarProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { teamId: routeTeamId, appId } = useParams();
  const { teamName, teamId: authTeamId, role } = useAuth();

  const teamId = routeTeamId ?? authTeamId;

  const navigateToApp = (newAppId: string) => {
    const currentTab = deriveTabFromPath(location.pathname);
    navigate(`/teams/${teamId}/apps/${newAppId}/${currentTab}`);
  };

  return (
    <>
      <PageSidebarBody isFilled>
        <div className="pf-v6-u-p-md">
          <Label icon={<UsersIcon />} variant="outline">
            {teamName}
          </Label>
        </div>

        <Nav aria-label="Application navigation">
          <NavList>
            <NavItem
              isActive={location.pathname.startsWith(`/teams/${teamId}`) && !location.pathname.includes('/apps/')}
              onClick={() => navigate(`/teams/${teamId}`)}
            >
              Team Dashboard
            </NavItem>
            {applications.map((app) => (
              <NavItem
                key={app.id}
                isActive={appId === app.id}
                onClick={() => navigateToApp(app.id)}
              >
                {app.name}
              </NavItem>
            ))}
          </NavList>
        </Nav>

        {role === 'admin' && (
          <Nav aria-label="Admin navigation" className="pf-v6-u-mt-lg">
            <NavExpandable
              title="Admin"
              groupId="admin"
              isExpanded={location.pathname.startsWith('/admin')}
            >
              <NavItem
                isActive={location.pathname === '/admin/clusters'}
                onClick={() => navigate('/admin/clusters')}
              >
                <CogIcon /> Clusters
              </NavItem>
            </NavExpandable>
          </Nav>
        )}
      </PageSidebarBody>

      <PageSidebarBody isFilled={false}>
        <div className="pf-v6-u-p-md">
          <Button
            variant="secondary"
            isBlock
            onClick={() => navigate(`/teams/${teamId}/onboard`)}
          >
            + Onboard Application
          </Button>
        </div>
      </PageSidebarBody>
    </>
  );
}
