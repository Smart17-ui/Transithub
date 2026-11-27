import { Alert, Anchor, Button, Paper, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import { LogoMark } from '../brand/Logo'
import { IconBuilding, IconShield, IconTicket } from './LandingPage'

/**
 * The one sign-in page for TransitHub staff and transport-company users.
 * After sign-in, each person sees only what their roles allow.
 * Passengers do not sign in here: they book as guests or sign in with their
 * phone number from the booking pages (BP-03 decision J).
 */
export function SignInPage({ loading, error, onSignIn }: { loading: boolean; error?: string; onSignIn: () => void }) {
  return (
    <div className="staff-signin">
      <Paper className="staff-signin-card" radius="xl">
        <aside className="staff-signin-side">
          <span className="hero-kicker">For bus companies and the TransitHub team</span>
          <Title order={2} className="staff-signin-side-title">Everything your team needs, in one place</Title>
          <ul className="staff-signin-points">
            <li><span aria-hidden><IconBuilding /></span>Follow your company’s registration and its progress</li>
            <li><span aria-hidden><IconTicket /></span>Sell tickets and manage trips as these tools launch</li>
            <li><span aria-hidden><IconShield /></span>Each person sees only the work their role allows</li>
          </ul>
        </aside>

        <div className="staff-signin-main">
          <LogoMark size={36} />
          <Title order={1} className="staff-signin-title">Welcome back</Title>
          <Text c="dimmed" mt={6}>Sign in to your TransitHub account to continue.</Text>

          {error && (
            <Alert color="red" radius="md" mt="lg" title="We couldn’t start sign-in" className="staff-signin-error">
              {friendly(error)}
            </Alert>
          )}

          <Button fullWidth size="md" radius="xl" color="teal" mt="xl" onClick={onSignIn} loading={loading}>
            {error ? 'Try again' : 'Continue to sign in'} <span className="button-arrow">→</span>
          </Button>
          <Text size="xs" c="dimmed" mt="sm">You’ll be taken to our secure sign-in page and brought straight back.</Text>

          <div className="staff-signin-note">
            <Text size="sm" fw={600}>Travelling?</Text>
            <Text size="sm" c="dimmed" mt={4}>
              You won’t need an account to book a ticket. Online booking is coming soon.
            </Text>
          </div>
          <div className="staff-signin-links">
            <Anchor component={Link} to="/register" size="sm">Register your bus company</Anchor>
            <Anchor component={Link} to="/" size="sm">← Back to home</Anchor>
          </div>
        </div>
      </Paper>
    </div>
  )
}

/** Browser network errors ("Failed to fetch") mean the sign-in service could not be reached. */
function friendly(error: string) {
  return /failed to fetch|networkerror|load failed/i.test(error)
    ? 'Our sign-in service isn’t responding right now. Please check your connection and try again in a moment.'
    : error
}
