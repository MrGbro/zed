# Repository Guidelines

## Project Structure & Module Organization
This repository is a Maven multi-module Java 21 project (`pom.xml` at root).

- Core runtime: `gateway-core`, shared utilities in `gateway-common`
- Bootstrap and wiring: `gateway-bootstrap`
- Admin plane: `gateway-admin` (Spring Boot web app)
- Distribution packaging: `gateway-dist`
- Extension families: `gateway-transport/*`, `gateway-proxy/*`, `gateway-observe/*`, `gateway-governance/*`, `gateway-config/*`, `gateway-registry/*`
- Extension pattern: each family separates API module and concrete implementation modules
- Plugin contracts: `gateway-plugin/gateway-plugin-api`
- Architecture constraints: `architecture-tests`
- Docs and operational scripts: `docs/`, `scripts/e2e`, `scripts/perf`

Java sources are under `src/main/java`; tests are under `src/test/java`.

## Build, Test, and Development Commands
- `mvn -q -DskipTests validate`: fast project sanity check.
- `mvn -DskipTests compile`: compile all modules.
- `mvn test`: run full unit/integration test suite (do not skip when validating fixes).
- `mvn -pl gateway-core -am test`: test one module with required dependencies.
- `mvn -pl gateway-admin -DskipTests spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=19080"`: run admin locally.
- `powershell -File scripts/e2e/run-phase2-smoke.ps1`: phase2 smoke flow.
- `powershell -File scripts/perf/run-phase2-gate.ps1`: benchmark gate check (requires `wrk`).

## Coding Style & Naming Conventions
- 4-space indentation, UTF-8, Java 21.
- Packages follow `io.homeey.gateway.<domain>`.
- Types: `PascalCase`; methods/fields: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep API and implementation separated by module (for example `*-api` vs impl modules).
- SPI extensions must register resources in `META-INF/gateway/` using the target factory FQCN.

## Testing Guidelines
- Frameworks: JUnit 5 (`maven-surefire-plugin 3.2.5`), Spring Boot Test, ArchUnit.
- Test classes end with `*Test` (example: `RadixRouteLocatorTest`).
- Add/adjust tests in the same module as behavior changes; update architecture tests for boundary changes.

## Commit & Pull Request Guidelines
- Follow Conventional Commit style seen in history: `feat(gateway): ...`, `chore(git): ...`, `docs: ...`, `build: ...`.
- Keep commits scoped to one concern/module group.
- PRs should include a change summary and affected modules.
- PRs should call out config/protocol impacts (especially SPI keys, Nacos settings, snapshot schema).
- PRs should list exact verification commands and results.
