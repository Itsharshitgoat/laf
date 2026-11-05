# Contributing

Thank you for your interest in contributing to Lost & Found! This document explains
how to set up the project locally and submit changes.

## Getting started

1. Fork the repository and clone your fork.
2. Install JDK 17 and Android SDK 34 (minSdk 24, targetSdk 34).
3. Place a valid `app/google-services.json` for your Firebase project.
4. Run the unit tests:

   ```bash
   ./gradlew test
   ```

5. Assemble a debug build:

   ```bash
   ./gradlew assembleDebug
   ```

## Branching

- `main` is the default branch; keep it release-ready.
- Create a feature branch from `main` using a descriptive name, e.g.
  `feat/claim-handover` or `fix/search-pagination`.
- Prefer small, focused pull requests (the project follows per-file/per-concern
  commit history).

## Commit messages

- Use the imperative mood and a short subject line (≤ 72 characters).
- Reference related issues when applicable, e.g. `Fix unread badge counter (#42)`.

## Code style

- Java sources follow the conventions already present in `app/src/main/java`.
- Keep business logic in `repositories/` and `utils/`; avoid putting heavy
  logic directly inside activities/fragments.
- Never commit secrets, service-account keys, or `local.properties`.
- Update `SECURITY.md` if your change affects auth, rules, or secrets.

## Pull requests

- Run `./gradlew test` before submitting.
- Fill out the PR template and describe the test plan.
- Add or update tests for non-trivial changes.

## Communication

Open an issue for bugs and feature requests using the provided templates.
For security issues, see `SECURITY.md`.
