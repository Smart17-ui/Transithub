import { useEffect, useState } from 'react'
import { Alert, Button, Group, Paper, Text, Title } from '@mantine/core'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { ApiProblem, api } from '../api/client'
import { userManager } from '../auth/oidc'
import { ApiError } from '../components/common'

const TOKEN_KEY = 'transithub.invitation-token'

/**
 * Accepting a Company Administrator invitation (AC-R1-EST-05), opened from the link in
 * the invitation email or SMS. The one-time token is moved out of the address bar at
 * once (into this tab's memory only), so it does not stay in history or get shared.
 */
export function InvitationPage({ token: accessToken }: { token: string | null }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [invitation] = useState<string | null>(() => {
    const fromUrl = new URLSearchParams(window.location.search).get('token')
    if (fromUrl) sessionStorage.setItem(TOKEN_KEY, fromUrl)
    return fromUrl ?? sessionStorage.getItem(TOKEN_KEY)
  })
  useEffect(() => {
    if (window.location.search) window.history.replaceState(null, '', '/invitation')
  }, [])

  const accept = useMutation({
    mutationFn: () => api.acceptInvitation(accessToken!, invitation!),
    onSuccess: () => {
      sessionStorage.removeItem(TOKEN_KEY)
      void queryClient.invalidateQueries({ queryKey: ['me'] })
      navigate('/', { replace: true })
    },
  })

  if (!invitation) {
    return (
      <Paper className="panel center-message" radius="lg" p="xl">
        <Title order={2}>This invitation link is incomplete</Title>
        <Text c="dimmed" mt="sm">Open the link exactly as it appears in your email or SMS, or ask TransitHub to send a new one.</Text>
        <Button mt="lg" radius="xl" variant="default" component={Link} to="/contact">Contact TransitHub</Button>
      </Paper>
    )
  }

  const problem = accept.error instanceof ApiProblem ? accept.error : null
  return (
    <Paper className="panel center-message invitation-card" radius="lg" p="xl">
      <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">Invitation</Text>
      <Title order={1} className="application-title">Set up your company on TransitHub</Title>
      <Text c="dimmed" mt="sm">
        Your company has been approved. Accept this invitation to become its Company Administrator: you will manage
        your company’s account and, as they launch, its staff, trips and ticket sales.
      </Text>

      {!accessToken ? (
        <>
          <Text mt="lg">First, sign in. If you do not have a TransitHub account yet, choose <b>Create an account</b> on the next page, using your own email address.</Text>
          <Group mt="lg" justify="center">
            <Button size="md" radius="xl" color="teal"
              onClick={() => void userManager.signinRedirect({ state: { returnTo: '/invitation' } })}>
              Sign in to accept <span className="button-arrow">→</span>
            </Button>
          </Group>
        </>
      ) : (
        <>
          {problem?.code === 'invitation-expired' && (
            <Alert color="orange" mt="lg" title="This invitation has expired">Ask TransitHub to send you a new link.</Alert>
          )}
          {problem?.code === 'invitation-invalid' && (
            <Alert color="red" mt="lg" title="This invitation can no longer be used">
              It may already have been accepted, or replaced by a newer link. If you need help, contact TransitHub.
            </Alert>
          )}
          {accept.error && !problem?.code?.startsWith('invitation-') && <ApiError error={accept.error} />}
          <Group mt="lg" justify="center">
            <Button size="md" radius="xl" color="teal" loading={accept.isPending} onClick={() => accept.mutate()}
              disabled={problem?.code?.startsWith('invitation-')}>
              Accept and continue
            </Button>
          </Group>
          <Text size="xs" c="dimmed" mt="md">You will be asked to set up an authenticator app at your next sign-in, to keep your company secure.</Text>
        </>
      )}
    </Paper>
  )
}
