import { useState, useEffect, useCallback } from 'react';
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
import { Outlet } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { Sidebar } from './Sidebar';
import { AppBreadcrumb } from './AppBreadcrumb';

const BREAKPOINT_XL = 1200;

export function AppShell() {
  const { username, teamName } = useAuth();
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
          <UserIcon />
          <span>{username}</span>
          <span style={{ opacity: 0.6 }}>|</span>
          <span>{teamName}</span>
        </span>
      </MastheadContent>
    </Masthead>
  );

  const sidebar = (
    <PageSidebar isSidebarOpen={isSidebarOpen}>
      <Sidebar />
    </PageSidebar>
  );

  return (
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
        <Outlet />
      </PageSection>
    </Page>
  );
}
