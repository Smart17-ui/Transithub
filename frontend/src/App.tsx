import { useEffect, useRef, useState, type ReactNode } from 'react'
import { AppShell, Button, Container, Group, Text } from '@mantine/core'
import { Link, NavLink, Navigate, Route, Routes, useNavigate } from 'react-router'
import type { User } from 'oidc-client-ts'
import { userManager } from './auth/oidc'
import { Logo } from './brand/Logo'
import { CenterMessage } from './components/common'
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

  async function signIn() {
    setAuthError('')
    try { await userManager.signinRedirect() }
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
              <Text size="sm" className="topbar-product">Staff workspace</Text>
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
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Container>
      </AppShell.Main>
    </AppShell>
  )
}

function initials(name: string | undefined) {
  return (name ?? '?').split(/[\s._@-]+/).filter(Boolean).slice(0, 2).map((part) => part[0]!.toUpperCase()).join('') || '?'
}

function AuthCallback({ onSignedIn }: { onSignedIn: (user: User) => void }) {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const handled = useRef(false)
  useEffect(() => {
    if (handled.current) return
    handled.current = true
    userManager.signinRedirectCallback().then((user) => {
      onSignedIn(user)
      navigate('/', { replace: true })
    }).catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Sign-in could not be completed.'))
  }, [navigate, onSignedIn])
  return <CenterMessage title={error ? 'Sign-in failed' : 'Signing you in'} detail={error || 'Returning to your workspace…'} />
}

export default App
