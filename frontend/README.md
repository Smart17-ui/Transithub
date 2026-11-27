# TransitHub Frontend

The online staff workspace. It uses React, TypeScript and Vite, with Mantine UI, TanStack Query and Keycloak OIDC.

- **Work queue** (`/`): registrations by stage (drafts, to assign, in verification, review & decision, closed), with counts and search.
- **Registration page** (`/registrations/:id`): details, representatives, evidence, duplicates, verification and recommendation history, and the next actions. Buttons appear only for actions the server lists in `allowedActions`, so roles and separation of duties are enforced by the backend, not the screen.
- **New registration** (`/registrations/new`): online capture of a draft.

Source layout: `src/pages/` (one file per route), `src/components/commands.tsx` (one dialog per registration command), `src/components/common.tsx` (labels, badges, error messages, staff names), `src/api/` (typed API client).

Offline field capture is intentionally not included yet; the device trial in backlog item #8 must guide that implementation.

## Local setup

1. Start the backend and PostgreSQL using the [backend setup guide](../README.md). The frontend proxies `/api` and `/v3` to `http://localhost:8080`.
2. In Keycloak, configure the `transithub-backoffice` client as a public OIDC client with standard flow and PKCE S256. Add `http://localhost:5173/auth/callback` as a valid redirect URI, `http://localhost:5173/` as a post-logout redirect URI, and `http://localhost:5173` as a web origin. The client ID must be in the backend's `OIDC_CLIENT_IDS` allow-list.
3. Copy `.env.example` to `.env.local` if your issuer or client ID differs from the defaults.
4. Install dependencies and run the app at `http://localhost:5173`:

   ```powershell
   npm.cmd install
   npm.cmd run dev
   ```

5. Sign in with a staff account. The account needs `registration.capture` to create a draft. Other actions appear only when the backend returns them in `allowedActions`.

The OIDC access token is held in `sessionStorage` by the client library for this browser session. This is the direct SPA development flow; the backend-for-frontend cookie session in recommendation R-27 is a later hardening task.

## OpenAPI types

With the backend running, generate TypeScript definitions from its published OpenAPI document:

```powershell
npm.cmd run api:generate
```

This writes `src/api/schema.d.ts`. The API wrapper in this first slice keeps the response types it consumes in `src/api/types.ts`; wire those definitions to the generated schema as the next frontend screens are added.
