# ADR-0018: One repository with `backend/` and `frontend/` folders

**Status:** Accepted (27 November 2026)
**Source:** the user's decision when the first frontend slice was added; ADR-0009 (one PWA), ADR-0012 (solo workflow)

## Context
The first frontend slice was created in a `frontend/` folder inside the Java project. Backend and frontend files were mixed at the repository root. The GitHub repository is named `Transithub`, not after either app, and there is one developer.

## Decision
- One repository (a monorepo) with one folder per app:
  - `backend/` holds the Spring Boot application (`pom.xml`, `mvnw`, `src/`);
  - `frontend/` holds the React + Vite PWA (`package.json`, `src/`).
- Shared material stays at the root: `docs/`, `scripts/`, `.github/`, `README.md`.
- Each app has its own CI workflow (`backend-ci.yml`, `frontend-ci.yml`), and each runs only when its own folder changes. CodeQL scans Java and TypeScript. Dependabot watches Maven in `/backend` and npm in `/frontend`.
- The API contract is the backend's OpenAPI document. The frontend generates its TypeScript types from it.

## Consequences
- One commit can change the API and its screens together, and the docs serve both apps.
- Backend commands now run from `backend/` (`cd backend; .\mvnw verify`).
- Git history follows the moved files; use `git log --follow` for a file's history from before the move.
- Revisit separate repositories if separate teams or release cycles appear.
