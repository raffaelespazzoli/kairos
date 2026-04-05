# Story 1.4: Portal Page Shell & Navigation

Status: done

## Story

As a developer,
I want a consistent portal layout with sidebar navigation, breadcrumbs, and team context visible at all times,
So that I always know where I am and can navigate efficiently between my team's resources.

## Acceptance Criteria

1. **AppShell with masthead and sidebar**
   - **Given** a developer is authenticated and the SPA loads
   - **When** the AppShell component renders
   - **Then** a PatternFly Page component is displayed with a persistent masthead at the top
   - **And** the masthead shows the portal logo/name and the developer's avatar with their team name
   - **And** a collapsible vertical sidebar is displayed on the left

2. **Sidebar content layout**
   - **Given** the sidebar is displayed
   - **When** reviewing its content
   - **Then** a team selector appears at the top of the sidebar
   - **And** an application list (initially empty) appears in the middle
   - **And** a "+ Onboard Application" button appears at the bottom of the sidebar

3. **Sidebar expanded at wide viewports**
   - **Given** the sidebar is displayed at a viewport width >= 1200px
   - **When** the user has not interacted with the collapse toggle
   - **Then** the sidebar is expanded showing full labels (256px width)

4. **Sidebar auto-collapses at narrow viewports**
   - **Given** the viewport width is < 1200px
   - **When** the page renders
   - **Then** the sidebar auto-collapses to icon-only mode (64px width)
   - **And** the user can manually toggle the sidebar open/closed

5. **Breadcrumb navigation**
   - **Given** a developer navigates within the portal
   - **When** any page renders
   - **Then** breadcrumbs are displayed below the masthead showing the navigation path (Team → Application → View)
   - **And** each breadcrumb segment is clickable and navigates to that level

6. **Application context tab bar**
   - **Given** the developer is viewing an application context
   - **When** the application page renders
   - **Then** a horizontal tab bar appears below the breadcrumbs with tabs: Overview, Builds, Releases, Environments, Health, Settings
   - **And** the active tab is highlighted with a blue bottom border
   - **And** clicking a tab loads the corresponding view without a full page reload (client-side routing)

7. **Empty state when no applications**
   - **Given** no applications have been onboarded for the developer's team
   - **When** the main content area loads
   - **Then** a PatternFly EmptyState is displayed with the message "No applications onboarded yet"
   - **And** a description reads "Your team is recognized — get started by onboarding your first application."
   - **And** a primary "Onboard Application" button is shown

8. **Tab preservation on app switch**
   - **Given** the developer switches between applications in the sidebar
   - **When** selecting a different application
   - **Then** the current tab selection is preserved (e.g., if viewing Builds for app A, switching to app B shows Builds for app B)
   - **And** breadcrumbs update immediately

9. **Keyboard navigation and accessibility**
   - **Given** all interactive elements in the shell
   - **When** navigating with keyboard only
   - **Then** all elements are reachable via Tab key
   - **And** focus indicators are visible on all interactive elements (PatternFly default)
   - **And** the sidebar, breadcrumbs, and tabs have appropriate ARIA roles and labels

## Tasks / Subtasks

- [x] Task 1: Create AppShell layout component (AC: #1)
  - [x] Create `AppShell.tsx` in `components/layout/` using PatternFly `Page`, `Masthead`, `PageSidebar`, `PageSection`
  - [x] Implement masthead with portal logo/name and user avatar + team name
  - [x] Wire up sidebar open/close state with responsive breakpoint detection
- [x] Task 2: Create Sidebar component (AC: #2, #3, #4)
  - [x] Create `Sidebar.tsx` in `components/layout/`
  - [x] Add team selector (PatternFly dropdown or select) at top
  - [x] Add application list (PatternFly Nav) in the middle — empty initially
  - [x] Add "+ Onboard Application" button at bottom (secondary variant)
  - [x] Implement 256px expanded / 64px icon-only width states
  - [x] Auto-collapse at viewport < 1200px; manual toggle at any width
- [x] Task 3: Create AppBreadcrumb component (AC: #5)
  - [x] Create `AppBreadcrumb.tsx` in `components/layout/`
  - [x] Use PatternFly Breadcrumb with react-router-dom Links
  - [x] Build breadcrumb segments from current route: Team → Application → View
  - [x] Each segment clickable and navigates via client-side routing
- [x] Task 4: Create ApplicationTabs component (AC: #6, #8)
  - [x] Create `ApplicationTabs.tsx` in `components/layout/`
  - [x] Use PatternFly Tabs with 6 tabs: Overview, Builds, Releases, Environments, Health, Settings
  - [x] Sync active tab with URL path (tab ↔ route)
  - [x] Preserve tab selection when switching applications in sidebar
- [x] Task 5: Set up React Router route structure (AC: #6, #8)
  - [x] Define route tree with nested routes for team → application → tab views
  - [x] Create placeholder page components in `routes/`
  - [x] Wire tabs to route navigation (no full page reload)
- [x] Task 6: Create empty state for no applications (AC: #7)
  - [x] Use PatternFly EmptyState with exact copy from acceptance criteria
  - [x] Add primary "Onboard Application" CTA button
- [x] Task 7: Create useAuth hook for user/team context in UI (AC: #1)
  - [x] Create `useAuth.ts` in `hooks/`
  - [x] Provide user display name, team name, and role for masthead and navigation
- [x] Task 8: Accessibility pass (AC: #9)
  - [x] Verify all shell elements keyboard-navigable
  - [x] Verify focus indicators on all interactive elements
  - [x] Add ARIA roles/labels: `<nav>` for sidebar and breadcrumbs, `<main>` for content
  - [x] Verify PatternFly default ARIA is not overridden

## Dev Notes

### PatternFly 6 Page Shell — Component Architecture

```
AppShell.tsx (Page)
├── Masthead
│   ├── MastheadMain → MastheadBrand → MastheadLogo (portal name)
│   └── MastheadContent → user avatar + team name (right-aligned)
├── PageSidebar
│   └── Sidebar.tsx
│       ├── PageSidebarBody (isFilled={false}) → team selector
│       ├── PageSidebarBody (isFilled={true}) → Nav → application list
│       └── PageSidebarBody (isFilled={false}) → "+ Onboard Application" button
├── PageSection (breadcrumbs)
│   └── AppBreadcrumb.tsx
├── PageSection (tabs — only in application context)
│   └── ApplicationTabs.tsx
└── PageSection (main content)
    └── <Outlet /> (React Router nested route content)
```

### AppShell.tsx — Implementation Pattern

```typescript
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
import { BarsIcon } from '@patternfly/react-icons';
import { Outlet } from 'react-router-dom';

function AppShell() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);

  const masthead = (
    <Masthead>
      <MastheadToggle>
        <PageToggleButton
          variant="plain"
          aria-label="Global navigation"
          isSidebarOpen={isSidebarOpen}
          onSidebarToggle={() => setIsSidebarOpen(!isSidebarOpen)}
        >
          <BarsIcon />
        </PageToggleButton>
      </MastheadToggle>
      <MastheadMain>
        <MastheadBrand>
          <MastheadLogo component="a" href="/">Developer Portal</MastheadLogo>
        </MastheadBrand>
      </MastheadMain>
      <MastheadContent>
        {/* User avatar + team name — right side */}
      </MastheadContent>
    </Masthead>
  );

  const sidebar = (
    <PageSidebar isSidebarOpen={isSidebarOpen}>
      <Sidebar />
    </PageSidebar>
  );

  return (
    <Page masthead={masthead} sidebar={sidebar}>
      <PageSection variant="light" isWidthLimited>
        <AppBreadcrumb />
      </PageSection>
      {/* ApplicationTabs rendered conditionally when in app context */}
      <PageSection isFilled>
        <Outlet />
      </PageSection>
    </Page>
  );
}
```

**Key PatternFly 6 components:**
- `Page` — root shell, accepts `masthead` and `sidebar` props
- `Masthead` with sub-components: `MastheadToggle`, `MastheadMain`, `MastheadBrand`, `MastheadLogo`, `MastheadContent`
- `PageSidebar` — `isSidebarOpen` prop controls visibility; use `PageSidebarBody` children with `isFilled` for layout
- `PageToggleButton` — built-in sidebar toggle (pairs with `MastheadToggle`)
- `PageSection` — content areas with variant and fill props

### Sidebar.tsx — Implementation Pattern

```typescript
import {
  PageSidebarBody,
  Nav,
  NavList,
  NavItem,
  Button,
} from '@patternfly/react-core';
import { useNavigate, useLocation } from 'react-router-dom';

function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <>
      <PageSidebarBody isFilled={false}>
        {/* Team selector — simple display for MVP (single team from JWT) */}
        <div className="pf-v6-u-p-md">
          <strong>{teamName}</strong>
        </div>
      </PageSidebarBody>

      <PageSidebarBody isFilled>
        <Nav aria-label="Application navigation">
          <NavList>
            {applications.map(app => (
              <NavItem
                key={app.id}
                isActive={isCurrentApp(app.id)}
                onClick={() => navigateToApp(app.id)}
              >
                {app.name}
              </NavItem>
            ))}
          </NavList>
        </Nav>
      </PageSidebarBody>

      <PageSidebarBody isFilled={false}>
        <div className="pf-v6-u-p-md">
          <Button variant="secondary" isBlock onClick={() => navigate('/onboard')}>
            + Onboard Application
          </Button>
        </div>
      </PageSidebarBody>
    </>
  );
}
```

**Sidebar layout:** Three `PageSidebarBody` sections — team selector (top, non-filled), app nav (middle, filled/scrollable), onboard button (bottom, non-filled).

### Sidebar Responsive Behavior

| Viewport | Sidebar State | Width |
|---|---|---|
| >= 1200px | Expanded (labels visible) | 256px |
| < 1200px | Auto-collapses to icon-only | 64px |
| Any width | Manual toggle via hamburger | Toggles between expanded/collapsed |

Implementation approach:
- Use `window.matchMedia('(min-width: 1200px)')` or PatternFly's breakpoint utilities
- Initialize `isSidebarOpen` based on viewport width
- Listen for resize events to auto-collapse/expand
- Manual toggle overrides auto behavior until next resize

PatternFly breakpoint token: The UX spec references `--pf-v5-global--breakpoint--xl` (1200px). In PF6, check the actual token name — it may be `--pf-t--global--breakpoint--xl` or similar. The numeric value (1200px) is the authoritative requirement.

### AppBreadcrumb.tsx — Implementation Pattern

```typescript
import { Breadcrumb, BreadcrumbItem } from '@patternfly/react-core';
import { Link, useParams, useLocation } from 'react-router-dom';

function AppBreadcrumb() {
  const { teamId, appId } = useParams();
  const location = useLocation();

  // Derive current view from path
  const currentView = deriveViewFromPath(location.pathname);

  return (
    <Breadcrumb>
      <BreadcrumbItem>
        <Link to={`/teams/${teamId}`}>{teamName}</Link>
      </BreadcrumbItem>
      {appId && (
        <BreadcrumbItem>
          <Link to={`/teams/${teamId}/apps/${appId}`}>{appName}</Link>
        </BreadcrumbItem>
      )}
      {currentView && (
        <BreadcrumbItem isActive>{currentView}</BreadcrumbItem>
      )}
    </Breadcrumb>
  );
}
```

**Breadcrumb levels:** Team → Application → Current View (e.g., "Builds", "Releases")
- Use `react-router-dom` `Link` for `component` prop or render prop on `BreadcrumbItem`
- Wrap in `<nav aria-label="Breadcrumb">` (PatternFly Breadcrumb does this by default)

### ApplicationTabs.tsx — Implementation Pattern

```typescript
import { Tabs, Tab, TabTitleText } from '@patternfly/react-core';
import { useNavigate, useParams, useLocation } from 'react-router-dom';

const APP_TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'builds', label: 'Builds' },
  { key: 'releases', label: 'Releases' },
  { key: 'environments', label: 'Environments' },
  { key: 'health', label: 'Health' },
  { key: 'settings', label: 'Settings' },
];

function ApplicationTabs() {
  const { teamId, appId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const activeTab = deriveTabFromPath(location.pathname) || 'overview';

  const handleTabSelect = (_event: any, tabKey: string | number) => {
    navigate(`/teams/${teamId}/apps/${appId}/${tabKey}`);
  };

  return (
    <Tabs activeKey={activeTab} onSelect={handleTabSelect} aria-label="Application tabs">
      {APP_TABS.map(tab => (
        <Tab key={tab.key} eventKey={tab.key} title={<TabTitleText>{tab.label}</TabTitleText>} />
      ))}
    </Tabs>
  );
}
```

**Active tab styling:** PatternFly Tabs component renders the active tab with a blue bottom border by default — no custom CSS needed.

**Tab ↔ Route sync:** The active tab is derived from the URL path, and selecting a tab navigates to the corresponding route. This ensures deep linking works (bookmarkable tabs) and browser back/forward navigates between tabs.

**Tab preservation on app switch:** When the user selects a different app in the sidebar, the navigation function preserves the current tab key:
```typescript
const navigateToApp = (newAppId: string) => {
  const currentTab = deriveTabFromPath(location.pathname) || 'overview';
  navigate(`/teams/${teamId}/apps/${newAppId}/${currentTab}`);
};
```

### React Router — Route Structure

```typescript
// App.tsx route tree
<BrowserRouter>
  <Routes>
    <Route element={<AppShell />}>
      {/* Team-level routes */}
      <Route path="/teams/:teamId" element={<TeamDashboardPage />} />
      <Route path="/teams/:teamId/dashboard" element={<TeamDashboardPage />} />

      {/* Application-level routes (with tabs) */}
      <Route path="/teams/:teamId/apps/:appId" element={<ApplicationLayout />}>
        <Route index element={<ApplicationOverviewPage />} />
        <Route path="overview" element={<ApplicationOverviewPage />} />
        <Route path="builds" element={<ApplicationBuildsPage />} />
        <Route path="releases" element={<ApplicationReleasesPage />} />
        <Route path="environments" element={<ApplicationEnvironmentsPage />} />
        <Route path="health" element={<ApplicationHealthPage />} />
        <Route path="settings" element={<ApplicationSettingsPage />} />
      </Route>

      {/* Onboarding */}
      <Route path="/teams/:teamId/onboard" element={<OnboardingWizardPage />} />

      {/* Admin routes */}
      <Route path="/admin/clusters" element={<AdminClustersPage />} />

      {/* Default redirect */}
      <Route index element={<Navigate to="/teams/default" replace />} />
    </Route>
  </Routes>
</BrowserRouter>
```

**`ApplicationLayout`** wraps the tab bar + `<Outlet />` for tab content. This is separate from `AppShell` — it renders inside AppShell's outlet and adds the tab bar only for application-context routes.

### Route Page Files (Placeholders)

Create these placeholder files in `src/main/webui/src/routes/`:

| File | Purpose |
|---|---|
| `TeamDashboardPage.tsx` | Team portfolio view (Story 7.x) |
| `ApplicationOverviewPage.tsx` | App overview with env chain (Story 2.x) |
| `ApplicationBuildsPage.tsx` | Build & release table (Story 4.x) |
| `ApplicationReleasesPage.tsx` | Release list (Story 4.x) |
| `ApplicationEnvironmentsPage.tsx` | Environment chain detail (Story 2.x) |
| `ApplicationHealthPage.tsx` | Health/metrics (Story 6.x) |
| `ApplicationSettingsPage.tsx` | App settings (future) |
| `OnboardingWizardPage.tsx` | Onboarding wizard (Story 2.x) |
| `AdminClustersPage.tsx` | Admin cluster management (Story 1.7) |

Each placeholder renders a `<PageSection>` with the page title and an empty state or "Coming soon" message.

### useAuth Hook

```typescript
// src/main/webui/src/hooks/useAuth.ts
export function useAuth() {
  // For MVP: extract from OIDC token context or API response
  // The actual token is managed by the OIDC library
  return {
    username: string,      // display name from JWT preferred_username
    teamName: string,      // from JWT team claim
    teamId: string,        // team identifier
    role: 'member' | 'lead' | 'admin',
    isAuthenticated: boolean,
  };
}
```

This hook provides the user/team context for the masthead display and sidebar team selector. The actual OIDC token management (login redirect, token refresh, bearer injection) is handled in Story 1.5's `apiFetch()`.

### Empty State — Exact Copy

```typescript
import { EmptyState, EmptyStateBody, EmptyStateFooter, EmptyStateActions, Button } from '@patternfly/react-core';

<EmptyState titleText="No applications onboarded yet" headingLevel="h2">
  <EmptyStateBody>
    Your team is recognized — get started by onboarding your first application.
  </EmptyStateBody>
  <EmptyStateFooter>
    <EmptyStateActions>
      <Button variant="primary" onClick={() => navigate(`/teams/${teamId}/onboard`)}>
        Onboard Application
      </Button>
    </EmptyStateActions>
  </EmptyStateFooter>
</EmptyState>
```

### Semantic HTML Requirements

| Element | Usage | Notes |
|---|---|---|
| `<nav>` | Sidebar navigation, breadcrumbs | PatternFly Nav and Breadcrumb components render `<nav>` by default |
| `<main>` | Content area | PatternFly Page renders `<main>` for the content region |
| `<h1>` | Page title | One per page — team name or app name |
| `<h2>` | Section headings | Within page content |
| `<button>` | Actions (onboard, toggle) | Not `<a>` for non-navigation actions |
| `<a>` | Navigation links | Breadcrumb items, sidebar nav items |

### Accessibility Checklist

- [ ] All interactive elements reachable via Tab key
- [ ] Visible focus indicators on all interactive elements (PatternFly default — do NOT override)
- [ ] Sidebar: `<nav aria-label="Application navigation">`
- [ ] Breadcrumbs: PatternFly Breadcrumb renders `<nav aria-label="Breadcrumb">` by default
- [ ] Tabs: PatternFly Tabs provides `role="tablist"`, `role="tab"`, keyboard arrow navigation by default
- [ ] Sidebar toggle: `aria-label="Global navigation"` on toggle button
- [ ] `prefers-reduced-motion` respected (PatternFly default)
- [ ] Heading hierarchy: h1 (page) → h2 (sections) — no skipped levels

### CSS / Styling Rules

- **PatternFly CSS custom properties only** — no hardcoded colors, spacing, or font sizes
- PF6 utility classes use `pf-v6-u-*` prefix (e.g., `pf-v6-u-p-md` for padding)
- Sidebar width: 256px expanded, 64px collapsed — may need custom CSS variable or override PatternFly's `--pf-v6-c-page__sidebar--Width`
- Active tab: blue bottom border is PatternFly default — no custom styling
- Masthead accent: single accent color token permitted on logo/nav header per UX spec
- No Tailwind, no CSS modules — PatternFly tokens and utility classes only
- Dark theme deferred for MVP

### What NOT to Build in This Story

- No actual OIDC login flow / redirect (OIDC token assumed available; login UX deferred)
- No `apiFetch()` or API client (Story 1.5)
- No real application data — sidebar app list is empty or uses mock data
- No onboarding wizard (Story 2.x) — button navigates to placeholder
- No environment chain, build tables, or other content views (later epics)
- No team switching (MVP assumes single team from JWT)
- No global search in masthead (future feature)
- No dark theme

### Previous Story Intelligence (Stories 1.1–1.3)

From previous stories:
- **Story 1.1:** Project scaffolded with React 18 + TypeScript 5 + Vite + PatternFly 6. `App.tsx` has minimal `<Page>` wrapper + `<BrowserRouter>`. Frontend dirs: `components/layout/`, `components/shared/`, `routes/`, `hooks/`, `api/`, `types/` exist as placeholders.
- **Story 1.2:** `TeamContext` CDI bean exists on backend with `teamIdentifier`, `role`, `teamId`. GET `/api/v1/teams` returns user's teams. JWT contains `team` and `role` claims.
- **Story 1.3:** `PermissionFilter` + Casbin enforces role-based access on all `/api/v1/` endpoints. `PortalAuthorizationException` returns 403 with standardized JSON.
- **Existing App.tsx** to be **replaced** with the full AppShell + Router setup from this story.

### Files to Create/Modify

**Create:**
- `src/main/webui/src/components/layout/AppShell.tsx`
- `src/main/webui/src/components/layout/Sidebar.tsx`
- `src/main/webui/src/components/layout/AppBreadcrumb.tsx`
- `src/main/webui/src/components/layout/ApplicationTabs.tsx`
- `src/main/webui/src/components/layout/ApplicationLayout.tsx`
- `src/main/webui/src/hooks/useAuth.ts`
- `src/main/webui/src/routes/TeamDashboardPage.tsx` (placeholder)
- `src/main/webui/src/routes/ApplicationOverviewPage.tsx` (placeholder)
- `src/main/webui/src/routes/ApplicationBuildsPage.tsx` (placeholder)
- `src/main/webui/src/routes/ApplicationReleasesPage.tsx` (placeholder)
- `src/main/webui/src/routes/ApplicationEnvironmentsPage.tsx` (placeholder)
- `src/main/webui/src/routes/ApplicationHealthPage.tsx` (placeholder)
- `src/main/webui/src/routes/ApplicationSettingsPage.tsx` (placeholder)
- `src/main/webui/src/routes/OnboardingWizardPage.tsx` (placeholder)
- `src/main/webui/src/routes/AdminClustersPage.tsx` (placeholder)

**Modify:**
- `src/main/webui/src/App.tsx` — replace minimal scaffold with full route tree + AppShell
- `src/main/webui/src/main.tsx` — ensure BrowserRouter wraps the app (may already exist)

### Project Structure Notes

- `components/layout/` holds the persistent shell: AppShell, Sidebar, AppBreadcrumb, ApplicationTabs, ApplicationLayout
- `routes/` holds page-level components that render inside the shell's `<Outlet />`
- `hooks/useAuth.ts` bridges the OIDC identity to React components — provides user/team/role
- `ApplicationLayout` is a nested layout that adds the tab bar for application-scoped routes
- All route placeholders should render something visible (page title + empty state) so navigation is testable

### References

- [Source: planning-artifacts/architecture.md § Frontend Structure] — Component tree, route files, hooks, PatternFly 6 enforcement
- [Source: planning-artifacts/architecture.md § Routing] — React Router v6, Quinoa SPA routing, <500ms navigation target
- [Source: planning-artifacts/ux-design-specification.md § Navigation Layout] — Masthead, sidebar, breadcrumbs, tabs layout and behavior
- [Source: planning-artifacts/ux-design-specification.md § Responsive Design] — Breakpoints (1200px xl), sidebar collapse (256px/64px)
- [Source: planning-artifacts/ux-design-specification.md § Empty States] — "No applications onboarded yet" exact copy
- [Source: planning-artifacts/ux-design-specification.md § Accessibility] — WCAG 2.1 AA, keyboard, ARIA, semantic HTML
- [Source: planning-artifacts/epics.md § Epic 1 / Story 1.4] — Acceptance criteria, story statement
- [Source: implementation-artifacts/1-1-project-scaffolding-monorepo-setup.md] — Existing frontend directory structure, PatternFly 6 setup

## Dev Agent Record

### Agent Model Used

claude-4.6-opus

### Debug Log References

- Fixed jsdom `window.matchMedia` not available in test environment — added polyfill in test-setup.ts
- Fixed multiple "My Team" text matches in tests by scoping queries to specific DOM regions

### Completion Notes List

- Implemented full AppShell with PatternFly 6 Page, Masthead, PageSidebar, PageSection
- Masthead shows portal logo ("Developer Portal"), user avatar, and team name
- Sidebar has three sections: team name (top), application nav (middle, empty for MVP), and "+ Onboard Application" button (bottom)
- Sidebar auto-collapses below 1200px viewport via matchMedia listener; manual toggle at any width
- AppBreadcrumb derives Team → Application → View hierarchy from route params
- ApplicationTabs renders 6 tabs (Overview, Builds, Releases, Environments, Health, Settings) synced with URL path
- ApplicationLayout wraps tabs + Outlet for nested application routes
- Full React Router v6 route tree with nested layouts for team and application contexts
- 9 placeholder page components created in routes/
- NoApplicationsEmptyState uses exact AC copy and navigates to onboard page
- useAuth hook provides MVP hardcoded user/team context (will integrate with OIDC in Story 1.5)
- Accessibility verified: all ARIA roles/labels present, semantic HTML (nav, main, button), keyboard-navigable
- Set up Vitest + React Testing Library + jsdom test infrastructure
- 51 tests across 8 test files — all passing
- Zero ESLint errors, zero TypeScript errors
- Code review: fixed Sidebar to use route teamId (useParams) instead of hardcoded auth value
- Code review: added navigateToApp() with tab preservation for AC #8 readiness
- Code review: team display now uses PatternFly Label with UsersIcon for selector appearance (AC #2)
- Code review: set --pf-v6-c-page__sidebar--Width--base to 256px on Page (AC #3)
- Code review: breadcrumb now defaults to "Overview" on app index route (AC #5)
- 53 tests across 8 test files — all passing after review fixes

### Change Log

- 2026-04-04: Implemented Story 1.4 — Portal Page Shell & Navigation (all 8 tasks complete)
- 2026-04-04: Code review fixes — 5 findings resolved: Sidebar uses route teamId instead of hardcoded auth value; added navigateToApp with tab preservation; team display uses PF Label selector style; sidebar expanded width set to 256px; breadcrumb defaults to "Overview" on app index route

### File List

**Created:**
- developer-portal/src/main/webui/vitest.config.ts
- developer-portal/src/main/webui/src/test-setup.ts
- developer-portal/src/main/webui/src/components/layout/AppShell.tsx
- developer-portal/src/main/webui/src/components/layout/AppShell.test.tsx
- developer-portal/src/main/webui/src/components/layout/Sidebar.tsx
- developer-portal/src/main/webui/src/components/layout/Sidebar.test.tsx
- developer-portal/src/main/webui/src/components/layout/AppBreadcrumb.tsx
- developer-portal/src/main/webui/src/components/layout/AppBreadcrumb.test.tsx
- developer-portal/src/main/webui/src/components/layout/ApplicationTabs.tsx
- developer-portal/src/main/webui/src/components/layout/ApplicationTabs.test.tsx
- developer-portal/src/main/webui/src/components/layout/ApplicationLayout.tsx
- developer-portal/src/main/webui/src/components/layout/Accessibility.test.tsx
- developer-portal/src/main/webui/src/components/shared/NoApplicationsEmptyState.tsx
- developer-portal/src/main/webui/src/components/shared/NoApplicationsEmptyState.test.tsx
- developer-portal/src/main/webui/src/hooks/useAuth.ts
- developer-portal/src/main/webui/src/hooks/useAuth.test.ts
- developer-portal/src/main/webui/src/routes/TeamDashboardPage.tsx
- developer-portal/src/main/webui/src/routes/ApplicationOverviewPage.tsx
- developer-portal/src/main/webui/src/routes/ApplicationBuildsPage.tsx
- developer-portal/src/main/webui/src/routes/ApplicationReleasesPage.tsx
- developer-portal/src/main/webui/src/routes/ApplicationEnvironmentsPage.tsx
- developer-portal/src/main/webui/src/routes/ApplicationHealthPage.tsx
- developer-portal/src/main/webui/src/routes/ApplicationSettingsPage.tsx
- developer-portal/src/main/webui/src/routes/OnboardingWizardPage.tsx
- developer-portal/src/main/webui/src/routes/AdminClustersPage.tsx
- developer-portal/src/main/webui/src/App.test.tsx

**Modified:**
- developer-portal/src/main/webui/src/App.tsx — replaced minimal scaffold with full route tree + AppShell
- developer-portal/src/main/webui/package.json — added test dependencies and test scripts
