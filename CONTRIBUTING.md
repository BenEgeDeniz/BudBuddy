# Contributing to Bud Buddy

Thank you for your interest in contributing to Bud Buddy! To ensure a smooth and organized development process, we follow a strict branching strategy.

## Branching Strategy

The repository maintains three primary branches:

1. **`master`** - Stable Version Release Code. This branch contains the production-ready, stable releases of the application.
2. **`staging`** - Release Candidate. This branch is used for testing and preparing the next release before it is pushed to `master`.
3. **`dev`** - Development. This is the active development branch where all new features and bug fixes are integrated.

### Pull Request Policy

The Continuous Integration (CI) pipeline enforces the following rules for pull requests:

- **All general contributions and pull requests MUST be sent to the `dev` branch.**
- Pull requests to `staging` are **only allowed** if they originate from the `dev` branch of this repository. External forks cannot submit pull requests directly to `staging`.
- Pull requests to `master` are **only allowed** if they originate from the `staging` branch of this repository. External forks cannot submit pull requests directly to `master`.

### How to Contribute

1. Fork the repository.
2. Create a new feature branch based on your fork's `dev` branch.
3. Make your changes and commit them.
4. Push your changes to your fork.
5. Open a Pull Request targeting the **`dev`** branch of the main repository.

Pull requests targeting `staging` or `master` directly will be rejected by the CI pipeline.

## Build Artifacts

The CI pipeline automatically builds and uploads APKs as artifacts depending on the branch:

- **`dev` Branch**: Generates and uploads a Debug APK (`app-debug-dev`).
- **`staging` Branch**: Generates and uploads a Debug APK (`app-debug`) and a Staging Signed Release APK (`app-staging-release`).
- **`master` Branch**: Generates and uploads a Production Signed Release APK (`app-release`).

These artifacts can be downloaded from the "Actions" tab in GitHub for testing and distribution purposes.

Thank you for following these guidelines!
