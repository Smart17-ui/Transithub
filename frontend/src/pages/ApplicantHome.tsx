import { Anchor, Badge, Button, Group, Modal, Paper, Text, Title } from '@mantine/core'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { userManager } from '../auth/oidc'
import { Link, useNavigate } from 'react-router'
import { ApiProblem, api } from '../api/client'
import type { ApplicationSummary, RegistrationStatus } from '../api/types'
import { ApiError, formatDateTime } from '../components/common'

/**
 * Home for someone who created their own account to bring their bus company to
 * TransitHub (self-service, Release 1 §9): start an application, or continue one.
 */
const statusLabel: Record<RegistrationStatus, string> = {
  DRAFT: 'In preparation', SUBMITTED: 'Submitted', UNDER_VERIFICATION: 'Being checked',
  AWAITING_INFORMATION: 'More information needed', RECOMMENDED: 'Being checked', APPROVED: 'Approved',
  REJECTED: 'Declined', WITHDRAWN: 'Withdrawn',
}
const closed = (s: RegistrationStatus) => s === 'APPROVED' || s === 'REJECTED' || s === 'WITHDRAWN'

export function ApplicantHome({ token, name, emailNotConfirmed }: { token: string; name: string | null; emailNotConfirmed: boolean }) {
  const navigate = useNavigate()
  const [confirmClose, setConfirmClose] = useState(false)
  const closeAccount = useMutation({
    mutationFn: () => api.closeAccount(token),
    // The sign-in account no longer exists, so there is no session to end at the
    // sign-in service: forget it locally and return to the public home page.
    onSuccess: async () => {
      await userManager.removeUser()
      window.location.assign('/')
    },
  })
  const applications = useQuery({ queryKey: ['my-applications'], queryFn: () => api.myApplications(token), enabled: !emailNotConfirmed })
  const start = useMutation({
    mutationFn: () => api.startApplication(token),
    onSuccess: (created) => navigate(`/application/${created.id}`),
    onError: (error) => {
      // Already one in progress (SS-11): show that one instead.
      if (error instanceof ApiProblem && error.code === 'application-already-open') void applications.refetch()
    },
  })

  if (emailNotConfirmed) {
    return (
      <Paper className="panel center-message" radius="lg" p="xl">
        <Title order={2}>Confirm your email address</Title>
        <Text c="dimmed" mt="sm">We sent you an email with a link to confirm your address. Open it, then sign in again to continue.</Text>
      </Paper>
    )
  }

  const list = applications.data ?? []
  const open = list.find((a) => !closed(a.status))
  const past = list.filter((a) => a !== open)

  return (
    <div className="applicant-home">
      <Paper className="panel applicant-welcome" radius="lg" p="xl">
        <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">Your account</Text>
        <Title order={1} className="application-title">Welcome{name ? `, ${name}` : ''}</Title>

        {applications.isPending ? <Text c="dimmed" mt="sm">Loading your applications…</Text>
          : open ? <OpenApplication application={open} />
          : (
            <>
              <Text c="dimmed" mt="sm" maw={640}>
                {past.length ? 'You can start a new application for your bus company at any time.'
                  : 'Apply to bring your bus company to TransitHub. It takes about 15 minutes, and you can stop and come back at any time.'}
              </Text>
              <ul className="tick-list applicant-needs">
                <li>Your company’s PACRA Certificate of Incorporation</li>
                <li>Your RTSA Road Service Licence</li>
                <li>The NRC or passport number of each director who signs</li>
              </ul>
              <Group mt="lg">
                <Button size="md" radius="xl" color="teal" onClick={() => start.mutate()} loading={start.isPending}>
                  Start your application <span className="button-arrow">→</span>
                </Button>
              </Group>
            </>
          )}
        {(applications.error ?? start.error) && <ApiError error={(applications.error ?? start.error)!} />}
      </Paper>

      {past.length > 0 && (
        <Paper className="panel" radius="lg" p="xl">
          <Text fw={700}>Earlier applications</Text>
          {past.map((a) => (
            <Group key={a.id} justify="space-between" className="list-row" mt="sm">
              <div>
                <Anchor component={Link} to={`/application/${a.id}`} fw={600} size="sm">{a.legalName || 'Bus company application'}</Anchor>
                <Text size="xs" c="dimmed">{a.reference} · {formatDateTime(a.updatedAt)}</Text>
              </div>
              <Badge variant="light" color={a.status === 'APPROVED' ? 'green' : a.status === 'REJECTED' ? 'red' : 'gray'}>{statusLabel[a.status]}</Badge>
            </Group>
          ))}
        </Paper>
      )}

      {/* SS-14: travellers and staff sometimes sign up here by mistake. */}
      <div className="applicant-not-for-me">
        <Text fw={700} size="sm">Not registering a bus company?</Text>
        <Text size="sm" c="dimmed" mt={4}>
          <b>Travelling?</b> You don’t need an account: you’ll book as a guest with your name and phone number when
          online booking opens. <b>TransitHub or bus company staff?</b> Your administrator gives this account access;
          there is nothing else you need to do.
        </Text>
        <Text size="sm" c="dimmed" mt={6}>
          Created this account by mistake?{' '}
          {list.length === 0 && !applications.isPending
            ? <Anchor component="button" type="button" size="sm" onClick={() => setConfirmClose(true)}>Close your account</Anchor>
            : <Anchor component={Link} to="/contact">Ask us to close it</Anchor>}
          . Accounts with no application are closed automatically after 90 days.
        </Text>
      </div>

      {/* SS-14 (#19): only offered while there is no application; the server checks again. */}
      <Modal opened={confirmClose} onClose={() => setConfirmClose(false)} centered radius="lg"
        title={<Text fw={700}>Close your account?</Text>}>
        <Text size="sm">
          We will delete your sign-in and remove your name and email address from TransitHub. You can't undo this,
          but you can create a new account at any time.
        </Text>
        {closeAccount.error && <ApiError error={closeAccount.error} />}
        <Group justify="flex-end" mt="lg">
          <Button variant="default" radius="xl" onClick={() => setConfirmClose(false)}>Keep my account</Button>
          <Button color="red" radius="xl" loading={closeAccount.isPending} onClick={() => closeAccount.mutate()}>
            Close my account
          </Button>
        </Group>
      </Modal>
    </div>
  )
}

function OpenApplication({ application }: { application: ApplicationSummary }) {
  return (
    <div className="applicant-open">
      <Group justify="space-between" align="center" wrap="wrap" gap="md">
        <div>
          <Text fw={700} size="lg">{application.legalName || 'Your bus company application'}</Text>
          <Group gap="sm" mt={4}>
            <Badge variant="light" color={application.status === 'DRAFT' ? 'gray' : application.status === 'AWAITING_INFORMATION' ? 'orange' : 'blue'}>
              {statusLabel[application.status]}
            </Badge>
            <Text size="xs" c="dimmed">{application.reference} · updated {formatDateTime(application.updatedAt)}</Text>
          </Group>
        </div>
        <Button radius="xl" color="teal" component={Link} to={`/application/${application.id}`}>
          {application.status === 'DRAFT' ? 'Continue your application' : 'View your application'} <span className="button-arrow">→</span>
        </Button>
      </Group>
    </div>
  )
}
