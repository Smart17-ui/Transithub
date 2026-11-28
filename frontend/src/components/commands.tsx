import { useState, type ReactNode } from 'react'
import {
  Alert, Button, Checkbox, FileInput, Group, Modal, Select, SegmentedControl, Stack, Text, TextInput, Textarea,
} from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Company, Evidence, EvidenceType, InformationRequest, Registration, RegistryResult, RegistrySource } from '../api/types'
import { ApiError, evidenceTypeLabels } from './common'

type CommandResult = Registration | { registration: Registration }

/**
 * Runs one command against a registration. On success the page shows the
 * returned version straight away and everything derived from it reloads.
 */
export function useCommand(registrationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (send: () => Promise<CommandResult>) => send(),
    onSuccess: (result) => {
      const registration = 'registration' in result ? result.registration : result
      queryClient.setQueryData(['registration', registrationId], registration)
      for (const key of ['verification', 'decision', 'audit', 'duplicate-flags']) {
        queryClient.invalidateQueries({ queryKey: [key, registrationId] })
      }
      queryClient.invalidateQueries({ queryKey: ['registrations'] })
    },
  })
}

export function CommandDialog({ opened, onClose, title, intro, children, submitLabel, color = 'teal', canSubmit, busy, error, onSubmit }: {
  opened: boolean; onClose: () => void; title: string; intro?: ReactNode; children: ReactNode; submitLabel: string
  color?: string; canSubmit: boolean; busy: boolean; error: Error | null; onSubmit: () => void
}) {
  return (
    <Modal opened={opened} onClose={onClose} title={title} centered radius="lg" size="lg">
      <Stack>
        {intro}
        {children}
        {error && <ApiError error={error} />}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>Cancel</Button>
          <Button color={color} disabled={!canSubmit} loading={busy} onClick={onSubmit}>{submitLabel}</Button>
        </Group>
      </Stack>
    </Modal>
  )
}

type DialogProps = { token: string; registration: Registration; opened: boolean; onClose: () => void }

/** AC-R1-VER-01, -06: the list only offers people who may verify this registration (AC-R1-VER-02). */
export function AssignVerifierDialog({ token, registration, opened, onClose, currentVerifier }: DialogProps & { currentVerifier: string | null }) {
  const command = useCommand(registration.id)
  const [verifierId, setVerifierId] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const candidates = useQuery({
    queryKey: ['verifier-candidates', registration.id],
    queryFn: () => api.verifierCandidates(token, registration.id),
    enabled: opened,
  })
  const options = (candidates.data ?? [])
    .filter((member) => member.id !== currentVerifier)
    .map((member) => ({ value: member.id, label: member.displayName }))
  const reassigning = !!currentVerifier

  return (
    <CommandDialog opened={opened} onClose={onClose} title={reassigning ? 'Reassign compliance review' : 'Assign compliance review'}
      intro={<Text size="sm" c="dimmed">Only active Compliance Officers with no involvement in preparing this application are listed.</Text>}
      submitLabel={reassigning ? 'Reassign' : 'Assign'} canSubmit={!!verifierId && (!reassigning || !!reason.trim())}
      busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.assignVerifier(token, registration, verifierId!, reason.trim()), { onSuccess: onClose })}>
      <Select label="Compliance Officer" placeholder={candidates.isPending ? 'Loading…' : 'Select an officer'} data={options}
        value={verifierId} onChange={setVerifierId} searchable nothingFoundMessage="No eligible Compliance Officers" required />
      {candidates.error && <ApiError error={candidates.error} />}
      <Textarea label="Reason" placeholder={reassigning ? 'Reason for reassignment' : 'Optional'}
        value={reason} onChange={(e) => setReason(e.currentTarget.value)} required={reassigning} maxLength={2000} autosize minRows={2} />
    </CommandDialog>
  )
}

/** AC-R1-VER-03 */
export function AssessEvidenceDialog({ token, registration, evidence, opened, onClose }: DialogProps & { evidence: Evidence | null }) {
  const command = useCommand(registration.id)
  const [outcome, setOutcome] = useState<'ACCEPTED' | 'INSUFFICIENT' | 'INVALID'>('ACCEPTED')
  const [findings, setFindings] = useState('')
  return (
    <CommandDialog opened={opened && !!evidence} onClose={onClose} title={`Review document: ${evidence?.fileName ?? ''}`}
      intro={evidence && <Text size="sm" c="dimmed">{evidenceTypeLabels[evidence.evidenceType]}. A new review replaces your previous review of this document.</Text>}
      submitLabel="Record review" canSubmit={outcome === 'ACCEPTED' || !!findings.trim()}
      busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.assessEvidence(token, registration, evidence!.id, outcome, findings.trim()), { onSuccess: onClose })}>
      <SegmentedControl fullWidth value={outcome} onChange={(v) => setOutcome(v as typeof outcome)}
        data={[{ value: 'ACCEPTED', label: 'Accepted' }, { value: 'INSUFFICIENT', label: 'Insufficient' }, { value: 'INVALID', label: 'Invalid' }]} />
      <Textarea label="Findings" placeholder="What was reviewed and the outcome" value={findings}
        onChange={(e) => setFindings(e.currentTarget.value)} required={outcome !== 'ACCEPTED'} maxLength={4000} autosize minRows={3} />
    </CommandDialog>
  )
}

export function DuplicateReviewDialog({ token, registration, flagId, matchedReference, opened, onClose }: DialogProps & { flagId: string | null; matchedReference: string }) {
  const command = useCommand(registration.id)
  const [outcome, setOutcome] = useState<'NOT_DUPLICATE' | 'DUPLICATE'>('NOT_DUPLICATE')
  const [note, setNote] = useState('')
  return (
    <CommandDialog opened={opened && !!flagId} onClose={onClose} title={`Potential duplicate of ${matchedReference}`}
      intro={<Text size="sm" c="dimmed">Applications are never merged or discarded automatically. Record your conclusion below.</Text>}
      submitLabel="Record review" canSubmit={!!note.trim()} busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.reviewDuplicate(token, registration, flagId!, outcome, note.trim()), { onSuccess: onClose })}>
      <SegmentedControl fullWidth value={outcome} onChange={(v) => setOutcome(v as typeof outcome)}
        data={[{ value: 'NOT_DUPLICATE', label: 'Not a duplicate' }, { value: 'DUPLICATE', label: 'Is a duplicate' }]} />
      <Textarea label="Basis for conclusion" placeholder="How you reached this conclusion" value={note}
        onChange={(e) => setNote(e.currentTarget.value)} required maxLength={2000} autosize minRows={2} />
    </CommandDialog>
  )
}

function today() {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10)
}

/** AC-R1-VER-04 */
export function RequestInformationDialog({ token, registration, opened, onClose }: DialogProps) {
  const command = useCommand(registration.id)
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  return (
    <CommandDialog opened={opened} onClose={onClose} title="Request additional information"
      intro={<Text size="sm" c="dimmed">The review is paused until the information is received. The applicant is notified by email and SMS.</Text>}
      submitLabel="Send request" canSubmit={!!description.trim() && !!dueDate}
      busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.requestInformation(token, registration, description.trim(), dueDate), { onSuccess: onClose })}>
      <Textarea label="Information required" placeholder="e.g. The tax clearance certificate provided expired in March 2026. Please provide a current certificate."
        value={description} onChange={(e) => setDescription(e.currentTarget.value)} required maxLength={4000} autosize minRows={3} />
      <TextInput label="Due date" type="date" min={today()} value={dueDate} onChange={(e) => setDueDate(e.currentTarget.value)} required w={200} />
    </CommandDialog>
  )
}

/** AC-R1-VER-05: optionally attach a document, then answer; verification resumes. */
export function ProvideInformationDialog({ token, registration, request, opened, onClose }: DialogProps & { request: InformationRequest | null }) {
  const upload = useCommand(registration.id)
  const answer = useCommand(registration.id)
  const [file, setFile] = useState<File | null>(null)
  const [evidenceType, setEvidenceType] = useState<EvidenceType>('OTHER')
  const [response, setResponse] = useState('')
  const busy = upload.isPending || answer.isPending

  async function submit() {
    let current = registration
    if (file) {
      const result = await upload.mutateAsync(() => api.attachRequestedEvidence(token, current, request!.id, evidenceType, file))
      current = 'registration' in result ? result.registration : result
      setFile(null)
    }
    await answer.mutateAsync(() => api.provideInformation(token, current, request!.id, response.trim()))
    onClose()
  }

  return (
    <CommandDialog opened={opened && !!request} onClose={onClose} title="Submit requested information"
      intro={request && <Alert variant="light" color="orange" title={`Due ${new Date(request.dueDate).toLocaleDateString()}`}>{request.description}</Alert>}
      submitLabel="Submit response" canSubmit={!!response.trim()} busy={busy} error={upload.error ?? answer.error}
      onSubmit={() => { submit().catch(() => undefined) }}>
      <Group grow align="flex-start">
        <FileInput label="Document (optional)" placeholder="PDF, JPEG or PNG, up to 10 MB" accept="application/pdf,image/jpeg,image/png"
          value={file} onChange={setFile} clearable />
        <Select label="Document type" data={Object.entries(evidenceTypeLabels).map(([value, label]) => ({ value, label }))}
          value={evidenceType} onChange={(v) => setEvidenceType((v ?? 'OTHER') as EvidenceType)} disabled={!file} allowDeselect={false} />
      </Group>
      <Textarea label="Response" placeholder="Describe what has been provided, or why it cannot be provided" value={response}
        onChange={(e) => setResponse(e.currentTarget.value)} required maxLength={4000} autosize minRows={3} />
    </CommandDialog>
  )
}

const registryGuides: Record<RegistrySource, { title: string; field: string; how: string }> = {
  PACRA: {
    title: 'PACRA company registry',
    field: 'PACRA registration number',
    how: 'Search the PACRA register by this number. Confirm the company exists, is active, and the name matches.',
  },
  RTSA: {
    title: 'RTSA Road Service Licence',
    field: 'Road Service Licence number',
    how: 'Confirm with RTSA, or against the original licence, that this licence is current and issued to this company.',
  },
  ZRA: {
    title: 'ZRA tax clearance',
    field: 'Tax clearance certificate number',
    how: 'Check the certificate number on the ZRA verification service. Confirm it is genuine and still valid.',
  },
  OTHER: { title: 'Other registry', field: 'Reference', how: 'Record the registry consulted in the note.' },
}

/**
 * The checker records what an official register showed for one of the
 * company's numbers. Every result except a match needs an explanation.
 */
export function RegistryCheckDialog({ token, registration, source, opened, onClose }: DialogProps & { source: RegistrySource }) {
  const command = useCommand(registration.id)
  const prefilled = source === 'PACRA' ? registration.details.registrationNumber
    : source === 'RTSA' ? registration.details.operatingLicenceNumber : ''
  const [reference, setReference] = useState(prefilled ?? '')
  const [result, setResult] = useState<RegistryResult>('MATCH')
  const [note, setNote] = useState('')
  const guide = registryGuides[source]
  return (
    <CommandDialog opened={opened} onClose={onClose} title={`Registry verification: ${guide.title}`}
      intro={<Alert variant="light" color="sky">{guide.how}</Alert>}
      submitLabel="Record verification" color={result === 'MATCH' ? 'teal' : 'orange'}
      canSubmit={!!reference.trim() && (result === 'MATCH' || !!note.trim())}
      busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.recordRegistryCheck(token, registration, source, reference.trim(), result, note.trim()),
        { onSuccess: onClose })}>
      <TextInput label={guide.field} value={reference} onChange={(e) => setReference(e.currentTarget.value)} required maxLength={100} />
      <Select label="Verification result" allowDeselect={false} value={result}
        onChange={(v) => setResult((v ?? 'MATCH') as RegistryResult)}
        data={[
          { value: 'MATCH', label: 'Verified: record found, current and consistent' },
          { value: 'MISMATCH', label: 'Record found, but details are inconsistent' },
          { value: 'NOT_FOUND', label: 'No record found' },
          { value: 'COULD_NOT_CHECK', label: 'Unable to verify (registry unavailable)' },
        ]} />
      <Textarea label="Note" placeholder={result === 'MATCH' ? 'Optional, e.g. "Active since 2019"' : 'What the registry showed and the action you will take'}
        value={note} onChange={(e) => setNote(e.currentTarget.value)} required={result !== 'MATCH'} maxLength={2000} autosize minRows={2} />
    </CommandDialog>
  )
}

/** AC-R1-DEC-03, AC-R1-EST-01: approval establishes the company. */
export function ApproveDialog({ token, registration, opened, onClose, onApproved }: DialogProps & { onApproved: (company: Company) => void }) {
  const command = useCommand(registration.id)
  const [rationale, setRationale] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  // What still blocks approval, as the server sees it; shown as a checklist before anyone tries.
  const summary = useQuery({ queryKey: ['verification', registration.id], queryFn: () => api.verification(token, registration.id), enabled: opened })
  const v = summary.data
  const accepted = new Set(v?.assessments.filter((a) => a.outcome === 'ACCEPTED').map((a) => a.evidenceId) ?? [])
  const docsDone = registration.evidence.filter((e) => accepted.has(e.id)).length
  const matched = (source: string) => v?.registryChecks.some((c) => c.source === source && c.result === 'MATCH') ?? false
  const checks = [
    { done: docsDone === registration.evidence.length && registration.evidence.length > 0,
      label: `Every document reviewed and accepted (${docsDone} of ${registration.evidence.length})`,
      hint: 'Use Review next to each document under Supporting documents.' },
    { done: matched('PACRA'), label: 'PACRA company registry verified as a match', hint: 'Use Verify next to Company registry.' },
    { done: matched('RTSA'), label: 'RTSA Road Service Licence verified as a match', hint: 'Use Verify next to Road Service Licence.' },
    { done: (v?.unreviewedDuplicateFlags.length ?? 0) === 0, label: 'Possible duplicates reviewed', hint: 'Review each one under Potential duplicates.' },
  ]
  const ready = !!v && v.outstandingForApproval.length === 0

  return (
    <CommandDialog opened={opened} onClose={onClose} title="Approve application"
      intro={<Text size="sm" c="dimmed">Approval establishes <b>{registration.details.legalName}</b> as a company on TransitHub with its own TH-ZM company code.</Text>}
      submitLabel="Approve" canSubmit={ready && !!rationale.trim() && confirmed} busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.approve(token, registration, rationale.trim()), {
        onSuccess: (result) => { if ('company' in result) onApproved(result.company as Company); onClose() },
      })}>
      <Stack gap={8} className="approve-checklist">
        <Text fw={600} size="sm">{ready ? 'All checks are complete.' : 'Before you can approve:'}</Text>
        {summary.isPending ? <Text size="sm" c="dimmed">Checking…</Text> : checks.map((c) => (
          <div key={c.label} className={`approve-check ${c.done ? 'is-done' : ''}`}>
            <span className="approve-check-mark" aria-hidden>{c.done ? '✓' : '•'}</span>
            <div>
              <Text size="sm" fw={c.done ? 400 : 600}>{c.label}</Text>
              {!c.done && <Text size="xs" c="dimmed">{c.hint}</Text>}
            </div>
          </div>
        ))}
      </Stack>
      {ready && <>
        <Textarea label="Reason for approving" placeholder="e.g. Registered and licensed; all documents valid" value={rationale}
          onChange={(e) => setRationale(e.currentTarget.value)} required maxLength={4000} autosize minRows={3} />
        <Checkbox checked={confirmed} onChange={(e) => setConfirmed(e.currentTarget.checked)} label="I confirm that I have personally reviewed the documents and verified the PACRA and RTSA records." />
      </>}
    </CommandDialog>
  )
}

/** AC-R1-DEC-04 */
export function RejectDialog({ token, registration, opened, onClose }: DialogProps) {
  const command = useCommand(registration.id)
  const [reasons, setReasons] = useState('')
  return (
    <CommandDialog opened={opened} onClose={onClose} title="Decline application" color="red"
      intro={<Alert variant="light" color="orange">The representatives are notified with your reasons and the appeal deadline (30 days).</Alert>}
      submitLabel="Decline application" canSubmit={!!reasons.trim()} busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.reject(token, registration, reasons.trim()), { onSuccess: onClose })}>
      <Textarea label="Reasons for the decision" placeholder="These reasons are shared with the applicant" value={reasons}
        onChange={(e) => setReasons(e.currentTarget.value)} required maxLength={4000} autosize minRows={4} />
    </CommandDialog>
  )
}

/** AC-R1-WDR-01 */
export function WithdrawDialog({ token, registration, opened, onClose }: DialogProps) {
  const command = useCommand(registration.id)
  const [reason, setReason] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  return (
    <CommandDialog opened={opened} onClose={onClose} title="Withdraw application" color="red"
      intro={<Alert color="orange" variant="light">This closes the registration as withdrawn. Its history and submitted information are kept.</Alert>}
      submitLabel="Confirm withdrawal" canSubmit={!!reason.trim() && confirmed} busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.withdraw(token, registration, reason.trim()), { onSuccess: onClose })}>
      <Textarea label="Reason for withdrawal" placeholder="As provided by the applicant" value={reason}
        onChange={(e) => setReason(e.currentTarget.value)} required minRows={3} maxLength={2000} autosize />
      <Checkbox checked={confirmed} onChange={(e) => setConfirmed(e.currentTarget.checked)} label="I confirm that the applicant has instructed this withdrawal." />
    </CommandDialog>
  )
}
