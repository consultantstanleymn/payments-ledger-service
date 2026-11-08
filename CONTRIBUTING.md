# Contributing

- Run `./mvnw -B verify -DskipITs` before each commit. It runs the unit tests, the property tests and Spotless.
- Run `./mvnw -B verify` before each push. It also runs the integration tests, which need a container engine.
- Integration tests need a container API. With rootless podman, start the user socket and export `DOCKER_HOST` (see docs/ONBOARDING.md).
  If Testcontainers does not work on your machine, run the integration tests in CI only. The `integration` job does this on `ubuntu-latest`.
- Format with `./mvnw spotless:apply`.
- Add a Flyway file for each schema change. Never edit an applied migration.
- Keep money as `long` minor units. Keep rules in `Rules` free of I/O.
- Keep commits small. Write the subject in the imperative.
