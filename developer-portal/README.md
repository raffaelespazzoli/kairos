# Developer Portal

A Quarkus + React monorepo serving an internal developer portal as a single deployable artifact.

**Stack:** Quarkus 3.34.x | React 18 | TypeScript 5 | PatternFly 6 | Vite 5 | PostgreSQL

## Running in dev mode

```bash
./mvnw quarkus:dev
```

This starts both the Quarkus backend and the Vite frontend dev server (via Quinoa) with hot reload.
Dev Services automatically provisions a PostgreSQL container via Docker/Podman.

- **Application root:** <http://localhost:8080/>
- **Dev UI:** <http://localhost:8080/q/dev/>
- **Health (readiness):** <http://localhost:8080/q/health/ready>
- **Health (liveness):** <http://localhost:8080/q/health/live>

> The OIDC extension will log a warning about the auth server not being available until Keycloak is configured (Story 1.2).

## Packaging

```bash
./mvnw package
```

Produces the fast-jar layout under `target/quarkus-app/`. Quinoa automatically runs `npm install` and `npm run build` during the Maven build — do not run npm separately in CI.

Run the packaged application:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Container image

Build using the generated Dockerfile:

```bash
docker build -f src/main/docker/Dockerfile.jvm -t developer-portal .
docker run -i --rm -p 8080:8080 developer-portal
```

## Running tests

```bash
./mvnw test          # unit tests (Surefire)
./mvnw verify        # unit + integration tests (Failsafe)
```

## Project structure

```
src/main/java/com/portal/   Backend domain packages
src/main/resources/          Config, Flyway migrations, Casbin policies
src/main/webui/              React SPA (managed by Quinoa)
src/main/docker/             Dockerfiles
src/test/                    Test packages mirroring main
```

## Related guides

- [Quarkus](https://quarkus.io/)
- [Quinoa](https://quarkiverse.github.io/quarkiverse-docs/quarkus-quinoa/dev/index.html)
- [PatternFly 6](https://www.patternfly.org/)
