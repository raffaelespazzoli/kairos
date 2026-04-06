## Deferred from: code review of 1-3-casbin-rbac-authorization-layer (2026-04-04)

- `project-context.md` still documents the authorization pipeline in the wrong order (`PermissionFilter` before `TeamContextFilter`); deferred because it is pre-existing docs drift outside this change set.

## Deferred from: code review of 3-2-devspaces-launch-vault-secret-navigation (2026-04-06)

- ~~`OnboardingCompletionPanel.tsx` still navigates `View {application}` to `/teams/${teamId}/applications/${applicationId}`, but the router only defines application pages under `/teams/:teamId/apps/:appId`~~ — **resolved** during 3.2 review: changed to `/apps/`.
