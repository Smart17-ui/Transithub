import { useEffect, useRef, useState, type PointerEvent } from 'react'
import {
  Alert, Anchor, Badge, Checkbox, FileInput, Group, Paper, Select, SegmentedControl, SimpleGrid, Stack, Text,
  TextInput, Textarea, ThemeIcon,
} from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { EvidenceType, IdDocumentType, Registration, Representative, RepresentativeInput } from '../api/types'
import { CommandDialog, useCommand } from './commands'
import { evidenceTypeLabels } from './common'

/* ------------------------------------------------------------------ */
/* Checklist: what still prevents submission, in the order to do it.   */
/* ------------------------------------------------------------------ */

const companyFieldLabels: Record<string, string> = {
  'company.legalName': 'legal name',
  'company.registrationNumber': 'PACRA registration number',
  'company.operatingLicenceNumber': 'Road Service Licence number',
  'company.businessType': 'business type',
  'company.district': 'district',
  'company.physicalAddress': 'physical address',
  'company.contactPhone': 'contact phone',
}

type Step = { key: string; title: string; done: boolean; detail: string }

/** Turns the server's `missingForSubmission` codes into four steps a person can follow. */
export function draftSteps(registration: Registration): Step[] {
  const missing = registration.missingForSubmission
  const nameOf = (id: string) => registration.representatives.find((r) => r.id === id)?.fullName ?? 'A representative'
  const repProblems = (suffixes: string[]) => missing
    .map((code) => /^representative\[(.+)\]\.(\w+)$/.exec(code))
    .filter((m): m is RegExpExecArray => !!m && suffixes.includes(m[2]!))

  const companyMissing = missing.filter((code) => code.startsWith('company.')).map((code) => companyFieldLabels[code] ?? code)
  const repInfo = repProblems(['idDocument', 'capacity', 'phone'])
  const signatures = repProblems(['acknowledgement', 'refused'])
  const required = registration.representatives.filter((r) => r.acknowledgementRequired)
  const reps = registration.representatives.length

  return [
    {
      key: 'company', title: 'Company details', done: companyMissing.length === 0,
      detail: companyMissing.length ? `Outstanding: ${companyMissing.join(', ')}.` : 'Complete.',
    },
    {
      key: 'representatives', title: 'Authorised representatives',
      done: reps > 0 && repInfo.length === 0 && !missing.includes('representatives.primaryContact'),
      detail: reps === 0 ? 'Add at least one person authorised to act for the company.'
        : missing.includes('representatives.primaryContact') ? 'Designate one representative as the primary contact.'
        : repInfo.length ? `${[...new Set(repInfo.map((m) => nameOf(m[1]!)))].join(', ')}: identity document, capacity or telephone number outstanding.`
        : `${reps} added.`,
    },
    {
      key: 'evidence', title: 'Supporting documents',
      done: !missing.includes('evidence.certificateOfIncorporation') && !missing.includes('evidence.operatingLicence'),
      detail: [missing.includes('evidence.certificateOfIncorporation') && 'Certificate of Incorporation',
        missing.includes('evidence.operatingLicence') && 'Road Service Licence'].filter(Boolean).join(' and ')
        ? `Outstanding: ${[missing.includes('evidence.certificateOfIncorporation') && 'Certificate of Incorporation',
          missing.includes('evidence.operatingLicence') && 'Road Service Licence'].filter(Boolean).join(' and ')}.`
        : `${registration.evidence.length} uploaded.`,
    },
    {
      key: 'signatures', title: 'Declarations', done: reps > 0 && signatures.length === 0,
      detail: reps === 0 ? 'Available once representatives have been added.'
        : signatures.some((m) => m[2] === 'refused') ? 'A representative has declined to sign; the application cannot be submitted.'
        : signatures.length ? `${signatures.length} of ${required.length} outstanding. Complete this step last, as later changes invalidate signatures.`
        : `All ${required.length} required declarations signed.`,
    },
  ]
}

export function DraftChecklist({ registration }: { registration: Registration }) {
  const steps = draftSteps(registration)
  const done = steps.filter((s) => s.done).length
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Group justify="space-between" align="flex-start">
        <div>
          <Text fw={700} size="lg">Submission checklist</Text>
          <Text size="sm" c="dimmed" mt={4}>Complete each step in order. Declarations are signed last, as any later change invalidates them.</Text>
        </div>
        <Badge size="lg" variant="light" color={done === steps.length ? 'green' : 'orange'}>{done} of {steps.length} complete</Badge>
      </Group>
      <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }} spacing="sm" mt="md">
        {steps.map((step, i) => (
          <div key={step.key} className={`check-step ${step.done ? 'is-done' : ''}`}>
            <ThemeIcon radius="xl" size={28} color={step.done ? 'teal' : 'gray'} variant={step.done ? 'filled' : 'light'}>
              <Text size="xs" fw={700}>{step.done ? '✓' : i + 1}</Text>
            </ThemeIcon>
            <div>
              <Text fw={600} size="sm">{step.title}</Text>
              <Text size="xs" c="dimmed" mt={2}>{step.detail}</Text>
            </div>
          </div>
        ))}
      </SimpleGrid>
    </Paper>
  )
}

/* ------------------------------------------------------------------ */
/* Representatives                                                     */
/* ------------------------------------------------------------------ */

const PHONE = /^\+?[0-9 -]{7,20}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

type DialogProps = { token: string; registration: Registration; opened: boolean; onClose: () => void }

export function RepresentativeDialog({ token, registration, representative, opened, onClose }: DialogProps & { representative: Representative | null }) {
  const command = useCommand(registration.id)
  const first = registration.representatives.length === 0
  const [form, setForm] = useState<RepresentativeInput>(() => representative ? {
    fullName: representative.fullName,
    idDocumentType: (representative.idDocumentType as IdDocumentType | null) ?? 'NRC',
    idDocumentNumber: representative.idDocumentNumber, capacity: representative.capacity,
    phone: representative.phone, email: representative.email,
    acknowledgementRequired: representative.acknowledgementRequired, primaryContact: representative.primaryContact,
  } : {
    fullName: '', idDocumentType: 'NRC', idDocumentNumber: '', capacity: '', phone: '', email: '',
    acknowledgementRequired: true, primaryContact: first,
  })
  const set = <K extends keyof RepresentativeInput>(key: K, value: RepresentativeInput[K]) => setForm((f) => ({ ...f, [key]: value }))
  const phoneError = form.phone && !PHONE.test(form.phone.trim()) ? 'Enter a valid number, for example +260 977 123 456' : null
  const emailError = form.email && !EMAIL.test(form.email.trim()) ? 'Enter a valid email address' : null
  const clean = (value: string | null) => value?.trim() || null
  const signed = representative?.acknowledgement?.outcome === 'ACKNOWLEDGED'

  return (
    <CommandDialog opened={opened} onClose={onClose} title={representative ? `Edit ${representative.fullName}` : 'Add a representative'}
      intro={signed
        ? <Alert color="orange" variant="light">Changing a representative invalidates the declarations already signed. Every representative required to sign will need to sign again.</Alert>
        : <Text size="sm" c="dimmed">An individual authorised to act for the company, such as a director or owner. Identity document, capacity and telephone number are required before submission.</Text>}
      submitLabel={representative ? 'Save changes' : 'Add representative'}
      canSubmit={!!form.fullName.trim() && !phoneError && !emailError} busy={command.isPending} error={command.error}
      onSubmit={() => {
        const body: RepresentativeInput = {
          ...form, fullName: form.fullName.trim(), idDocumentNumber: clean(form.idDocumentNumber), capacity: clean(form.capacity),
          phone: clean(form.phone), email: clean(form.email), idDocumentType: clean(form.idDocumentNumber) ? form.idDocumentType : null,
        }
        command.mutate(() => representative
          ? api.updateRepresentative(token, registration, representative.id, body)
          : api.addRepresentative(token, registration, body), { onSuccess: onClose })
      }}>
      <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
        <TextInput label="Full name" placeholder="e.g. Mwansa Banda" required value={form.fullName}
          onChange={(e) => set('fullName', e.currentTarget.value)} maxLength={200} data-autofocus />
        <TextInput label="Capacity" placeholder="e.g. Managing Director" value={form.capacity ?? ''}
          onChange={(e) => set('capacity', e.currentTarget.value)} maxLength={100} />
        <Select label="Identity document" data={[{ value: 'NRC', label: 'NRC' }, { value: 'PASSPORT', label: 'Passport' }, { value: 'OTHER', label: 'Other' }]}
          value={form.idDocumentType} onChange={(v) => set('idDocumentType', (v ?? 'NRC') as IdDocumentType)} allowDeselect={false} />
        <TextInput label="Document number" placeholder={form.idDocumentType === 'NRC' ? '123456/10/1' : 'Document number'} value={form.idDocumentNumber ?? ''}
          onChange={(e) => set('idDocumentNumber', e.currentTarget.value)} maxLength={50} />
        <TextInput label="Telephone" type="tel" placeholder="+260 977 123 456" value={form.phone ?? ''} error={phoneError}
          onChange={(e) => set('phone', e.currentTarget.value)} />
        <TextInput label="Email" type="email" placeholder="Optional" value={form.email ?? ''} error={emailError}
          onChange={(e) => set('email', e.currentTarget.value)} maxLength={254} />
      </SimpleGrid>
      <Group gap="xl">
        <Checkbox label="Primary contact (receives the account invitation upon approval)" checked={form.primaryContact}
          onChange={(e) => set('primaryContact', e.currentTarget.checked)} />
        <Checkbox label="Required to sign the declaration" checked={form.acknowledgementRequired}
          onChange={(e) => set('acknowledgementRequired', e.currentTarget.checked)} />
      </Group>
    </CommandDialog>
  )
}

/* ------------------------------------------------------------------ */
/* Documents                                                           */
/* ------------------------------------------------------------------ */

const MAX_BYTES = 10 * 1024 * 1024
const ACCEPTED = ['application/pdf', 'image/jpeg', 'image/png']

export function EvidenceUploadDialog({ token, registration, opened, onClose }: DialogProps) {
  const command = useCommand(registration.id)
  const present = new Set(registration.evidence.map((e) => e.evidenceType))
  const firstMissing = (['CERTIFICATE_OF_INCORPORATION', 'TAX_CLEARANCE', 'OPERATING_LICENCE'] as EvidenceType[]).find((t) => !present.has(t))
  const [type, setType] = useState<EvidenceType>(firstMissing ?? 'OTHER')
  const [file, setFile] = useState<File | null>(null)
  const fileError = !file ? null
    : file.size > MAX_BYTES ? `This file is ${(file.size / 1024 / 1024).toFixed(1)} MB. The maximum size is 10 MB.`
    : !ACCEPTED.includes(file.type) ? 'Only PDF, JPEG and PNG files are accepted.'
    : null
  const signed = registration.representatives.some((r) => r.acknowledgement?.outcome === 'ACKNOWLEDGED')

  return (
    <CommandDialog opened={opened} onClose={onClose} title="Upload a supporting document"
      intro={signed
        ? <Alert color="orange" variant="light">Uploading a document invalidates the declarations already signed; representatives will need to sign again.</Alert>
        : <Text size="sm" c="dimmed">Documents are encrypted at rest. A clear photograph of a paper document is acceptable.</Text>}
      submitLabel="Upload" canSubmit={!!file && !fileError} busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => api.attachEvidence(token, registration, type, file!), { onSuccess: onClose })}>
      <Select label="Document type" data={Object.entries(evidenceTypeLabels).map(([value, label]) => ({
        value, label: present.has(value as EvidenceType) ? `${label} (uploaded)` : label,
      }))} value={type} onChange={(v) => setType((v ?? 'OTHER') as EvidenceType)} allowDeselect={false} />
      <FileInput label="File" placeholder="Choose a PDF, JPEG or PNG (max 10 MB)" accept={ACCEPTED.join(',')} required
        value={file} onChange={setFile} error={fileError} clearable />
    </CommandDialog>
  )
}

/* ------------------------------------------------------------------ */
/* Signature: the representative reads the declaration and signs.      */
/* ------------------------------------------------------------------ */

/** A drawing pad that works with a finger, stylus or mouse. Ink is always dark on white, like paper. */
function SignaturePad({ onChange }: { onChange: (blank: boolean) => void }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const [blank, setBlank] = useState(true)

  useEffect(() => {
    const el = canvas.current!
    const ratio = window.devicePixelRatio || 1
    el.width = el.clientWidth * ratio
    el.height = el.clientHeight * ratio
    const ctx = el.getContext('2d')!
    ctx.scale(ratio, ratio)
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, el.clientWidth, el.clientHeight)
    ctx.lineWidth = 2.4
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.strokeStyle = '#101C35'
  }, [])

  const point = (e: PointerEvent<HTMLCanvasElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    return [e.clientX - rect.left, e.clientY - rect.top] as const
  }
  const start = (e: PointerEvent<HTMLCanvasElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    drawing.current = true
    const ctx = e.currentTarget.getContext('2d')!
    const [x, y] = point(e)
    ctx.beginPath()
    ctx.moveTo(x, y)
    ctx.lineTo(x + 0.1, y + 0.1)
    ctx.stroke()
  }
  const move = (e: PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return
    const ctx = e.currentTarget.getContext('2d')!
    const [x, y] = point(e)
    ctx.lineTo(x, y)
    ctx.stroke()
    if (blank) { setBlank(false); onChange(false) }
  }
  const end = () => { drawing.current = false }
  const clear = () => {
    const el = canvas.current!
    const ctx = el.getContext('2d')!
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, el.clientWidth, el.clientHeight)
    setBlank(true)
    onChange(true)
  }

  return (
    <div>
      <div className="signature-pad">
        <canvas ref={canvas} className="signature-canvas" aria-label="Signature area: sign with a finger, stylus or mouse"
          onPointerDown={start} onPointerMove={move} onPointerUp={end} onPointerCancel={end} onPointerLeave={end} />
        {blank && <span className="signature-hint">Sign within the box</span>}
        <span className="signature-line" aria-hidden />
      </div>
      <Group justify="flex-end" mt={6}><Anchor component="button" size="sm" onClick={clear} disabled={blank}>Clear</Anchor></Group>
    </div>
  )
}

function canvasPng(): Promise<Blob> {
  const el = document.querySelector<HTMLCanvasElement>('.signature-canvas')!
  return new Promise((resolve, reject) => el.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Could not read the signature.'))), 'image/png'))
}

export function SignatureDialog({ token, registration, representative, opened, onClose }: DialogProps & { representative: Representative | null }) {
  const command = useCommand(registration.id)
  const declaration = useQuery({ queryKey: ['declaration'], queryFn: () => api.declaration(token), staleTime: Infinity, enabled: opened })
  const [mode, setMode] = useState<'sign' | 'refuse'>('sign')
  const [blank, setBlank] = useState(true)
  const [read, setRead] = useState(false)
  const [reason, setReason] = useState('')
  if (!representative) return null

  const submit = () => {
    if (mode === 'refuse') {
      command.mutate(() => api.refuse(token, registration, representative.id, reason.trim()), { onSuccess: onClose })
      return
    }
    command.mutate(async () => api.acknowledge(token, registration, representative.id, declaration.data!.version, await canvasPng()),
      { onSuccess: onClose })
  }

  return (
    <CommandDialog opened={opened} onClose={onClose} title={`Declaration: ${representative.fullName}`}
      intro={<Text size="sm" c="dimmed">Please hand the device to {representative.fullName}, who should read the declaration below before signing.</Text>}
      submitLabel={mode === 'sign' ? 'Record signature' : 'Record decision not to sign'} color={mode === 'sign' ? 'teal' : 'red'}
      canSubmit={mode === 'sign' ? !blank && read && !!declaration.data : !!reason.trim()}
      busy={command.isPending} error={command.error ?? declaration.error} onSubmit={submit}>
      <SegmentedControl fullWidth value={mode} onChange={(v) => setMode(v as typeof mode)}
        data={[{ value: 'sign', label: 'Signs the declaration' }, { value: 'refuse', label: 'Declines to sign' }]} />
      <Paper className="declaration" p="md" radius="md">
        <Text size="xs" c="dimmed" fw={700} tt="uppercase" lts={0.6}>Declaration {declaration.data?.version}</Text>
        <Text size="sm" mt={6} lh={1.65}>{declaration.data?.text ?? 'Loading…'}</Text>
      </Paper>
      {mode === 'sign' ? (
        <Stack gap="xs">
          <SignaturePad onChange={setBlank} />
          <Checkbox checked={read} onChange={(e) => setRead(e.currentTarget.checked)}
            label={`${representative.fullName} has read and accepted the declaration above.`} />
        </Stack>
      ) : (
        <Textarea label="Reason given" placeholder="The representative’s reason for declining to sign" value={reason}
          onChange={(e) => setReason(e.currentTarget.value)} required maxLength={2000} autosize minRows={3} />
      )}
    </CommandDialog>
  )
}

/** Confirms removing a representative or document; the record stays in the draft's history. */
export function RemoveDialog({ token, registration, target, opened, onClose }: DialogProps & {
  target: { kind: 'representative' | 'evidence'; id: string; label: string } | null
}) {
  const command = useCommand(registration.id)
  if (!target) return null
  return (
    <CommandDialog opened={opened} onClose={onClose} title={`Remove ${target.label}?`} color="red"
      intro={<Text size="sm" c="dimmed">It will be removed from the application and retained in its history. Signed declarations will be invalidated.</Text>}
      submitLabel="Remove" canSubmit busy={command.isPending} error={command.error}
      onSubmit={() => command.mutate(() => target.kind === 'representative'
        ? api.removeRepresentative(token, registration, target.id)
        : api.removeEvidence(token, registration, target.id), { onSuccess: onClose })}>
      {null}
    </CommandDialog>
  )
}
