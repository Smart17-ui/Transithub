import { UserManager, WebStorageStateStore } from 'oidc-client-ts'

const authority = import.meta.env.VITE_OIDC_AUTHORITY || 'http://localhost:8180/realms/staff'
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID || 'transithub-backoffice'

export const userManager = new UserManager({
  authority,
  client_id: clientId,
  redirect_uri: `${window.location.origin}/auth/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  // Access tokens are short-lived (minutes). Renew them in the background with the
  // refresh token before they expire, so people are not signed out mid-task; the
  // sign-in service still ends the session after its idle and maximum times.
  automaticSilentRenew: true,
  monitorSession: false,
})
