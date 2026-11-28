import { Alert, Anchor, Badge, Paper, Text, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api, ApiProblem } from '../api/client'
import type { EvidenceType, Me, RegistrationStatus } from '../api/types'

export const statusLabels: Record<RegistrationStatus, string> = {
  DRAFT: 'Draft',
  SUBMITTED: 'Awaiting review',
  UNDER_VERIFICATION: 'Under review',
  AWAITING_INFORMATION: 'Information requested',
  RECOMMENDED: 'Pending decision',
  APPROVED: 'Approved',
  REJECTED: 'Declined',
  WITHDRAWN: 'Withdrawn',
}

const statusColors: Record<RegistrationStatus, string> = {
  DRAFT: 'gray',
  SUBMITTED: 'blue',
  UNDER_VERIFICATION: 'teal',
  AWAITING_INFORMATION: 'orange',
  RECOMMENDED: 'grape',
  APPROVED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'gray',
}

export function StatusBadge({ status }: { status: RegistrationStatus }) {
  return <Badge color={statusColors[status] ?? 'gray'} variant="light" radius="xl">{statusLabels[status] ?? status}</Badge>
}

export const evidenceTypeLabels: Record<EvidenceType, string> = {
  CERTIFICATE_OF_INCORPORATION: 'Certificate of Incorporation (PACRA)',
  TAX_CLEARANCE: 'Tax Clearance Certificate (ZRA)',
  OPERATING_LICENCE: 'Road Service Licence (RTSA)',
  REPRESENTATIVE_IDENTITY: 'Proof of identity (NRC or passport)',
  PROOF_OF_ADDRESS: 'Proof of business address',
  OTHER: 'Other supporting document',
}

export function actionLabel(action: string) {
  return ({
    editDraft: 'Edit the application', submit: 'Submit for review', withdraw: 'Withdraw at the applicant’s request',
    viewAudit: 'View the audit trail', takeVerification: 'Assign the review to yourself',
    assignVerification: 'Assign a Compliance Officer', verify: 'Review documents and verify registry records',
    requestInformation: 'Request additional information', provideInformation: 'Submit the requested information',
    approve: 'Approve the application', reject: 'Decline the application',
    resolveSyncConflict: 'Resolve a synchronisation conflict',
  } as Record<string, string>)[action] || action
}

export function formatDateTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '—'
}

export function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleDateString() : '—'
}

export function Metric({ label, value }: { label: string; value: string }) {
  return (
    <Paper className="metric-card" radius="lg" p="md">
      <Text size="xs" c="dimmed" tt="uppercase" fw={700} lts={0.7}>{label}</Text>
      <Text fw={700} mt={5}>{value}</Text>
    </Paper>
  )
}

export function CenterMessage({ title, detail }: { title: string; detail: string }) {
  return (
    <Paper className="panel center-message" radius="lg" p="xl">
      <Title order={3}>{title}</Title>
      <Text c="dimmed" mt="xs">{detail}</Text>
      <Anchor component={Link} to="/" mt="lg">Back to applications</Anchor>
    </Paper>
  )
}

/** Explains an API error in terms of what the user can do next. */
export function ApiError({ error, onReload }: { error: Error; onReload?: () => void }) {
  const problem = error instanceof ApiProblem ? error : null
  let title = 'The request could not be completed'
  let hint: string | null = null
  if (problem?.code === 'concurrent-modification' || problem?.status === 409) {
    title = 'This application was updated by someone else'
    hint = 'Reload to see the latest version, then try again.'
  } else if (problem?.code === 'separation-of-duties') {
    title = 'Action restricted for this application'
    hint = 'You were involved in preparing this application, so a different colleague must complete this step.'
  } else if (problem?.status === 403) {
    title = 'Your role does not permit this action'
    hint = 'Contact your administrator if you believe you should have access.'
  } else if (problem?.status === 401) {
    title = 'Your session has expired'
    hint = 'Please sign in again to continue.'
  }
  return (
    <Alert color="red" variant="light" mt="md" title={title}>
      {error.message}
      {problem?.missing?.length ? <> Outstanding: {problem.missing.map(requirementLabel).join('; ')}.</> : null}
      {hint && <Text size="sm" mt={4}>{hint}</Text>}
      {onReload && problem?.status === 409 && <Anchor component="button" size="sm" mt={4} onClick={onReload}>Reload now</Anchor>}
    </Alert>
  )
}

/**
 * Names for user ids (registrations and audit events store ids). Unknown
 * ids, such as "system", are shown as they are.
 */
export function useStaffNames(token: string, ids: Array<string | null | undefined>) {
  const unique = [...new Set(ids.filter((id): id is string => !!id))].sort()
  const query = useQuery({
    queryKey: ['staff-names', unique],
    queryFn: () => api.staffNames(token, unique),
    enabled: unique.length > 0,
    staleTime: 5 * 60_000,
  })
  return (id: string | null | undefined) => (id ? query.data?.[id] ?? shortId(id) : '—')
}

function shortId(id: string) {
  return /^[0-9a-f-]{36}$/i.test(id) ? `user ${id.slice(-6)}` : id
}

/** The signed-in user's roles and permissions in the current scope. */
/**
 * The signed-in user. Someone who just created their own account (self-service
 * sign-up) is not known to TransitHub yet: they are registered here, once.
 */
async function loadMe(token: string): Promise<Me> {
  const me = await api.me(token)
  if (me.registered) return me
  try {
    await api.createAccount(token)
  } catch (error) {
    if (error instanceof ApiProblem && error.code === 'email-not-verified') return { ...me, emailNotConfirmed: true }
    throw error
  }
  return api.me(token)
}

export function useMe(token: string) {
  const query = useQuery({ queryKey: ['me'], queryFn: () => loadMe(token), staleTime: 60_000 })
  const permissions = new Set(query.data?.permissions ?? [])
  return { me: query.data, loading: query.isPending, can: (permission: string) => permissions.has(permission) }
}

/** Staff-facing role names. Internal role codes are unchanged. */
export const roleNames: Record<string, string> = {
  REGISTRATION_OFFICER: 'Onboarding Officer',
  REGISTRATION_SUPERVISOR: 'Onboarding Supervisor',
  VERIFICATION_OFFICER: 'Compliance Officer',
  PLATFORM_ADMINISTRATOR: 'Platform Administrator',
  COMPANY_ADMINISTRATOR: 'Company Administrator',
}

const roleIntros: Record<string, string> = {
  REGISTRATION_OFFICER: 'As an Onboarding Officer, you prepare and submit applications on behalf of transport operators and respond to requests for additional information.',
  REGISTRATION_SUPERVISOR: 'As an Onboarding Supervisor, you oversee every application, allocate reviews and resolve synchronisation conflicts.',
  VERIFICATION_OFFICER: 'As a Compliance Officer, you review submitted applications, verify registry records with PACRA and RTSA, and approve or decline each application.',
  PLATFORM_ADMINISTRATOR: 'As a Platform Administrator, you manage staff access and oversee the audit trail.',
}

/** Plain-language names for the requirement codes the API returns. */
export function requirementLabel(code: string) {
  const fixed: Record<string, string> = {
    'registryCheck.PACRA': 'PACRA registry verification showing a match',
    'registryCheck.RTSA': 'RTSA licence verification showing a match',
    'evidence.certificateOfIncorporation': 'Certificate of Incorporation',
    'evidence.operatingLicence': 'Road Service Licence',
    'representatives.atLeastOne': 'at least one authorised representative',
    'representatives.primaryContact': 'a primary contact',
    'company.operatingLicenceNumber': 'Road Service Licence number',
    'company.registrationNumber': 'PACRA registration number',
  }
  if (fixed[code]) return fixed[code]
  if (/^evidence\[.+\]\.assessment$/.test(code)) return 'a review of every supporting document'
  if (/^evidence\[.+\]\.notAccepted$/.test(code)) return 'replacement of a document that was not accepted'
  if (/^duplicate\[.+\]\.review$/.test(code)) return 'a review of a possible duplicate'
  return code
}

/** One sentence per role the user holds, so they know when work will appear. */
export function roleIntro(roles: string[]) {
  return roles.map((role) => roleIntros[role]).filter(Boolean).join(' ')
}
