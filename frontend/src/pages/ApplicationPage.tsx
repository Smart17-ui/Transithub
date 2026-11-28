import { useEffect, useState } from 'react'
import {
  Alert, Anchor, Badge, Button, Group, Modal, Paper, Stack, Text, Textarea, ThemeIcon, Title,
} from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { APPLICATIONS, api } from '../api/client'
import type { Application, CompanyDetails, Evidence, Registration, RegistrationStatus, Representative } from '../api/types'
import { ApiError, CenterMessage, evidenceTypeLabels, formatDate, formatDateTime } from '../components/common'
import { DetailsFields, emptyDetails, normalizeDetails } from '../components/DetailsFields'
import { DocumentPreview } from '../components/DocumentPreview'
import { EvidenceUploadDialog, RemoveDialog, RepresentativeDialog, SignatureDialog, draftSteps } from '../components/capture'

/**
 * "My application" (Release 1 §9): a bus company's owner, director or manager
 * prepares, submits and follows their own application. Plain language only:
 * no internal roles, names of checkers or notes (SS-7).
 */

type Dialog =
  | { kind: 'representative'; representative: Representative | null }
  | { kind: 'sign'; representative: Representative }
  | { kind: 'upload' }
  | { kind: 'preview'; evidence: Evidence }
  | { kind: 'remove'; target: { kind: 'representative' | 'evidence'; id: string; label: string } }
  | { kind: 'submit' | 'takeBack' | 'withdraw' }

/** What the applicant sees for each stage. */
const stage: Record<RegistrationStatus, { label: string; color: string; text: string }> = {
  DRAFT: { label: 'In preparation', color: 'gray', text: 'Complete the four steps below, then submit. Your changes are saved as you go.' },
  SUBMITTED: { label: 'Submitted', color: 'blue', text: 'We have your application. A member of our team will start checking it soon.' },
  UNDER_VERIFICATION: { label: 'Being checked', color: 'blue', text: 'Our team is checking your company with PACRA and RTSA and reviewing your documents.' },
  AWAITING_INFORMATION: { label: 'More information needed', color: 'orange', text: 'We need something more from you. Our team will contact you with the details.' },
  RECOMMENDED: { label: 'Being checked', color: 'blue', text: 'Our checks are nearly complete. You will hear from us soon.' },
  APPROVED: { label: 'Approved', color: 'green', text: 'Congratulations: your company is approved on TransitHub.' },
  REJECTED: { label: 'Declined', color: 'red', text: 'We could not approve this application. The reasons are below.' },
  WITHDRAWN: { label: 'Withdrawn', color: 'gray', text: 'You withdrew this application.' },
}

const progressSteps = ['Prepare', 'Submitted', 'Being checked', 'Decision'] as const
function progressIndex(status: RegistrationStatus) {
  switch (status) {
    case 'DRAFT': return 0
    case 'SUBMITTED': return 1
    case 'UNDER_VERIFICATION': case 'AWAITING_INFORMATION': case 'RECOMMENDED': return 2
    default: return 3
  }
}

/** The server's checklist, in the applicant's words. */
const stepTitles: Record<string, string> = {
  company: 'Company details', representatives: 'Directors and owners', evidence: 'Documents', signatures: 'Signatures',
}

const withBase = (data: Application): Application & Registration =>
  ({ ...data, basePath: APPLICATIONS, channel: 'SELF_SERVICE', capturedBy: '', contentRevision: 0 })

export function ApplicationPage({ token }: { token: string }) {
  const { id = '' } = useParams()
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const close = () => setDialog(null)

  // Same cache key as the capture dialogs update; `select` marks it as a self-service application.
  const query = useQuery({
    queryKey: ['registration', id], queryFn: () => api.getApplication(token, id), enabled: !!id,
    select: (data) => withBase(data as Application),
  })
  const application = query.data

  if (query.isPending) return <CenterMessage title="Loading your application" detail="Just a moment…" />
  if (query.error || !application) {
    return <CenterMessage title="Application not found" detail="It may have been removed, or it belongs to another account." />
  }

  const can = (action: string) => application.allowedActions.includes(action)
  const editable = can('edit')
  const info = stage[application.status]

  return (
    <Stack gap="lg" className="application-page">
      <Anchor component={Link} to="/" size="sm">← My account</Anchor>

      <Paper className="panel application-hero" radius="lg" p="xl">
        <Group justify="space-between" align="flex-start" wrap="wrap" gap="md">
          <div>
            <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">Your application · {application.reference}</Text>
            <Title order={1} className="application-title">{application.details.legalName || 'Your bus company'}</Title>
            <Group gap="sm" mt="xs">
              <Badge color={info.color} variant="light" size="lg" radius="xl">{info.label}</Badge>
              <Text size="sm" c="dimmed">Last updated {formatDateTime(application.updatedAt)}</Text>
            </Group>
          </div>
          <Group gap="sm">
            {can('submit') && <Button radius="xl" color="teal" onClick={() => setDialog({ kind: 'submit' })}>Submit application</Button>}
            {can('takeBack') && <Button radius="xl" variant="light" color="teal" onClick={() => setDialog({ kind: 'takeBack' })}>Make changes</Button>}
            {can('withdraw') && <Button radius="xl" variant="subtle" color="red" onClick={() => setDialog({ kind: 'withdraw' })}>Withdraw</Button>}
          </Group>
        </Group>
        <Text mt="md">{info.text}</Text>
        <ProgressBar application={application} />
      </Paper>

      {application.status === 'REJECTED' && <DeclinedPanel application={application} />}
      {application.status === 'APPROVED' && (
        <Alert color="green" radius="lg" title="Your company is approved">
          Your company now has its TransitHub company code. The tools to run your company, starting with ticket sales,
          will appear in your account as they launch.
        </Alert>
      )}

      {application.status === 'DRAFT' && <Checklist application={application} />}

      <DetailsPanel token={token} application={application} editable={editable} />
      <PeoplePanel application={application} editable={editable} onOpen={setDialog} />
      <DocumentsPanel token={token} application={application} editable={editable} onOpen={setDialog} />
      <TimelinePanel application={application} />

      {dialog?.kind === 'representative' && <RepresentativeDialog token={token} registration={application} representative={dialog.representative} opened onClose={close} />}
      {dialog?.kind === 'sign' && <SignatureDialog token={token} registration={application} representative={dialog.representative} opened onClose={close} />}
      {dialog?.kind === 'upload' && <EvidenceUploadDialog token={token} registration={application} opened onClose={close} />}
      {dialog?.kind === 'preview' && <DocumentPreview token={token} registrationId={application.id} evidence={dialog.evidence} basePath={APPLICATIONS} onClose={close} />}
      {dialog?.kind === 'remove' && <RemoveDialog token={token} registration={application} target={dialog.target} opened onClose={close} />}
      {(dialog?.kind === 'submit' || dialog?.kind === 'takeBack' || dialog?.kind === 'withdraw') &&
        <StageDialog token={token} application={application} kind={dialog.kind} onClose={close} />}
    </Stack>
  )
}

function ProgressBar({ application }: { application: Application }) {
  const current = progressIndex(application.status)
  const closedEarly = application.status === 'WITHDRAWN'
  return (
    <ol className="application-progress" aria-label="Application progress">
      {progressSteps.map((label, i) => {
        const state = closedEarly ? (i === 0 ? 'done' : 'todo') : i < current ? 'done' : i === current ? 'current' : 'todo'
        return (
          <li key={label} className={`is-${state}`} aria-current={state === 'current' ? 'step' : undefined}>
            <span className="application-progress-dot" aria-hidden>{state === 'done' ? '✓' : i + 1}</span>
            <span>{i === 3 && ['APPROVED', 'REJECTED'].includes(application.status) ? stage[application.status].label : label}</span>
          </li>
        )
      })}
    </ol>
  )
}

function Checklist({ application }: { application: Application & Registration }) {
  const steps = draftSteps(application)
  const done = steps.filter((s) => s.done).length
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Group justify="space-between" align="flex-start">
        <div>
          <Text fw={700} size="lg">Before you submit</Text>
          <Text size="sm" c="dimmed" mt={4}>Do the signatures last: any change afterwards means signing again.</Text>
        </div>
        <Badge size="lg" variant="light" color={done === steps.length ? 'green' : 'orange'}>{done} of {steps.length} done</Badge>
      </Group>
      <div className="application-checklist">
        {steps.map((step, i) => (
          <div key={step.key} className={`check-step ${step.done ? 'is-done' : ''}`}>
            <ThemeIcon radius="xl" size={28} color={step.done ? 'teal' : 'gray'} variant={step.done ? 'filled' : 'light'}>
              <Text size="xs" fw={700}>{step.done ? '✓' : i + 1}</Text>
            </ThemeIcon>
            <div>
              <Text fw={600} size="sm">{stepTitles[step.key] ?? step.title}</Text>
              <Text size="xs" c="dimmed" mt={2}>{step.detail}</Text>
            </div>
          </div>
        ))}
      </div>
    </Paper>
  )
}

function DetailsPanel({ token, application, editable }: { token: string; application: Application & Registration; editable: boolean }) {
  const queryClient = useQueryClient()
  const [details, setDetails] = useState<CompanyDetails>(emptyDetails)
  useEffect(() => { setDetails({ ...emptyDetails, ...application.details }) }, [application.id, application.version])
  const save = useMutation({
    mutationFn: () => api.updateDetails(token, application, normalizeDetails(details)),
    onSuccess: (updated) => queryClient.setQueryData(['registration', application.id], updated),
  })
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Text fw={700} size="lg">1. Company details</Text>
      <Text c="dimmed" size="sm" mb="lg">As they appear on your PACRA certificate and RTSA Road Service Licence.</Text>
      <DetailsFields value={details} onChange={setDetails} disabled={!editable} />
      {save.error && <ApiError error={save.error} onReload={() => queryClient.invalidateQueries({ queryKey: ['registration', application.id] })} />}
      {save.isSuccess && !save.isPending && <Text size="sm" c="teal" mt="sm">Saved.</Text>}
      {editable && <Group justify="flex-end" mt="lg"><Button color="teal" radius="xl" onClick={() => save.mutate()} loading={save.isPending}>Save details</Button></Group>}
    </Paper>
  )
}

function PeoplePanel({ application, editable, onOpen }: { application: Application & Registration; editable: boolean; onOpen: (d: Dialog) => void }) {
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Group justify="space-between" align="flex-start" mb="md">
        <div>
          <Text fw={700} size="lg">2. Directors and owners</Text>
          <Text size="sm" c="dimmed" mt={4}>Everyone who signs for the company, starting with you. Each person signs the declaration at the end.</Text>
        </div>
        {editable && <Button size="xs" radius="xl" variant="light" color="teal" onClick={() => onOpen({ kind: 'representative', representative: null })}>Add person</Button>}
      </Group>
      {application.representatives.length === 0
        ? <Text size="sm" c="dimmed">No one added yet.</Text>
        : (
          <Stack gap="sm">
            {application.representatives.map((rep) => {
              const ack = rep.acknowledgement
              const signed = ack?.outcome === 'ACKNOWLEDGED'
              return (
                <Group key={rep.id} justify="space-between" className="list-row" wrap="nowrap" align="flex-start">
                  <div>
                    <Group gap={6}><Text fw={600} size="sm">{rep.fullName}</Text>{rep.primaryContact && <Badge size="xs" variant="light">Main contact</Badge>}</Group>
                    <Text size="xs" c="dimmed">{[rep.capacity, rep.idDocumentNumber && `${rep.idDocumentType ?? ''} ${rep.idDocumentNumber}`, rep.phone].filter(Boolean).join(' · ') || 'Details still needed'}</Text>
                    {editable && (
                      <Group gap="sm" mt={6}>
                        {rep.acknowledgementRequired && !signed && (
                          <Button size="compact-xs" color="teal" onClick={() => onOpen({ kind: 'sign', representative: rep })}>Sign declaration</Button>
                        )}
                        <Anchor component="button" size="xs" onClick={() => onOpen({ kind: 'representative', representative: rep })}>Edit</Anchor>
                        <Anchor component="button" size="xs" c="var(--th-danger-text)"
                          onClick={() => onOpen({ kind: 'remove', target: { kind: 'representative', id: rep.id, label: rep.fullName } })}>Remove</Anchor>
                      </Group>
                    )}
                  </div>
                  {!rep.acknowledgementRequired ? <Badge color="gray" variant="light">Does not sign</Badge>
                    : signed ? <Badge color="green" variant="light">Signed {formatDate(ack!.recordedAt)}</Badge>
                    : <Badge color="orange" variant="light">Not signed yet</Badge>}
                </Group>
              )
            })}
          </Stack>
        )}
    </Paper>
  )
}

function DocumentsPanel({ token, application, editable, onOpen }: {
  token: string; application: Application & Registration; editable: boolean; onOpen: (d: Dialog) => void
}) {
  const [error, setError] = useState<Error | null>(null)
  const has = (type: string) => application.evidence.some((e) => e.evidenceType === type)
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Group justify="space-between" align="flex-start" mb="md">
        <div>
          <Text fw={700} size="lg">3. Documents</Text>
          <Text size="sm" c="dimmed" mt={4}>PDF, JPEG or PNG, up to 10 MB each. A clear photo is fine.</Text>
        </div>
        {editable && <Button size="xs" radius="xl" variant="light" color="teal" onClick={() => onOpen({ kind: 'upload' })}>Upload document</Button>}
      </Group>
      <Group gap="xs" mb="md">
        <Badge variant="light" color={has('CERTIFICATE_OF_INCORPORATION') ? 'green' : 'orange'}>
          {has('CERTIFICATE_OF_INCORPORATION') ? '✓' : '•'} PACRA Certificate of Incorporation
        </Badge>
        <Badge variant="light" color={has('OPERATING_LICENCE') ? 'green' : 'orange'}>
          {has('OPERATING_LICENCE') ? '✓' : '•'} RTSA Road Service Licence
        </Badge>
      </Group>
      {application.evidence.length === 0 ? <Text size="sm" c="dimmed">No documents uploaded yet.</Text> : (
        <Stack gap="xs">
          {application.evidence.map((e) => (
            <Group key={e.id} justify="space-between" className="list-row" wrap="nowrap">
              <div>
                <Anchor component="button" size="sm" fw={600} onClick={() => onOpen({ kind: 'preview', evidence: e })}>{e.fileName}</Anchor>
                <Text size="xs" c="dimmed">{evidenceTypeLabels[e.evidenceType]} · {(e.sizeBytes / 1024).toFixed(0)} KB · {formatDate(e.uploadedAt)}</Text>
              </div>
              <Group gap="sm" wrap="nowrap">
                <Button size="xs" variant="default" onClick={() => onOpen({ kind: 'preview', evidence: e })}>View</Button>
                <Anchor component="button" size="xs" onClick={() => {
                  setError(null)
                  api.downloadEvidence(token, application.id, e.id, e.fileName, APPLICATIONS).catch((err: Error) => setError(err))
                }}>Download</Anchor>
                {editable && <Anchor component="button" size="xs" c="var(--th-danger-text)"
                  onClick={() => onOpen({ kind: 'remove', target: { kind: 'evidence', id: e.id, label: e.fileName } })}>Remove</Anchor>}
              </Group>
            </Group>
          ))}
        </Stack>
      )}
      {error && <ApiError error={error} />}
    </Paper>
  )
}

const timelineLabel: Record<RegistrationStatus, string> = {
  DRAFT: 'Application started', SUBMITTED: 'Submitted', UNDER_VERIFICATION: 'Checking started',
  AWAITING_INFORMATION: 'More information requested', RECOMMENDED: 'Checks completed', APPROVED: 'Approved',
  REJECTED: 'Declined', WITHDRAWN: 'Withdrawn',
}

function TimelinePanel({ application }: { application: Application }) {
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Text fw={700} size="lg" mb="md">History</Text>
      <ol className="application-timeline">
        {[...application.timeline].reverse().map((entry, i) => (
          <li key={`${entry.at}-${i}`}>
            <Text size="sm" fw={600}>{entry.status === 'DRAFT' && i < application.timeline.length - 1 ? 'Taken back to make changes' : timelineLabel[entry.status]}</Text>
            <Text size="xs" c="dimmed">{formatDateTime(entry.at)}</Text>
            {entry.note && entry.status !== 'DRAFT' && <Text size="sm" mt={4}>{entry.note}</Text>}
          </li>
        ))}
      </ol>
    </Paper>
  )
}

function DeclinedPanel({ application }: { application: Application }) {
  const reasons = [...application.timeline].reverse().find((e) => e.status === 'REJECTED')?.note
  return (
    <Alert color="red" radius="lg" title="Why we could not approve it">
      <Text size="sm">{reasons ?? 'Our team will contact you with the reasons.'}</Text>
      {application.appealDeadline && (
        <Text size="sm" mt="sm">You may appeal this decision until <b>{formatDate(application.appealDeadline)}</b>. Contact our team to start an appeal.</Text>
      )}
      <Text size="sm" mt="sm">Once the problem is fixed, you can also start a new application from your account.</Text>
    </Alert>
  )
}

/** Submit, take back (SS-9) or withdraw (SS-10), each with a clear confirmation. */
function StageDialog({ token, application, kind, onClose }: {
  token: string; application: Application; kind: 'submit' | 'takeBack' | 'withdraw'; onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const run = useMutation({
    mutationFn: () => kind === 'submit' ? api.submitApplication(token, application)
      : kind === 'takeBack' ? api.takeBackApplication(token, application)
      : api.withdrawApplication(token, application, reason.trim()),
    onSuccess: (updated) => {
      queryClient.setQueryData(['registration', application.id], updated)
      queryClient.invalidateQueries({ queryKey: ['my-applications'] })
      onClose()
    },
  })
  const copy = {
    submit: {
      title: 'Submit your application?', button: 'Submit application', color: 'teal',
      text: 'Our team will check your company with PACRA and RTSA and review your documents. You can still make changes until checking starts.',
    },
    takeBack: {
      title: 'Make changes?', button: 'Take back and edit', color: 'teal',
      text: 'Your application goes back to "In preparation" so you can correct it. Submit it again when you are done. Signatures will be needed again if you change anything.',
    },
    withdraw: {
      title: 'Withdraw your application?', button: 'Withdraw application', color: 'red',
      text: 'We will stop processing it. You can start a new application later.',
    },
  }[kind]
  return (
    <Modal opened onClose={onClose} title={<Text fw={700}>{copy.title}</Text>} centered radius="lg">
      <Text size="sm" c="dimmed">{copy.text}</Text>
      {kind === 'withdraw' && (
        <Textarea mt="md" label="Why are you withdrawing?" placeholder="For example: we are not ready yet" required
          value={reason} onChange={(e) => setReason(e.currentTarget.value)} maxLength={2000} autosize minRows={2} />
      )}
      {run.error && <ApiError error={run.error} />}
      <Group justify="flex-end" mt="lg">
        <Button variant="default" radius="xl" onClick={onClose}>Cancel</Button>
        <Button radius="xl" color={copy.color} loading={run.isPending} disabled={kind === 'withdraw' && !reason.trim()}
          onClick={() => run.mutate()}>{copy.button}</Button>
      </Group>
    </Modal>
  )
}
