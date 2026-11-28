import { useEffect, useState } from 'react'
import {
  Alert, Anchor, Badge, Button, Divider, Group, Loader, Modal, Paper, Stack, Stepper, Table, Text, Textarea, Title,
} from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { api } from '../api/client'
import type {
  Assessment, Company, CompanyDetails, DecisionSummary, Evidence, InformationRequest, Registration, RegistrySource,
  Representative, VerificationSummary,
} from '../api/types'
import {
  ApiError, CenterMessage, StatusBadge, actionLabel, evidenceTypeLabels, formatDate, formatDateTime,
  requirementLabel, statusLabels, useStaffNames,
} from '../components/common'
import {
  ApproveDialog, AssessEvidenceDialog, AssignVerifierDialog, DuplicateReviewDialog, ProvideInformationDialog,
  RegistryCheckDialog, RejectDialog, RequestInformationDialog, WithdrawDialog, useCommand,
} from '../components/commands'
import { DetailsFields, emptyDetails, normalizeDetails } from '../components/DetailsFields'
import { DraftChecklist, EvidenceUploadDialog, RemoveDialog, RepresentativeDialog, SignatureDialog } from '../components/capture'
import { DocumentPreview } from '../components/DocumentPreview'
import { SnapshotView } from './SyncPage'

type Dialog =
  | { kind: 'assign' | 'requestInformation' | 'approve' | 'reject' | 'withdraw' | 'audit' }
  | { kind: 'registryCheck'; source: RegistrySource }
  | { kind: 'assess'; evidence: Evidence }
  | { kind: 'provide'; request: InformationRequest }
  | { kind: 'duplicate'; flagId: string; matchedReference: string }
  | { kind: 'representative'; representative: Representative | null }
  | { kind: 'sign'; representative: Representative }
  | { kind: 'upload' }
  | { kind: 'preview'; evidence: Evidence }
  | { kind: 'remove'; target: { kind: 'representative' | 'evidence'; id: string; label: string } }

/** Registrations before submission carry no verification or decision records. */
const verificationStarted = (r: Registration) => r.status !== 'DRAFT' && r.status !== 'SUBMITTED'

export function RegistrationPage({ token }: { token: string }) {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [approvedCompany, setApprovedCompany] = useState<Company | null>(null)
  const close = () => setDialog(null)

  const registrationQuery = useQuery({ queryKey: ['registration', id], queryFn: () => api.getRegistration(token, id), enabled: !!id })
  const registration = registrationQuery.data
  const verification = useQuery({
    queryKey: ['verification', id], queryFn: () => api.verification(token, id),
    enabled: !!registration && verificationStarted(registration),
  })
  const decision = useQuery({
    queryKey: ['decision', id], queryFn: () => api.decision(token, id),
    enabled: !!registration && ['RECOMMENDED', 'APPROVED', 'REJECTED', 'UNDER_VERIFICATION'].includes(registration.status),
  })
  const duplicates = useQuery({
    queryKey: ['duplicate-flags', id], queryFn: () => api.duplicateFlags(token, id),
    enabled: !!registration && registration.status !== 'DRAFT',
  })
  const companyId = decision.data?.decision?.companyId
  const company = useQuery({
    queryKey: ['company', companyId], queryFn: () => api.getCompany(token, companyId!),
    enabled: !!companyId && !approvedCompany, retry: false,
  })

  const v = verification.data
  const d = decision.data
  const name = useStaffNames(token, [
    registration?.capturedBy, v?.assignedVerifier,
    ...(v?.assignments.flatMap((a) => [a.assignee, a.assignedBy]) ?? []),
    ...(v?.assessments.map((a) => a.assessedBy) ?? []),
    ...(v?.informationRequests.flatMap((r) => [r.requestedBy, r.respondedBy]) ?? []),
    ...(v?.registryChecks.map((c) => c.checkedBy) ?? []),
    d?.decision?.decidedBy,
    ...(registration?.evidence.map((e) => e.uploadedBy) ?? []),
  ])

  if (registrationQuery.isPending) return <CenterMessage title="Loading application" detail="Retrieving the latest version…" />
  if (registrationQuery.error || !registration) {
    return <CenterMessage title="This application is unavailable" detail={registrationQuery.error?.message || 'It may not exist, or it is not accessible with your current role.'} />
  }

  const allowed = new Set(registration.allowedActions)
  const reload = () => queryClient.invalidateQueries({ queryKey: ['registration', id] })
  const openRequest = v?.informationRequests.find((r) => r.status === 'OPEN') ?? null
  const shownCompany = approvedCompany ?? company.data ?? null

  return (
    <Stack gap="lg">
      <div className="eyebrow"><Link to="/">Applications</Link> / {registration.reference}</div>
      <Group justify="space-between" align="flex-start" className="registration-heading">
        <div>
          <Group gap="sm" align="center">
            <Title order={1} className="page-title">{registration.details.legalName || 'New application'}</Title>
            <StatusBadge status={registration.status} />
          </Group>
          <Text c="dimmed" mt={6}>
            {registration.reference}<span className="dot-separator">·</span>Prepared by {name(registration.capturedBy)}
            <span className="dot-separator">·</span>Last updated {formatDateTime(registration.updatedAt)}
          </Text>
        </div>
        <Group gap="xs">
          {allowed.has('viewAudit') && <Button variant="default" radius="xl" onClick={() => setDialog({ kind: 'audit' })}>Audit trail</Button>}
          {allowed.has('withdraw') && <Button color="red" variant="light" radius="xl" onClick={() => setDialog({ kind: 'withdraw' })}>Withdraw application</Button>}
          <PrimaryActions registration={registration} allowed={allowed} hasVerifier={!!v?.assignedVerifier} onOpen={setDialog} token={token} />
        </Group>
      </Group>

      <Progress registration={registration} />

      {registration.status === 'DRAFT' && <DraftChecklist registration={registration} />}
      {openRequest && (
        <Alert color="orange" variant="light" title={`Additional information requested by ${name(openRequest.requestedBy)} · due ${formatDate(openRequest.dueDate)}`}>
          <Text size="sm">{openRequest.description}</Text>
          {allowed.has('provideInformation') && (
            <Button size="xs" mt="sm" color="orange" onClick={() => setDialog({ kind: 'provide', request: openRequest })}>Submit information</Button>
          )}
        </Alert>
      )}
      {allowed.has('resolveSyncConflict') && <ConflictPanel token={token} registration={registration} name={name} />}
      {d?.decision && <DecisionBanner decision={d} company={shownCompany} name={name} />}

      <div className="detail-grid">
        <Stack gap="lg">
          <DetailsPanel token={token} registration={registration} editable={allowed.has('editDraft')} onReload={reload} />
          <RepresentativesPanel registration={registration} editable={allowed.has('editDraft')} onOpen={setDialog} />
          <EvidencePanel token={token} registration={registration} assessments={v?.assessments ?? []} name={name}
            canAssess={allowed.has('verify')} onAssess={(evidence) => setDialog({ kind: 'assess', evidence })}
            editable={allowed.has('editDraft')} onOpen={setDialog} />
          {(duplicates.data?.length ?? 0) > 0 && (
            <Paper className="panel" radius="lg" p="xl">
              <Text fw={700} size="lg">Potential duplicates</Text>
              <Text size="sm" c="dimmed" mt={4} mb="md">Identified automatically at submission. Each requires a conclusion from the Compliance Officer.</Text>
              <Stack gap="sm">
                {duplicates.data!.map((flag) => {
                  const open = v?.unreviewedDuplicateFlags.includes(flag.id)
                  return (
                    <Group key={flag.id} justify="space-between" className="list-row">
                      <div>
                        <Anchor component={Link} to={`/registrations/${flag.matchedId}`} fw={600} size="sm">{flag.matchedReference}</Anchor>
                        <Text size="xs" c="dimmed">{flag.matchReason}</Text>
                      </div>
                      {open ? (allowed.has('verify')
                        ? <Button size="xs" variant="light" color="orange" onClick={() => setDialog({ kind: 'duplicate', flagId: flag.id, matchedReference: flag.matchedReference })}>Review</Button>
                        : <Badge color="orange" variant="light">Pending review</Badge>)
                        : <Badge color="gray" variant="light">Reviewed</Badge>}
                    </Group>
                  )
                })}
              </Stack>
            </Paper>
          )}
        </Stack>

        <Stack gap="lg">
          {verificationStarted(registration) && (
            <VerificationPanel summary={v} loading={verification.isPending} error={verification.error} name={name}
              canVerify={allowed.has('verify')} onCheck={(source) => setDialog({ kind: 'registryCheck', source })} />
          )}
          <Paper className="panel" radius="lg" p="xl">
            <Text fw={700} size="lg">What happens next</Text>
            <Alert variant="light" color="sky" mt="md">{nextStep(registration, v, name)}</Alert>
            <Text fw={600} size="sm" mt="lg">What you can do</Text>
            {registration.allowedActions.filter((a) => a !== 'viewAudit').length
              ? <Stack gap="xs" mt="xs">{registration.allowedActions.filter((a) => a !== 'viewAudit').map((action) =>
                  <div className="action-line" key={action}><span className="action-check">→</span><Text size="sm">{actionLabel(action)}</Text></div>)}</Stack>
              : <Text size="sm" c="dimmed" mt={4}>Nothing is needed from you at this stage.</Text>}
            <Text size="xs" c="dimmed" mt="xs">Depends on your role and on who has already worked on this application.</Text>
            <Divider my="lg" />
            <Text fw={600} size="sm">About this application</Text>
            <dl className="fact-list">
              <dt>Reference</dt><dd>{registration.reference}</dd>
              <dt>How it arrived</dt><dd>{channelLabel(registration.channel)}</dd>
              <dt>{registration.channel === 'SELF_SERVICE' ? 'Applicant' : 'Prepared by'}</dt><dd>{name(registration.capturedBy)}</dd>
              <dt>Started</dt><dd>{formatDateTime(registration.createdAt)}</dd>
              <dt>Last updated</dt><dd>{formatDateTime(registration.updatedAt)}</dd>
              <dt>Documents</dt><dd>{registration.evidence.length}</dd>
            </dl>
          </Paper>
        </Stack>
      </div>

      {dialog?.kind === 'assign' && <AssignVerifierDialog token={token} registration={registration} opened onClose={close} currentVerifier={v?.assignedVerifier ?? null} />}
      {dialog?.kind === 'assess' && <AssessEvidenceDialog token={token} registration={registration} evidence={dialog.evidence} opened onClose={close} />}
      {dialog?.kind === 'duplicate' && <DuplicateReviewDialog token={token} registration={registration} flagId={dialog.flagId} matchedReference={dialog.matchedReference} opened onClose={close} />}
      {dialog?.kind === 'requestInformation' && <RequestInformationDialog token={token} registration={registration} opened onClose={close} />}
      {dialog?.kind === 'provide' && <ProvideInformationDialog token={token} registration={registration} request={dialog.request} opened onClose={close} />}
      {dialog?.kind === 'registryCheck' && <RegistryCheckDialog token={token} registration={registration} source={dialog.source} opened onClose={close} />}
      {dialog?.kind === 'approve' && <ApproveDialog token={token} registration={registration} opened onClose={close} onApproved={setApprovedCompany} />}
      {dialog?.kind === 'reject' && <RejectDialog token={token} registration={registration} opened onClose={close} />}
      {dialog?.kind === 'withdraw' && <WithdrawDialog token={token} registration={registration} opened onClose={close} />}
      {dialog?.kind === 'representative' && <RepresentativeDialog token={token} registration={registration} representative={dialog.representative} opened onClose={close} />}
      {dialog?.kind === 'sign' && <SignatureDialog token={token} registration={registration} representative={dialog.representative} opened onClose={close} />}
      {dialog?.kind === 'upload' && <EvidenceUploadDialog token={token} registration={registration} opened onClose={close} />}
      {dialog?.kind === 'remove' && <RemoveDialog token={token} registration={registration} target={dialog.target} opened onClose={close} />}
      {dialog?.kind === 'preview' && <DocumentPreview token={token} registrationId={registration.id} evidence={dialog.evidence} onClose={close} />}
      {dialog?.kind === 'audit' && <AuditModal token={token} id={id} onClose={close} name={name} />}
    </Stack>
  )
}

/** The buttons for the next step, shown only when the server lists the action (AC-R1-DEC-06). */
function PrimaryActions({ token, registration, allowed, hasVerifier, onOpen }: {
  token: string; registration: Registration; allowed: Set<string>; hasVerifier: boolean; onOpen: (dialog: Dialog) => void
}) {
  const submit = useCommand(registration.id)
  const take = useCommand(registration.id)
  return (
    <>
      {allowed.has('submit') && <Button color="teal" radius="xl" loading={submit.isPending}
        onClick={() => submit.mutate(() => api.submit(token, registration))}>Submit application</Button>}
      {allowed.has('takeVerification') && <Button color="teal" radius="xl" loading={take.isPending}
        onClick={() => take.mutate(() => api.takeVerification(token, registration))}>Assign to me</Button>}
      {allowed.has('assignVerification') && <Button color="teal" radius="xl" variant="light"
        onClick={() => onOpen({ kind: 'assign' })}>{hasVerifier ? 'Reassign review' : 'Assign review'}</Button>}
      {allowed.has('requestInformation') && <Button color="orange" variant="light" radius="xl" onClick={() => onOpen({ kind: 'requestInformation' })}>Request information</Button>}
      {allowed.has('reject') && <Button color="red" variant="light" radius="xl" onClick={() => onOpen({ kind: 'reject' })}>Decline</Button>}
      {allowed.has('approve') && <Button color="green" radius="xl" onClick={() => onOpen({ kind: 'approve' })}>Approve</Button>}
      {submit.error && <ApiError error={submit.error} />}
      {take.error && <ApiError error={take.error} />}
    </>
  )
}

function Progress({ registration }: { registration: Registration }) {
  const s = registration.status
  const active = { DRAFT: 0, SUBMITTED: 1, UNDER_VERIFICATION: 2, AWAITING_INFORMATION: 2, RECOMMENDED: 2, APPROVED: 4, REJECTED: 4, WITHDRAWN: -1 }[s]
  if (active < 0) return <Alert color="gray" variant="light" title="Application withdrawn">This application has been withdrawn. No further action is possible; its full history is retained.</Alert>
  return (
    <Paper className="panel" radius="lg" p="lg">
      <Stepper active={active} size="sm" color={s === 'REJECTED' ? 'red' : 'teal'} allowNextStepsSelect={false}>
        <Stepper.Step label="Application" description="Preparation" />
        <Stepper.Step label="Submitted" description="Awaiting review" />
        <Stepper.Step label="Compliance review" description={s === 'AWAITING_INFORMATION' ? 'Information requested' : 'Documents and registries'} />
        <Stepper.Step label="Decision" description={s === 'APPROVED' || s === 'REJECTED' ? statusLabels[s] : 'Approve or decline'} />
      </Stepper>
    </Paper>
  )
}

function DecisionBanner({ decision, company, name }: { decision: DecisionSummary; company: Company | null; name: (id: string | null | undefined) => string }) {
  const result = decision.decision!
  if (result.outcome === 'APPROVED') {
    return (
      <Alert color="green" variant="light" title={`Approved by ${name(result.decidedBy)} on ${formatDate(result.decidedAt)}`}>
        {company
          ? <Text size="sm">Company established as <b>{company.companyCode}</b> · {company.operationalStatus === 'PENDING_ACTIVATION' ? 'pending activation' : company.operationalStatus.replaceAll('_', ' ').toLowerCase()}. The primary contact has been invited to activate the account.</Text>
          : <Text size="sm">The company has been established and the primary contact invited to activate the account.</Text>}
        <Text size="sm" mt={4} c="dimmed">Rationale: {result.reasons}</Text>
      </Alert>
    )
  }
  return (
    <Alert color="red" variant="light" title={`Declined by ${name(result.decidedBy)} on ${formatDate(result.decidedAt)}`}>
      <Text size="sm">{result.reasons}</Text>
      {result.appealDeadline && <Text size="sm" mt={4}>Appeal window closes: <b>{formatDate(result.appealDeadline)}</b></Text>}
    </Alert>
  )
}

function DetailsPanel({ token, registration, editable, onReload }: {
  token: string; registration: Registration; editable: boolean; onReload: () => void
}) {
  const queryClient = useQueryClient()
  const [details, setDetails] = useState<CompanyDetails>(emptyDetails)
  useEffect(() => { setDetails({ ...emptyDetails, ...registration.details }) }, [registration.id, registration.version])
  const save = useMutation({
    mutationFn: () => api.updateDetails(token, registration, normalizeDetails(details)),
    onSuccess: (updated) => {
      queryClient.setQueryData(['registration', registration.id], updated)
      queryClient.invalidateQueries({ queryKey: ['registrations'] })
    },
  })
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Group justify="space-between" mb="lg">
        <div><Text fw={700} size="lg">Company details</Text>
          <Text c="dimmed" size="sm">{editable ? 'Every change is recorded in the application history.' : 'Read-only at this stage.'}</Text></div>
        {editable && <Badge color="teal" variant="light">Editable</Badge>}
      </Group>
      <DetailsFields value={details} onChange={setDetails} disabled={!editable} />
      {save.error && <ApiError error={save.error} onReload={onReload} />}
      {editable && <Group justify="flex-end" mt="lg"><Button color="teal" radius="xl" onClick={() => save.mutate()} loading={save.isPending}>Save changes</Button></Group>}
    </Paper>
  )
}

function RepresentativesPanel({ registration, editable, onOpen }: {
  registration: Registration; editable: boolean; onOpen: (dialog: Dialog) => void
}) {
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Group justify="space-between" align="flex-start" mb="md">
        <div>
          <Text fw={700} size="lg">Authorised representatives</Text>
          <Text size="sm" c="dimmed" mt={4}>Individuals authorised to act for the company, and the status of their declarations.</Text>
        </div>
        {editable && <Button size="xs" radius="xl" variant="light" onClick={() => onOpen({ kind: 'representative', representative: null })}>Add representative</Button>}
      </Group>
      {registration.representatives.length === 0
        ? <Text size="sm" c="dimmed">No representatives added.{editable && ' Begin with a director or another person authorised to sign for the company.'}</Text>
        : (
          <Stack gap="sm">
            {registration.representatives.map((rep) => {
              const ack = rep.acknowledgement
              const needsSignature = rep.acknowledgementRequired && ack?.outcome !== 'ACKNOWLEDGED'
              return (
                <Group key={rep.id} justify="space-between" className="list-row" wrap="nowrap" align="flex-start">
                  <div>
                    <Group gap={6}><Text fw={600} size="sm">{rep.fullName}</Text>{rep.primaryContact && <Badge size="xs" variant="light">Primary contact</Badge>}</Group>
                    <Text size="xs" c="dimmed">{[rep.capacity, rep.idDocumentType && `${rep.idDocumentType} ${rep.idDocumentNumber ?? ''}`, rep.phone, rep.email].filter(Boolean).join(' · ') || 'No details yet'}</Text>
                    {ack?.outcome === 'REFUSED' && ack.refusalReason && <Text size="xs" c="var(--th-danger-text)" mt={2}>Declined to sign: {ack.refusalReason}</Text>}
                    {editable && (
                      <Group gap="sm" mt={6}>
                        {needsSignature && (
                          <Button size="compact-xs" color="teal" onClick={() => onOpen({ kind: 'sign', representative: rep })}>
                            {ack?.outcome === 'REFUSED' ? 'Record signature' : 'Sign declaration'}
                          </Button>
                        )}
                        <Anchor component="button" size="xs" onClick={() => onOpen({ kind: 'representative', representative: rep })}>Edit</Anchor>
                        <Anchor component="button" size="xs" c="var(--th-danger-text)"
                          onClick={() => onOpen({ kind: 'remove', target: { kind: 'representative', id: rep.id, label: rep.fullName } })}>Remove</Anchor>
                      </Group>
                    )}
                  </div>
                  {!rep.acknowledgementRequired ? <Badge color="gray" variant="light">Signature not required</Badge>
                    : ack?.outcome === 'ACKNOWLEDGED' ? <Badge color="green" variant="light">Signed {formatDate(ack.recordedAt)}</Badge>
                    : ack?.outcome === 'REFUSED' ? <Badge color="red" variant="light">Declined to sign</Badge>
                    : <Badge color="orange" variant="light">Signature outstanding</Badge>}
                </Group>
              )
            })}
          </Stack>
        )}
    </Paper>
  )
}

/**
 * AC-R1-SYN-05/-06: a tablet and the server changed the same draft. Both versions are
 * shown side by side; the supervisor keeps one, with a reason. Everything else waits.
 */
function ConflictPanel({ token, registration, name }: { token: string; registration: Registration; name: (id: string) => string }) {
  const queryClient = useQueryClient()
  const conflicts = useQuery({ queryKey: ['sync-conflicts', registration.id], queryFn: () => api.syncConflicts(token, registration.id) })
  const [reason, setReason] = useState('')
  const open = conflicts.data?.find((c) => c.open)
  const resolve = useMutation({
    mutationFn: (choice: 'KEEP_SERVER' | 'USE_DEVICE') => api.resolveConflict(token, registration, open!.id, choice, reason.trim()),
    onSuccess: (updated) => {
      queryClient.setQueryData(['registration', registration.id], updated)
      void queryClient.invalidateQueries({ queryKey: ['sync-conflicts', registration.id] })
      setReason('')
    },
  })
  if (!open) return null
  return (
    <Paper className="panel request-open" radius="lg" p="xl">
      <Text fw={700} size="lg">Two versions of this draft</Text>
      <Text size="sm" c="dimmed" mt={4}>
        A tablet ({open.deviceId}, {name(open.deviceUser)}) and the server ({name(open.serverEditor)}) both changed this draft
        on {formatDateTime(open.detectedAt)}. Choose which version to keep. Until then, the draft cannot be changed or submitted.
      </Text>
      <div className="conflict-columns">
        <div><Text fw={600} size="sm">On the server now</Text><SnapshotView snapshot={open.serverSnapshot} /></div>
        <div><Text fw={600} size="sm">From the tablet</Text><SnapshotView snapshot={open.deviceSnapshot} /></div>
      </div>
      <Textarea mt="md" label="Reason for your choice" required value={reason} onChange={(e) => setReason(e.currentTarget.value)}
        placeholder="e.g. The tablet has the corrected address" autosize minRows={2} maxLength={2000} />
      {resolve.error && <ApiError error={resolve.error} />}
      <Group justify="flex-end" mt="md">
        <Button radius="xl" variant="default" disabled={!reason.trim()} loading={resolve.isPending && resolve.variables === 'KEEP_SERVER'} onClick={() => resolve.mutate('KEEP_SERVER')}>Keep the server version</Button>
        <Button radius="xl" color="teal" disabled={!reason.trim()} loading={resolve.isPending && resolve.variables === 'USE_DEVICE'} onClick={() => resolve.mutate('USE_DEVICE')}>Use the tablet version</Button>
      </Group>
    </Paper>
  )
}

/** How the application reached TransitHub, in plain words. */
function channelLabel(channel: string) {
  return ({ SELF_SERVICE: 'Applied online by the company', ONLINE: 'Entered by our team at the office', FIELD: 'Captured in the field on a tablet' } as Record<string, string>)[channel] ?? channel
}

/** Who acts next, so no one is left wondering why they have no actions. */
function nextStep(r: Registration, v: VerificationSummary | undefined, name: (id: string | null | undefined) => string) {
  switch (r.status) {
    case 'DRAFT': return `Awaiting completion and submission by ${name(r.capturedBy)}.`
    case 'SUBMITTED': return 'Awaiting assignment to a Compliance Officer.'
    case 'UNDER_VERIFICATION': return v?.assignedVerifier ? `Under review by ${name(v.assignedVerifier)}.` : 'Under compliance review.'
    case 'AWAITING_INFORMATION': return `Awaiting the requested information from ${name(r.capturedBy)}.`
    case 'RECOMMENDED': return 'Awaiting a decision under the previous review process.'
    case 'APPROVED': return 'Approved. The company has been established and its primary contact invited to activate the account.'
    case 'REJECTED': return 'Declined. The applicant may lodge an appeal before the appeal window closes.'
    case 'WITHDRAWN': return 'Withdrawn. No further action is possible.'
  }
}

const assessmentColor = { ACCEPTED: 'green', INSUFFICIENT: 'orange', INVALID: 'red' } as const
const assessmentLabel = { ACCEPTED: 'Accepted', INSUFFICIENT: 'Insufficient', INVALID: 'Invalid' } as const

function EvidencePanel({ token, registration, assessments, name, canAssess, onAssess, editable, onOpen }: {
  token: string; registration: Registration; assessments: Assessment[]; name: (id: string) => string
  canAssess: boolean; onAssess: (evidence: Evidence) => void; editable: boolean; onOpen: (dialog: Dialog) => void
}) {
  const [downloadError, setDownloadError] = useState<Error | null>(null)
  const byEvidence = new Map(assessments.map((a) => [a.evidenceId, a]))
  const download = (e: Evidence) => {
    setDownloadError(null)
    api.downloadEvidence(token, registration.id, e.id, e.fileName).catch((error: Error) => setDownloadError(error))
  }
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Group justify="space-between" align="flex-start" mb="md">
        <div>
          <Text fw={700} size="lg">Supporting documents</Text>
          <Text size="sm" c="dimmed" mt={4}>PDF, JPEG or PNG, up to 10 MB each. All documents are encrypted at rest.</Text>
        </div>
        {editable && <Button size="xs" radius="xl" variant="light" onClick={() => onOpen({ kind: 'upload' })}>Upload document</Button>}
      </Group>
      {registration.evidence.length === 0 ? <Text size="sm" c="dimmed">No documents uploaded.{editable && ' Required: Certificate of Incorporation (PACRA) and Road Service Licence (RTSA).'}</Text> : (
        <Table.ScrollContainer minWidth={560}>
          <Table verticalSpacing="xs">
            <Table.Thead><Table.Tr><Table.Th>Document</Table.Th><Table.Th>Uploaded by</Table.Th><Table.Th>Review outcome</Table.Th><Table.Th /></Table.Tr></Table.Thead>
            <Table.Tbody>
              {registration.evidence.map((e) => {
                const assessment = byEvidence.get(e.id)
                return (
                  <Table.Tr key={e.id}>
                    <Table.Td>
                      <Anchor component="button" size="sm" fw={600} onClick={() => onOpen({ kind: 'preview', evidence: e })}>{e.fileName}</Anchor>
                      <Text size="xs" c="dimmed">{evidenceTypeLabels[e.evidenceType]} · {(e.sizeBytes / 1024).toFixed(0)} KB</Text>
                    </Table.Td>
                    <Table.Td><Text size="xs">{name(e.uploadedBy)}</Text><Text size="xs" c="dimmed">{formatDate(e.uploadedAt)}</Text></Table.Td>
                    <Table.Td>
                      {assessment
                        ? <><Badge color={assessmentColor[assessment.outcome]} variant="light">{assessmentLabel[assessment.outcome]}</Badge>
                          {assessment.findings && <Text size="xs" c="dimmed" mt={4} lineClamp={2}>{assessment.findings}</Text>}</>
                        : <Text size="xs" c="dimmed">Pending review</Text>}
                    </Table.Td>
                    <Table.Td ta="right">
                      <Group gap="xs" justify="flex-end" wrap="nowrap">
                      <Button size="xs" variant="default" onClick={() => onOpen({ kind: 'preview', evidence: e })}>View</Button>
                      <Anchor component="button" size="xs" onClick={() => download(e)}>Download</Anchor>
                      {canAssess && <Button size="xs" variant="light" color="teal" onClick={() => onAssess(e)}>{assessment ? 'Update review' : 'Review'}</Button>}
                      {editable && <Anchor component="button" size="xs" c="var(--th-danger-text)" onClick={() => onOpen({ kind: 'remove', target: { kind: 'evidence', id: e.id, label: e.fileName } })}>Remove</Anchor>}
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                )
              })}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
      {downloadError && <ApiError error={downloadError} />}
    </Paper>
  )
}

const registryNames: Record<RegistrySource, string> = {
  PACRA: 'PACRA company registry', RTSA: 'RTSA Road Service Licence', ZRA: 'ZRA tax clearance', OTHER: 'Other registry',
}
const registryResultLabel = { MATCH: 'Verified', MISMATCH: 'Details do not match', NOT_FOUND: 'Not found', COULD_NOT_CHECK: 'Unable to verify' } as const
const registryResultColor = { MATCH: 'green', MISMATCH: 'red', NOT_FOUND: 'red', COULD_NOT_CHECK: 'orange' } as const

function VerificationPanel({ summary, loading, error, name, canVerify, onCheck }: {
  summary: VerificationSummary | undefined; loading: boolean; error: Error | null; name: (id: string | null | undefined) => string
  canVerify: boolean; onCheck: (source: RegistrySource) => void
}) {
  const latest = new Map((summary?.registryChecks ?? []).map((c) => [c.source, c]))
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Text fw={700} size="lg">Compliance review</Text>
      {loading ? <Loader size="sm" mt="md" /> : error ? <ApiError error={error} /> : summary && (
        <>
          <Text size="sm" mt="xs">Compliance Officer: <b>{summary.assignedVerifier ? name(summary.assignedVerifier) : 'Not yet assigned'}</b></Text>

          <Text size="xs" c="dimmed" tt="uppercase" fw={700} mt="lg">Registry verification</Text>
          <Stack gap={8} mt={8}>
            {(['PACRA', 'RTSA', 'ZRA'] as RegistrySource[]).map((source) => {
              const check = latest.get(source)
              return (
                <Group key={source} justify="space-between" wrap="nowrap" className="list-row" align="flex-start">
                  <div>
                    <Text size="sm" fw={600}>{registryNames[source]}{source === 'ZRA' && <Text span size="xs" c="dimmed"> · optional</Text>}</Text>
                    {check
                      ? <Text size="xs" c="dimmed">{check.reference} · {name(check.checkedBy)}, {formatDate(check.checkedAt)}{check.note ? ` · ${check.note}` : ''}</Text>
                      : <Text size="xs" c="dimmed">Not yet verified</Text>}
                  </div>
                  <Group gap={6} wrap="nowrap">
                    {check && <Badge size="sm" color={registryResultColor[check.result]} variant="light">{registryResultLabel[check.result]}</Badge>}
                    {canVerify && <Button size="compact-xs" variant="light" color="teal" onClick={() => onCheck(source)}>{check ? 'Re-verify' : 'Verify'}</Button>}
                  </Group>
                </Group>
              )
            })}
          </Stack>

          {summary.outstandingForApproval.length > 0 ? (
            <Alert variant="light" color="orange" mt="md" title="Requirements before approval">
              <ul className="requirement-list">{summary.outstandingForApproval.map((code) => <li key={code}>{requirementLabel(code)}</li>)}</ul>
            </Alert>
          ) : (
            <Alert variant="light" color="green" mt="md" title="Ready for a decision">All verifications are complete.</Alert>
          )}

          {summary.informationRequests.length > 0 && (
            <Stack gap={6} mt="lg">
              <Text size="xs" c="dimmed" tt="uppercase" fw={700}>Information requests</Text>
              {summary.informationRequests.map((r) => (
                <div key={r.id}>
                  <Group gap={6}><Badge size="xs" color={r.status === 'OPEN' ? 'orange' : 'gray'} variant="light">{r.status === 'OPEN' ? 'Open' : 'Answered'}</Badge>
                    <Text size="xs" c="dimmed">Due {formatDate(r.dueDate)}</Text></Group>
                  <Text size="xs" mt={2}>{r.description}</Text>
                  {r.response && <Text size="xs" c="dimmed" mt={2}>Response from {name(r.respondedBy)}: {r.response}</Text>}
                </div>
              ))}
            </Stack>
          )}
          {summary.assignments.length > 0 && (
            <Stack gap={6} mt="lg">
              <Text size="xs" c="dimmed" tt="uppercase" fw={700}>Assignment history</Text>
              {summary.assignments.map((a) => (
                <Text size="xs" key={a.id}>
                  {name(a.assignee)}, assigned by {name(a.assignedBy)} on {formatDate(a.assignedAt)}{a.reason ? ` (${a.reason})` : ''}
                  {a.endedAt && <Text span c="dimmed"> · ended {formatDate(a.endedAt)}</Text>}
                </Text>
              ))}
            </Stack>
          )}
        </>
      )}
    </Paper>
  )
}

function AuditModal({ token, id, onClose, name }: { token: string; id: string; onClose: () => void; name: (id: string) => string }) {
  const query = useQuery({ queryKey: ['audit', id], queryFn: () => api.audit(token, id) })
  const actorName = useStaffNames(token, query.data?.events.map((e) => e.actorId) ?? [])
  return (
    <Modal opened onClose={onClose} title="Audit trail" size="xl" centered radius="lg">
      {query.isPending ? <Loader size="sm" /> : query.error ? <ApiError error={query.error} /> : (
        <>
          <Text size="sm" c="dimmed" mb="md">{query.data.total} recorded events, most recent first</Text>
          <Table.ScrollContainer minWidth={680}>
            <Table striped highlightOnHover>
              <Table.Thead><Table.Tr><Table.Th>Date and time</Table.Th><Table.Th>Event</Table.Th><Table.Th>User</Table.Th><Table.Th>Reason</Table.Th></Table.Tr></Table.Thead>
              <Table.Tbody>{query.data.events.map((event) => (
                <Table.Tr key={event.id}>
                  <Table.Td>{formatDateTime(event.occurredAt)}</Table.Td>
                  <Table.Td><Text size="sm">{auditEventLabel(event.action)}</Text></Table.Td>
                  <Table.Td>{actorName(event.actorId) || name(event.actorId)}</Table.Td>
                  <Table.Td>{event.reason || '—'}</Table.Td>
                </Table.Tr>
              ))}</Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          {query.data.events.length === 0 && <Text size="sm" c="dimmed" ta="center" py="lg">No events have been recorded.</Text>}
        </>
      )}
    </Modal>
  )
}

const auditEvents: Record<string, string> = {
  'registration.started': 'Application created',
  'registration.synced': 'Application synchronised from a field device',
  'registration.details-updated': 'Company details updated',
  'registration.representative-added': 'Representative added',
  'registration.representative-updated': 'Representative updated',
  'registration.representative-removed': 'Representative removed',
  'registration.evidence-attached': 'Supporting document uploaded',
  'registration.evidence-removed': 'Supporting document removed',
  'registration.acknowledged': 'Declaration signed',
  'registration.acknowledgement-refused': 'Declaration declined',
  'registration.submitted': 'Application submitted',
  'registration.verifier-assigned': 'Compliance Officer assigned',
  'registration.evidence-assessed': 'Supporting document reviewed',
  'registration.duplicate-reviewed': 'Potential duplicate reviewed',
  'registration.registry-checked': 'Registry verification recorded',
  'registration.information-requested': 'Additional information requested',
  'registration.requested-evidence-attached': 'Requested document uploaded',
  'registration.information-provided': 'Requested information submitted',
  'registration.approved': 'Application approved',
  'registration.rejected': 'Application declined',
  'registration.withdrawn': 'Application withdrawn',
  'registration.recommended': 'Recommendation recorded (previous process)',
  'registration.recommendation-endorsed': 'Recommendation endorsed (previous process)',
  'registration.recommendation-challenged': 'Recommendation challenged (previous process)',
}

/** Readable names for audit events; unknown events fall back to a tidied code. */
function auditEventLabel(action: string) {
  if (auditEvents[action]) return auditEvents[action]
  const words = action.split('.').pop()!.replaceAll('-', ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}
