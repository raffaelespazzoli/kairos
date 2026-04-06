import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Page,
  Masthead,
  MastheadMain,
  MastheadBrand,
  MastheadLogo,
  MastheadContent,
  MastheadToggle,
  PageSidebar,
  PageSection,
  PageToggleButton,
} from '@patternfly/react-core';
import { BarsIcon, UserIcon } from '@patternfly/react-icons';
import { Outlet, useParams, useNavigate, useLocation } from 'react-router-dom';
import { LoadingSpinner } from '../shared/LoadingSpinner';
import { useAuth } from '../../hooks/useAuth';
import { useApiFetch } from '../../hooks/useApiFetch';
import { ApplicationsProvider } from '../../contexts/ApplicationsContext';
import { TeamsProvider } from '../../contexts/TeamsContext';
import { Sidebar } from './Sidebar';
import { AppBreadcrumb } from './AppBreadcrumb';
import type { SidebarApp } from './Sidebar';
import type { TeamSummary } from '../../types/team';
import type { ApplicationSummary } from '../../types/application';

const BREAKPOINT_XL = 1200;

export function AppShell() {
  const { username, teamName, teamId: authTeamId } = useAuth();
  const { teamId: routeTeamId } = useParams();
  const [isSidebarOpen, setIsSidebarOpen] = useState(
    () => window.innerWidth >= BREAKPOINT_XL,
  );

  const handleResize = useCallback(() => {
    setIsSidebarOpen(window.innerWidth >= BREAKPOINT_XL);
  }, []);

  useEffect(() => {
    const mq = window.matchMedia(`(min-width: ${BREAKPOINT_XL}px)`);
    mq.addEventListener('change', handleResize);
    return () => mq.removeEventListener('change', handleResize);
  }, [handleResize]);

  const {
    data: teams,
    error: teamsError,
    isLoading: teamsLoading,
  } = useApiFetch<TeamSummary[]>('/api/v1/teams');

  const numericTeamId = useMemo(() => {
    if (!teams || teams.length === 0) return null;
    const identifier = routeTeamId ?? authTeamId;
    if (identifier) {
      const parsed = Number(identifier);
      if (!isNaN(parsed)) {
        const byId = teams.find((t) => t.id === parsed);
        if (byId) return byId.id;
      }
      const byOidc = teams.find((t) => t.oidcGroupId === identifier);
      if (byOidc) return byOidc.id;
    }
    return teams[0].id;
  }, [teams, routeTeamId, authTeamId]);

  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (numericTeamId === null || routeTeamId === undefined) return;
    if (routeTeamId !== String(numericTeamId)) {
      const newPath = location.pathname.replace(
        `/teams/${routeTeamId}`,
        `/teams/${numericTeamId}`,
      );
      navigate(newPath + location.search, { replace: true });
    }
  }, [numericTeamId, routeTeamId, location.pathname, location.search, navigate]);

  const {
    data: applications,
    error: appsError,
    isLoading: appsLoading,
  } = useApiFetch<ApplicationSummary[]>(
    numericTeamId !== null
      ? `/api/v1/teams/${numericTeamId}/applications`
      : null,
  );

  const appList = applications ?? [];

  const sidebarApps: SidebarApp[] = useMemo(
    () => appList.map((app) => ({ id: String(app.id), name: app.name })),
    [appList],
  );

  const activeTeam = useMemo(
    () => (teams ?? []).find((t) => t.id === numericTeamId) ?? null,
    [teams, numericTeamId],
  );

  const teamsContextValue = useMemo(
    () => ({
      teams: teams ?? [],
      activeTeamId: numericTeamId,
      activeTeam,
    }),
    [teams, numericTeamId, activeTeam],
  );

  const appsContextValue = useMemo(
    () => ({
      applications: appList,
      isLoading: teamsLoading || (numericTeamId !== null && appsLoading),
      error: teamsError ?? appsError ?? null,
    }),
    [appList, teamsLoading, numericTeamId, appsLoading, teamsError, appsError],
  );

  const masthead = (
    <Masthead>
      <MastheadToggle>
        <PageToggleButton
          variant="plain"
          aria-label="Global navigation"
          isSidebarOpen={isSidebarOpen}
          onSidebarToggle={() => setIsSidebarOpen((prev) => !prev)}
        >
          <BarsIcon />
        </PageToggleButton>
      </MastheadToggle>
      <MastheadMain>
        <MastheadBrand>
          <MastheadLogo component="a" href="/">
            Developer Portal
          </MastheadLogo>
        </MastheadBrand>
      </MastheadMain>
      <MastheadContent>
        <span className="pf-v6-u-display-flex pf-v6-u-align-items-center pf-v6-u-gap-sm">
          <span className="portal-user-avatar" aria-hidden="true">
            <UserIcon />
          </span>
          <span>{username}</span>
          <span className="pf-v6-u-color-300">|</span>
          <span>{activeTeam?.name ?? teamName}</span>
        </span>
      </MastheadContent>
    </Masthead>
  );

  const sidebar = (
    <PageSidebar isSidebarOpen={isSidebarOpen}>
      <Sidebar
        applications={sidebarApps}
        teams={teams ?? []}
        activeTeamId={numericTeamId}
      />
    </PageSidebar>
  );

  const needsRedirect =
    numericTeamId !== null &&
    routeTeamId !== undefined &&
    routeTeamId !== String(numericTeamId);

  return (
    <TeamsProvider value={teamsContextValue}>
      <ApplicationsProvider value={appsContextValue}>
        <Page
          masthead={masthead}
          sidebar={sidebar}
          style={
            { '--pf-v6-c-page__sidebar--Width--base': '256px' } as React.CSSProperties
          }
        >
          <PageSection variant="default" isWidthLimited>
            <AppBreadcrumb />
          </PageSection>
          <PageSection isFilled>
            {teamsLoading || needsRedirect ? <LoadingSpinner systemName="Portal" /> : <Outlet />}
          </PageSection>
        </Page>
      </ApplicationsProvider>
    </TeamsProvider>
  );
}
