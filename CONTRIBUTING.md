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
- Pull requests to `staging` are **only allowed** if they originate from the `dev` branch of this repository. Pull requests from external forks targeting `staging` will be discarded.
- Pull requests to `master` are **only allowed** if they originate from the `staging` branch of this repository. Pull requests from external forks targeting `master` will be discarded.

## Code Quality & Architecture

We try to keep our codebase clean, responsive, and robust. It's super helpful if you can try to align with our general patterns:

### 1. Modern Kotlin & Compose
- We prefer using **Jetpack Compose** for the UI, styled with **Material Design 3**.
- We're currently targeting **JVM 21**. 

### 2. Unidirectional Data Flow (UDF)
- **State Holders:** We generally expose state from `ViewModel`s using `StateFlow` and collect them in the UI via `collectAsState()`.
- **Flow Combining Quirks:** When combining many flows into a single UI State (like in `BudsViewModel`), consider grouping them into private data classes first (e.g., `ConnectionGroup`, `MediaGroup`). *Kotlin's standard `combine` limits us to 5 flows, so grouping helps us get around this cleanly.*
- **Subscription:** Try to use `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialValue)` when exposing state to help save background resources.
- **Local State:** We typically save `remember { mutableStateOf(...) }` for transient UI states (like active tabs or dialog toggles).

### 3. Fluid Animations
- **Motion matters:** We enjoy using fluid animations! It's nice when new components use Compose APIs like `AnimatedVisibility`, `animateDpAsState`, and `spring()` physics to match the existing dynamic aesthetic. 
- **Side Effects:** Try to handle UI events thoughtfully—`LaunchedEffect` for state-driven side effects and `rememberCoroutineScope()` for callback-driven actions (like button clicks) usually work best.

### 4. Dependency Injection
- We use a lightweight manual DI approach via `ServiceLocator`. 
- We'd prefer to avoid heavy frameworks like Hilt, Dagger, or Koin for now. If you need a dependency, you can usually just inject it through the `ServiceLocator` as seen in the existing ViewModels.

### 5. AI-Assisted Development
- We **encourage** the use of AI tools to help write and review code.
- However, **AI slop will not be tolerated.** Please make sure any generated code is reviewed, fully understood, and generally aligns with the core patterns defined above before opening a PR.

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
