import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import {
  Alert, Anchor, AppShell, Badge, Button, Checkbox, Container, Divider, Group, Loader,
  Modal, Paper, SimpleGrid, Stack, Table, Text, TextInput, Textarea, Title,
} from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate, Route, Routes, useNavigate, useParams } from 'react-router'
import type { User } from 'oidc-client-ts'
import { userManager } from './auth/oidc'
import { api } from './api/client'
import type { CompanyDetails, Registration } from './api/types'

const emptyDetails: CompanyDetails = {
  legalName: '', tradingName: '', registrationNumber: '', businessType: '', province: '', district: '',
  physicalAddress: '', postalAddress: '', contactEmail: '', contactPhone: '',
}

function normalizeDetails(details: CompanyDetails): CompanyDetails {
  return Object.fromEntries(Object.entries(details).map(([key, value]) => [key, value?.trim() || null])) as CompanyDetails
}

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

  return (
    <AppShell header={{ height: 72 }} padding={0}>
      <AppShell.Header className="topbar">
        <Container size="xl" className="topbar-inner">
          <Link to="/" className="brand" aria-label="TransitHub home">
            <span className="brand-mark">T</span>
            <span>transit<span className="brand-light">hub</span></span>
          </Link>
          <Group gap="md">
            <Anchor href="/v3/api-docs.yaml" target="_blank" rel="noreferrer" size="sm" c="dimmed">
              API contract
            </Anchor>
            {user ? (
              <>
                <Text size="sm" c="dimmed" className="user-name">{user.profile.name || user.profile.preferred_username}</Text>
                <Button variant="default" radius="xl" size="xs" onClick={signOut}>Sign out</Button>
              </>
            ) : authReady && (
              <Button color="teal" radius="xl" size="sm" onClick={signIn}>Sign in</Button>
            )}
          </Group>
        </Container>
      </AppShell.Header>
      <AppShell.Main>
        <Container size="xl" className="page-shell">
          {authError && <Alert color="red" mb="md">{authError}</Alert>}
          <Routes>
            <Route path="/auth/callback" element={<AuthCallback onSignedIn={setUser} />} />
            <Route path="/" element={
              user ? <Home token={user.access_token} /> : <Welcome loading={!authReady} onSignIn={signIn} />
            } />
            <Route path="/registrations/:id" element={
              user ? <RegistrationPage token={user.access_token} /> : <Welcome loading={!authReady} onSignIn={signIn} />
            } />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Container>
      </AppShell.Main>
    </AppShell>
  )
}

function Welcome({ loading, onSignIn }: { loading: boolean; onSignIn: () => void }) {
  return (
    <section className="welcome-layout">
      <div className="welcome-copy">
        <Badge color="teal" variant="light" radius="xl" mb="lg">Release 1 · staff workspace</Badge>
        <Title order={1} className="hero-title">Registration work,<br /><span>in one clear place.</span></Title>
        <Text size="lg" c="dimmed" maw={520} mt="lg" lh={1.7}>
          Capture a company registration, follow its progress, and see the actions available to you at every step.
        </Text>
        <Group mt="xl">
          <Button size="md" radius="xl" color="teal" onClick={onSignIn} loading={loading}>
            Sign in with TransitHub
          </Button>
          <Anchor href="/swagger-ui/index.html" target="_blank" c="dimmed" size="sm">Explore the API</Anchor>
        </Group>
        <Text size="xs" c="dimmed" mt="xl">Access is managed by your TransitHub role.</Text>
      </div>
      <Paper className="welcome-card" radius="xl" p="xl" shadow="sm">
        <div className="card-orbit orbit-one" /><div className="card-orbit orbit-two" />
        <div className="welcome-card-content">
          <Text size="xs" fw={700} tt="uppercase" c="teal.7" lts={1.2}>A connected workflow</Text>
          <div className="workflow-step"><span>01</span><div><Text fw={600}>Capture</Text><Text size="sm" c="dimmed">Build a registration draft</Text></div></div>
          <div className="workflow-line" />
          <div className="workflow-step"><span>02</span><div><Text fw={600}>Verify</Text><Text size="sm" c="dimmed">Review details and evidence</Text></div></div>
          <div className="workflow-line" />
          <div className="workflow-step"><span>03</span><div><Text fw={600}>Decide</Text><Text size="sm" c="dimmed">Move toward approval</Text></div></div>
        </div>
      </Paper>
    </section>
  )
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

function Home({ token }: { token: string }) {
  const navigate = useNavigate()
  const [details, setDetails] = useState<CompanyDetails>(emptyDetails)
  const [registrationId, setRegistrationId] = useState('')
  const mutation = useMutation({
    mutationFn: () => api.startRegistration(token, normalizeDetails(details)),
    onSuccess: (registration) => navigate(`/registrations/${registration.id}`),
  })

  return (
    <div className="content-grid">
      <section>
        <div className="eyebrow">Workspace / registrations</div>
        <Title order={1} className="page-title">Good to see you.</Title>
        <Text c="dimmed" mt={6} mb="xl">Start a new application or reopen one you were working on.</Text>
        <Paper className="panel" radius="lg" p="xl">
          <Group justify="space-between" mb="lg" align="flex-start">
            <div><Text fw={700} size="lg">New registration</Text><Text size="sm" c="dimmed" mt={4}>Create a draft and fill in the company details.</Text></div>
            <Badge color="teal" variant="light">Online capture</Badge>
          </Group>
          <DetailsFields value={details} onChange={setDetails} />
          {mutation.error && <ApiError error={mutation.error} />}
          <Group justify="flex-end" mt="lg">
            <Button color="teal" radius="xl" onClick={() => mutation.mutate()} loading={mutation.isPending}>
              Create draft <span className="button-arrow">→</span>
            </Button>
          </Group>
        </Paper>
      </section>
      <aside className="side-column">
        <Paper className="panel soft-panel" radius="lg" p="lg">
          <Text fw={700}>Open a registration</Text>
          <Text size="sm" c="dimmed" mt={4} mb="md">Use its UUID to pick up where you left off.</Text>
          <TextInput placeholder="Registration ID" value={registrationId} onChange={(event) => setRegistrationId(event.currentTarget.value)} />
          <Button variant="default" fullWidth mt="sm" radius="md" disabled={!registrationId.trim()}
            onClick={() => navigate(`/registrations/${registrationId.trim()}`)}>Open registration</Button>
        </Paper>
        <Paper className="tip-card" radius="lg" p="lg">
          <Text size="xs" fw={700} tt="uppercase" lts={1}>How this works</Text>
          <Text fw={600} mt="sm">The server guides each step.</Text>
          <Text size="sm" mt={6} className="tip-copy">Your available actions come from your role and the registration’s current status.</Text>
          <Anchor href="/v3/api-docs.yaml" target="_blank" size="sm" c="white" underline="always" mt="md" display="inline-block">View OpenAPI description ↗</Anchor>
        </Paper>
      </aside>
    </div>
  )
}

function RegistrationPage({ token }: { token: string }) {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const [auditOpen, setAuditOpen] = useState(false)
  const [editDetails, setEditDetails] = useState<CompanyDetails>(emptyDetails)
  const registrationQuery = useQuery({ queryKey: ['registration', id], queryFn: () => api.getRegistration(token, id), enabled: !!id })
  const updateMutation = useMutation({
    mutationFn: ({ registration, details }: { registration: Registration; details: CompanyDetails }) => api.updateDetails(token, registration, details),
    onSuccess: (registration) => queryClient.setQueryData(['registration', id], registration),
  })
  const submitMutation = useMutation({
    mutationFn: (registration: Registration) => api.submit(token, registration),
    onSuccess: ({ registration }) => queryClient.setQueryData(['registration', id], registration),
  })
  const withdrawMutation = useMutation({
    mutationFn: ({ registration, reason }: { registration: Registration; reason: string }) => api.withdraw(token, registration, reason),
    onSuccess: (registration) => queryClient.setQueryData(['registration', id], registration),
  })
  const registration = registrationQuery.data

  useEffect(() => {
    if (registration) setEditDetails({ ...emptyDetails, ...registration.details })
  }, [registration?.id, registration?.version])

  if (registrationQuery.isPending) return <CenterMessage title="Loading registration" detail="Getting the latest saved version…" />
  if (registrationQuery.error || !registration) return <CenterMessage title="Couldn’t open this registration" detail={registrationQuery.error?.message || 'Check the ID and try again.'} />

  const allowed = new Set(registration.allowedActions)
  return (
    <Stack gap="lg">
      <div className="eyebrow"><Link to="/">Workspace</Link> / {registration.reference}</div>
      <Group justify="space-between" align="flex-start" className="registration-heading">
        <div>
          <Group gap="sm" align="center"><Title order={1} className="page-title">{registration.details.legalName || 'New registration'}</Title><StatusBadge status={registration.status} /></Group>
          <Text c="dimmed" mt={6}>{registration.reference} <span className="dot-separator">·</span> Updated {new Date(registration.updatedAt).toLocaleString()}</Text>
        </div>
        <Group>
          {allowed.has('viewAudit') && <Button variant="default" radius="xl" onClick={() => setAuditOpen(true)}>Audit history</Button>}
          {allowed.has('submit') && <Button color="teal" radius="xl" loading={submitMutation.isPending}
            onClick={() => submitMutation.mutate(registration)}>Submit registration</Button>}
          {allowed.has('withdraw') && <WithdrawButton registration={registration} onWithdraw={(reason) => withdrawMutation.mutate({ registration, reason })} busy={withdrawMutation.isPending} />}
        </Group>
      </Group>

      <SimpleGrid cols={{ base: 1, md: 3 }} spacing="md">
        <Metric label="Status" value={registration.status.replaceAll('_', ' ').toLowerCase()} />
        <Metric label="Version" value={`v${registration.version}`} />
        <Metric label="Available actions" value={String(registration.allowedActions.length)} />
      </SimpleGrid>

      {registration.missingForSubmission.length > 0 && (
        <Alert color="orange" variant="light" title="Still needed before submission">
          {registration.missingForSubmission.map((item) => item.replaceAll('.', ' / ')).join(' · ')}
        </Alert>
      )}
      {updateMutation.error && <ApiError error={updateMutation.error} />}
      {submitMutation.error && <ApiError error={submitMutation.error} />}
      {withdrawMutation.error && <ApiError error={withdrawMutation.error} />}

      <div className="detail-grid">
        <Paper className="panel" radius="lg" p="xl">
          <Group justify="space-between" mb="lg"><div><Text fw={700} size="lg">Company details</Text><Text c="dimmed" size="sm">Changes require version {registration.version}.</Text></div>
            {allowed.has('editDraft') && <Badge color="teal" variant="light">Editable</Badge>}</Group>
          <DetailsFields value={editDetails} onChange={setEditDetails} disabled={!allowed.has('editDraft')} />
          {allowed.has('editDraft') && <Group justify="flex-end" mt="lg"><Button color="teal" radius="xl" onClick={() => updateMutation.mutate({ registration, details: normalizeDetails(editDetails) })} loading={updateMutation.isPending}>Save details</Button></Group>}
        </Paper>
        <Paper className="panel" radius="lg" p="xl">
          <Text fw={700} size="lg">What you can do</Text>
          <Text size="sm" c="dimmed" mt={4}>These actions update as the registration moves forward.</Text>
          <Divider my="md" />
          {registration.allowedActions.length ? <Stack gap="sm">{registration.allowedActions.map((action) => <div className="action-line" key={action}><span className="action-check">✓</span><Text size="sm">{actionLabel(action)}</Text></div>)}</Stack>
            : <Text size="sm" c="dimmed">No actions are available for your account right now.</Text>}
          <Divider my="md" />
          <Text size="xs" c="dimmed">Reference ID</Text><Text size="xs" ff="monospace" mt={4} className="uuid">{registration.id}</Text>
        </Paper>
      </div>

      <AuditModal token={token} id={id} opened={auditOpen} onClose={() => setAuditOpen(false)} />
    </Stack>
  )
}

function DetailsFields({ value, onChange, disabled = false }: {
  value: CompanyDetails; onChange: (value: CompanyDetails) => void; disabled?: boolean
}) {
  const set = (key: keyof CompanyDetails) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    onChange({ ...value, [key]: event.currentTarget.value })
  return (
    <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
      <TextInput label="Legal name" placeholder="e.g. Banda Transport Limited" value={value.legalName || ''} onChange={set('legalName')} disabled={disabled} />
      <TextInput label="Trading name" placeholder="Optional" value={value.tradingName || ''} onChange={set('tradingName')} disabled={disabled} />
      <TextInput label="Registration number" placeholder="PACRA number" value={value.registrationNumber || ''} onChange={set('registrationNumber')} disabled={disabled} />
      <TextInput label="Business type" placeholder="e.g. Bus operator" value={value.businessType || ''} onChange={set('businessType')} disabled={disabled} />
      <TextInput label="Province" placeholder="Province" value={value.province || ''} onChange={set('province')} disabled={disabled} />
      <TextInput label="District" placeholder="District" value={value.district || ''} onChange={set('district')} disabled={disabled} />
      <Textarea label="Physical address" placeholder="Street, area, town" value={value.physicalAddress || ''} onChange={set('physicalAddress')} disabled={disabled} autosize minRows={2} />
      <Textarea label="Postal address" placeholder="Optional" value={value.postalAddress || ''} onChange={set('postalAddress')} disabled={disabled} autosize minRows={2} />
      <TextInput label="Contact email" type="email" placeholder="name@company.zm" value={value.contactEmail || ''} onChange={set('contactEmail')} disabled={disabled} />
      <TextInput label="Contact phone" type="tel" placeholder="+260 …" value={value.contactPhone || ''} onChange={set('contactPhone')} disabled={disabled} />
    </SimpleGrid>
  )
}

function WithdrawButton({ registration, onWithdraw, busy }: {
  registration: Registration; onWithdraw: (reason: string) => void; busy: boolean
}) {
  const [opened, setOpened] = useState(false)
  const [reason, setReason] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  return <>
    <Button color="red" variant="light" radius="xl" onClick={() => setOpened(true)}>Withdraw</Button>
    <Modal opened={opened} onClose={() => setOpened(false)} title="Withdraw this registration" centered radius="lg">
      <Stack>
        <Alert color="orange" variant="light">This will close the registration as withdrawn. Its history and submitted information will be retained.</Alert>
        <Textarea label="Reason" placeholder="Record the representative’s reason for withdrawing" value={reason} onChange={(event) => setReason(event.currentTarget.value)} required minRows={3} maxLength={2000} />
        <Checkbox checked={confirmed} onChange={(event) => setConfirmed(event.currentTarget.checked)}
          label="I confirm the representative instructed this withdrawal." />
        <Group justify="flex-end"><Button variant="default" onClick={() => setOpened(false)}>Cancel</Button>
          <Button color="red" disabled={!reason.trim() || !confirmed} loading={busy}
            onClick={() => { onWithdraw(reason.trim()); setOpened(false) }}>Confirm withdrawal</Button></Group>
        <Text size="xs" c="dimmed">Request uses registration version {registration.version}.</Text>
      </Stack>
    </Modal>
  </>
}

function AuditModal({ token, id, opened, onClose }: { token: string; id: string; opened: boolean; onClose: () => void }) {
  const query = useQuery({ queryKey: ['audit', id], queryFn: () => api.audit(token, id), enabled: opened })
  return <Modal opened={opened} onClose={onClose} title="Audit history" size="xl" centered radius="lg">
    {query.isPending ? <Loader size="sm" /> : query.error ? <ApiError error={query.error} /> : (
      <>
        <Text size="sm" c="dimmed" mb="md">{query.data?.total ?? 0} recorded events · newest first</Text>
        <Table.ScrollContainer minWidth={680}>
          <Table striped highlightOnHover>
            <Table.Thead><Table.Tr><Table.Th>When</Table.Th><Table.Th>Action</Table.Th><Table.Th>Actor</Table.Th><Table.Th>Reason</Table.Th></Table.Tr></Table.Thead>
            <Table.Tbody>{query.data?.events.map((event) => <Table.Tr key={event.id}>
              <Table.Td>{new Date(event.occurredAt).toLocaleString()}</Table.Td><Table.Td>{event.action}</Table.Td>
              <Table.Td>{event.actorId}</Table.Td><Table.Td>{event.reason || '—'}</Table.Td>
            </Table.Tr>)}</Table.Tbody>
          </Table>
        </Table.ScrollContainer>
        {query.data?.events.length === 0 && <Text size="sm" c="dimmed" ta="center" py="lg">No audit entries yet.</Text>}
      </>
    )}
  </Modal>
}

function Metric({ label, value }: { label: string; value: string }) {
  return <Paper className="metric-card" radius="lg" p="md"><Text size="xs" c="dimmed" tt="uppercase" fw={700} lts={0.7}>{label}</Text><Text fw={700} mt={5} tt={label === 'Status' ? 'capitalize' : undefined}>{value}</Text></Paper>
}

function StatusBadge({ status }: { status: string }) {
  const color = status === 'APPROVED' ? 'green' : status === 'REJECTED' || status === 'WITHDRAWN' ? 'red' : status === 'DRAFT' ? 'gray' : 'teal'
  return <Badge color={color} variant="light" radius="xl">{status.replaceAll('_', ' ')}</Badge>
}

function CenterMessage({ title, detail }: { title: string; detail: string }) {
  return <Paper className="panel center-message" radius="lg" p="xl"><Title order={3}>{title}</Title><Text c="dimmed" mt="xs">{detail}</Text><Anchor component={Link} to="/" mt="lg">Back to workspace</Anchor></Paper>
}

function ApiError({ error }: { error: Error }) {
  return <Alert color="red" variant="light" mt="md" title="The request could not be completed">{error.message} Sign in with an account that has the required TransitHub permission, then try again.</Alert>
}

function actionLabel(action: string) {
  return ({ editDraft: 'Edit this draft', submit: 'Submit for verification', withdraw: 'Withdraw on the representative’s instruction',
    viewAudit: 'View audit history', assignVerification: 'Assign verification', verify: 'Record verification work',
    requestInformation: 'Request more information', provideInformation: 'Provide requested information',
    recommend: 'Make a recommendation', reviewRecommendation: 'Review recommendation', approve: 'Approve', reject: 'Reject',
    resolveSyncConflict: 'Resolve a sync conflict' } as Record<string, string>)[action] || action
}

export default App
