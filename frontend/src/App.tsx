import { useEffect, useRef, useState, type ReactNode } from 'react'
import { AppShell, Button, Container, Group, Paper, Text, Title } from '@mantine/core'
import { Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router'
import type { User } from 'oidc-client-ts'
import { userManager } from './auth/oidc'
import { Logo } from './brand/Logo'
import { CenterMessage, useMe } from './components/common'
import { ColorSchemeToggle } from './components/ColorSchemeToggle'
import { MobileMenu, siteLinks } from './components/MobileMenu'
import { AboutPage } from './pages/AboutPage'
import { ContactPage } from './pages/ContactPage'
import { EnquiriesPage } from './pages/EnquiriesPage'
import { LandingPage } from './pages/LandingPage'
import { ServicesPage } from './pages/ServicesPage'
import { RegisterCompanyPage } from './pages/RegisterCompanyPage'
import { ScrollToLocation } from './components/PublicPage'
import { NewRegistrationPage } from './pages/NewRegistrationPage'
import { QueuePage } from './pages/QueuePage'
import { RegistrationPage } from './pages/RegistrationPage'
import { ApplicationPage } from './pages/ApplicationPage'
import { SignInPage } from './pages/SignInPage'

function App() {
  const [user, setUser] = useState<User | null>(null)
  const [authReady, setAuthReady] = useState(false)
  const [authError, setAuthError] = useState('')

  useEffect(() => {
    if (window.location.pathname === '/auth/callback') return
    userManager.getUser().then((current) => {
      setUser(current && !current.expired ? current : null)
      setAuthReady(true)
    }).catch(() => setAuthReady(true))
    const onLoaded = (loaded: User) => setUser(loaded)
    const onUnloaded = () => setUser(null)
    userManager.events.addUserLoaded(onLoaded)
    userManager.events.addUserUnloaded(onUnloaded)
    return () => {
      userManager.events.removeUserLoaded(onLoaded)
      userManager.events.removeUserUnloaded(onUnloaded)
    }
  }, [])

  // Goes straight to the sign-in service. "replace" keeps the /sign-in hop out of
  // the history, so Back from the sign-in service returns to the page before it.
  async function signIn() {
    setAuthError('')
    const here = window.location.pathname
    const returnTo = here === '/sign-in' || here === '/auth/callback' ? '/' : here + window.location.search
    try { await userManager.signinRedirect({ redirectMethod: 'replace', state: { returnTo } }) }
    catch (error) { setAuthError(error instanceof Error ? error.message : 'Unable to start sign-in.') }
  }

  async function signOut() {
    await userManager.signoutRedirect()
  }

  // One sign-in for TransitHub staff and company users; roles decide what each sees.
  const signInPage = <SignInPage loading={!authReady} error={authError} onSignIn={signIn} />
  const signedIn = (page: ReactNode) => user ? page : signInPage

  return (
    <AppShell header={{ height: 72 }} padding={0}>
      <AppShell.Header className="topbar">
        <Container size="xl" className="topbar-inner">
          <Group gap="lg">
            <Link to="/" className="brand" aria-label="TransitHub home"><Logo tone="onDark" size={28} /></Link>
            {user && <>
              <span className="topbar-divider" aria-hidden />
              <WorkspaceLabel token={user.access_token} />
            </>}
          </Group>
          <Group gap="md">
            {!user && authReady && (
              <nav className="topbar-nav" aria-label="Main">
                {siteLinks.map((link) => <NavLink key={link.to} to={link.to} end={link.end}>{link.label}</NavLink>)}
              </nav>
            )}
            <span className={user ? undefined : 'topbar-theme'}><ColorSchemeToggle /></span>
            {user ? (
              <>
                <Group gap={8} className="user-chip">
                  <span className="user-initials" aria-hidden>{initials(user.profile.name || user.profile.preferred_username)}</span>
                  <Text size="sm" className="user-name">{user.profile.name || user.profile.preferred_username}</Text>
                </Group>
                <Button variant="outline" color="navy.2" radius="xl" size="xs" onClick={signOut} className="topbar-signout">Sign out</Button>
              </>
            ) : authReady && (
              <>
                <Button color="teal" radius="xl" size="sm" component={Link} to="/sign-in" className="topbar-signin">Sign in</Button>
                <MobileMenu />
              </>
            )}
          </Group>
        </Container>
      </AppShell.Header>
      <AppShell.Main>
        <Container size="xl" className="page-shell">
          <ScrollToLocation />
          <PageTitle signedIn={!!user} />
          <Routes>
            <Route path="/auth/callback" element={<AuthCallback onSignedIn={setUser} />} />
            <Route path="/" element={user ? <QueuePage token={user.access_token} /> : <LandingPage />} />
            <Route path="/services" element={<ServicesPage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="/contact" element={<ContactPage />} />
            <Route path="/register" element={<RegisterCompanyPage />} />
            <Route path="/sign-in" element={user ? <Navigate to="/" replace /> : signInPage} />
            <Route path="/staff" element={<Navigate to="/sign-in" replace />} />
            <Route path="/registrations/new" element={signedIn(user && <NewRegistrationPage token={user.access_token} />)} />
            <Route path="/enquiries" element={signedIn(user && <EnquiriesPage token={user.access_token} />)} />
            <Route path="/registrations/:id" element={signedIn(user && <RegistrationPage token={user.access_token} />)} />
            <Route path="/application/:id" element={signedIn(user && <ApplicationPage token={user.access_token} />)} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Container>
      </AppShell.Main>
    </AppShell>
  )
}

/** Says whose workspace this is: TransitHub staff, a bus company, or someone applying. */
function WorkspaceLabel({ token }: { token: string }) {
  const { me } = useMe(token)
  if (!me) return null
  const label = me.roles.length === 0 ? 'My application'
    : me.roles.every((role) => role === 'COMPANY_ADMINISTRATOR') ? 'Company workspace'
    : 'Staff workspace'
  return <Text size="sm" className="topbar-product">{label}</Text>
}

/** Names the browser tab after the page: the public site for visitors, the workspace for staff. */
function PageTitle({ signedIn }: { signedIn: boolean }) {
  const { pathname } = useLocation()
  useEffect(() => {
    const publicTitles: Record<string, string> = {
      '/': 'Bus travel in Zambia, made simple',
      '/services': 'Services',
      '/about': 'About us',
      '/contact': 'Contact us',
      '/register': 'Register your bus company',
      '/sign-in': 'Sign in',
    }
    const staffTitle = pathname === '/' ? 'Work queue'
      : pathname === '/enquiries' ? 'Enquiries'
      : pathname === '/registrations/new' ? 'New registration'
      : pathname.startsWith('/registrations/') ? 'Registration'
      : pathname.startsWith('/application/') ? 'My application'
      : undefined
    const page = signedIn ? staffTitle ?? publicTitles[pathname] : publicTitles[pathname] ?? 'Sign in'
    document.title = page ? `TransitHub · ${page}` : 'TransitHub'
  }, [pathname, signedIn])
  return null
}

function initials(name: string | undefined) {
  return (name ?? '?').split(/[\s._@-]+/).filter(Boolean).slice(0, 2).map((part) => part[0]!.toUpperCase()).join('') || '?'
}

const RETRY_KEY = 'transithub.signin-retry'

function AuthCallback({ onSignedIn }: { onSignedIn: (user: User) => void }) {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const handled = useRef(false)
  useEffect(() => {
    if (handled.current) return
    handled.current = true
    userManager.signinRedirectCallback().then((user) => {
      onSignedIn(user)
      const returnTo = (user.state as { returnTo?: string } | undefined)?.returnTo
      navigate(returnTo?.startsWith('/') ? returnTo : '/', { replace: true })
    }).catch(async () => {
      // Usually harmless: the sign-in finished in another tab (for example after
      // "Confirm my email" opened a new one), or this page was reloaded, so its
      // one-time sign-in ticket is already used. Recover instead of showing an error.
      const existing = await userManager.getUser().catch(() => null)
      if (existing && !existing.expired) {
        onSignedIn(existing)
        navigate('/', { replace: true })
        return
      }
      // Still signed in at the sign-in service: one fresh attempt returns at once.
      const last = Number(sessionStorage.getItem(RETRY_KEY) ?? 0)
      if (Date.now() - last > 60_000) {
        sessionStorage.setItem(RETRY_KEY, String(Date.now()))
        try { await userManager.signinRedirect({ redirectMethod: 'replace', state: { returnTo: '/' } }); return }
        catch { /* fall through to the message */ }
      }
      setError('We couldn’t finish signing you in. Please try again.')
    })
  }, [navigate, onSignedIn])

  if (error) {
    return (
      <Paper className="panel center-message" radius="lg" p="xl">
        <Title order={3}>Sign-in didn’t finish</Title>
        <Text c="dimmed" mt="xs">{error} If you just confirmed your email, your account is ready: simply sign in.</Text>
        <Group mt="lg" gap="sm" justify="center">
          <Button radius="xl" color="teal" onClick={() => {
            sessionStorage.removeItem(RETRY_KEY)
            void userManager.signinRedirect({ redirectMethod: 'replace', state: { returnTo: '/' } })
          }}>Sign in</Button>
          <Button radius="xl" variant="default" component={Link} to="/">Back to home</Button>
        </Group>
      </Paper>
    )
  }
  return <CenterMessage title="Signing you in" detail="Just a moment…" />
}

export default App
