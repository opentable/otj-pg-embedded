# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Maven Java library for running PostgreSQL through Testcontainers. Main code lives under `src/main/java/com/opentable/db/postgres`, split into `embedded`, `junit`, and `junit5` packages. Tests mirror the main packages in `src/test/java/com/opentable/db/postgres/embedded`. SQL and Liquibase/Flyway fixtures are in `src/test/resources/db` and `src/test/resources/liqui`. Project metadata and release settings are centralized in `pom.xml`.

## Build, Test, and Development Commands

- `mvn -B package` builds the jar and runs the test suite.
- `mvn test` runs tests only; Docker must be available because tests start PostgreSQL containers.
- `mvn -Dtest=EmbeddedPostgresTest test` runs one test class while iterating.
- `mvn -DskipTests package` compiles and packages without executing tests.

CI creates a Maven wrapper with Maven 3.9.9 and then runs `./mvnw -B package --file pom.xml`; locally, use a compatible Maven installation unless a wrapper has been generated.

## Coding Style & Naming Conventions

Target Java compatibility is Java 11 (`project.build.targetJdk`). Follow the existing Java style: 4-space indentation, clear final/local variable use where helpful, explicit imports, and concise Javadocs for public APIs. Keep packages under `com.opentable.db.postgres`. Class names use `UpperCamelCase`; methods, fields, and local variables use `lowerCamelCase`; constants use `UPPER_SNAKE_CASE`. Preserve the Apache 2.0 license header on Java source files.

## Testing Guidelines

Tests use JUnit 4 and JUnit Jupiter APIs, plus Testcontainers PostgreSQL. Name test classes with the `*Test` suffix and keep fixtures in `src/test/resources`. Add or update tests for behavior changes, especially around container configuration, JDBC URLs, Flyway, Liquibase, and JUnit integrations. Prefer focused test runs during development, then run `mvn test` or `mvn -B package` before opening a PR.

## Commit & Pull Request Guidelines

Recent history uses short imperative or descriptive commit subjects, for example `Correct typo in Flyway section of README`, `closed statements`, and Dependabot-style `Bump group:artifact from x to y`. Keep commits focused and concise; signed commits are recommended but not required.

Pull requests should include a brief summary, the motivation or linked issue, and notes on tests run. Include documentation updates for user-facing API or behavior changes. Avoid adding dependencies unless they are clearly necessary and small in scope.

## Security & Configuration Tips

Do not commit credentials, private registry tokens, or local Docker configuration. PostgreSQL image selection can be affected by `PG_FULL_IMAGE` and `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX`; mention any non-default image assumptions in tests or PR notes.
