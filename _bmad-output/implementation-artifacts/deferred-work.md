## Deferred from: code review of 1-3-casbin-rbac-authorization-layer (2026-04-04)

- `project-context.md` still documents the authorization pipeline in the wrong order (`PermissionFilter` before `TeamContextFilter`); deferred because it is pre-existing docs drift outside this change set.

## Deferred from: code review of 3-2-devspaces-launch-vault-secret-navigation (2026-04-06)

- ~~`OnboardingCompletionPanel.tsx` still navigates `View {application}` to `/teams/${teamId}/applications/${applicationId}`, but the router only defines application pages under `/teams/:teamId/apps/:appId`~~ — **resolved** during 3.2 review: changed to `/apps/`.

## ~~Deferred from: code review of 3-3-deep-links-on-environment-chain (2026-04-06)~~ — RESOLVED

- ~~`DeepLinkService.generateArgoCdLink()` always returns a URL from a required `portal.argocd.url` value, while `application.properties` gives that property a localhost default.~~ **Fixed:** added blank/null guard to `generateArgoCdLink`; now returns `Optional.empty()` when the URL is empty. Note: the `application.properties` default (`http://localhost:8080`) still provides a value when `ARGOCD_URL` is unset, which is correct for dev mode. In production, operators must set `ARGOCD_URL` to a real URL or leave it blank to suppress links.

## Deferred from: code review of 5-2-deployment-status-history (2026-04-10)

- `EnvironmentMapper.merge()` still uses `Collectors.toMap()` without a merge function, so duplicate `EnvironmentStatusDto.environmentName` values would still throw an `IllegalStateException`; deferred because that behavior predates story 5.2 and was not introduced by this change set.

## Deferred from: code review of 6-3-dora-metrics-retrieval-display (2026-04-12)

- DORA queries within `fetchDoraMetric` execute 3 HTTP calls sequentially per metric (current, previous, range). Dev Notes spec'd 12 fully concurrent queries. Functionally correct; performance optimization for future.
- No caching or deduplication for the 12 Prometheus calls per DORA request. Under load or large ranges, this creates high fan-out. Operational optimization for future.
- No OpenAPI/codegen for frontend/backend DTO contract (`dora.ts` hand-maintained). Pre-existing project pattern across all endpoints.
