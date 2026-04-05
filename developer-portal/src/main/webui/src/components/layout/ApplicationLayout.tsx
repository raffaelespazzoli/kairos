import { PageSection } from '@patternfly/react-core';
import { Outlet } from 'react-router-dom';
import { ApplicationTabs } from './ApplicationTabs';

/**
 * Nested layout for application-context routes.
 * Adds the tab bar above the routed content.
 */
export function ApplicationLayout() {
  return (
    <>
      <PageSection variant="default" isWidthLimited padding={{ default: 'noPadding' }}>
        <ApplicationTabs />
      </PageSection>
      <PageSection isFilled>
        <Outlet />
      </PageSection>
    </>
  );
}
