import { useState } from 'react'
import { Alert, Badge, Button, Group, Loader, Modal, Paper, Select, Stack, Text, Textarea, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../api/client'
import type { DraftSnapshot, QuarantinedItem } from '../api/types'
import { ApiError, formatDateTime, useMe, useStaffNames } from '../components/common'

/**
 * Offline sync (#7a): data from a tablet that could not be applied, kept safely until a
 * supervisor acts on it: turn it into a new draft for an Onboarding Officer, or close it
 * with a reason. Nothing here is lost or silently dropped.
 */
export function SyncPage({ token }: { token: string }) {
  const { can, me } = useMe(token)
  const items = useQuery({ queryKey: ['quarantine'], queryFn: () => api.quarantine(token), enabled: can('registration.resolve-sync-conflict') })
  const name = useStaffNames(token, (items.data ?? []).map((i) => i.userId))
  const [open, setOpen] = useState<QuarantinedItem | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  if (me && !can('registration.resolve-sync-conflict')) {
    return <Alert color="red" title="Supervisors only">Only Onboarding Supervisors act on offline data.</Alert>
  }

  return (
    <Stack gap="lg">
      <div>
        <div className="eyebrow">Workspace / offline sync</div>
        <Title order={1} className="page-title">Offline sync</Title>
        <Text c="dimmed" mt={6} maw={720}>
          Applications captured on a tablet that could not be added automatically, for example because the officer
          lost access before the tablet synced. The data is kept safely until you decide what to do with it.
        </Text>
      </div>
      {notice && <Alert color="teal" withCloseButton onClose={() => setNotice(null)}>{notice}</Alert>}
      {items.error && <ApiError error={items.error} />}
      {items.isPending ? <Group justify="center" py="xl"><Loader color="teal" /></Group>
        : items.data?.length === 0 ? (
          <Paper className="panel center-message" radius="lg" p="xl">
            <Text fw={700}>Nothing waiting</Text>
            <Text c="dimmed" size="sm" mt={4}>All offline data has been applied or dealt with.</Text>
          </Paper>
        ) : (
          <Stack gap="sm">
            {items.data?.map((item) => (
              <Paper key={item.id} className="panel" radius="lg" p="lg">
                <Group justify="space-between" wrap="wrap" gap="sm">
                  <div>
                    <Group gap="sm"><Badge color="orange" variant="light">Waiting</Badge>
                      <Text size="sm" c="dimmed">Received {formatDateTime(item.receivedAt)} from {item.deviceId}, sent by {name(item.userId)}</Text></Group>
                    <Text size="sm" mt={6}>{item.reason}</Text>
                  </div>
                  <Button radius="xl" variant="light" color="teal" onClick={() => setOpen(item)}>Review and decide</Button>
                </Group>
              </Paper>
            ))}
          </Stack>
        )}
      {open && <DecideDialog token={token} item={open} onClose={(message) => { setOpen(null); if (message) setNotice(message) }} />}
    </Stack>
  )
}

function DecideDialog({ token, item, onClose }: { token: string; item: QuarantinedItem; onClose: (message?: string) => void }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const data = useQuery({ queryKey: ['quarantine', item.id], queryFn: () => api.quarantinedData(token, item.id) })
  const officers = useQuery({ queryKey: ['sync-officers'], queryFn: () => api.syncOfficers(token) })
  const [mode, setMode] = useState<'draft' | 'close'>('draft')
  const [officer, setOfficer] = useState<string | null>(null)
  const [text, setText] = useState('')
  const done = () => queryClient.invalidateQueries({ queryKey: ['quarantine'] })
  const draft = useMutation({
    mutationFn: () => api.releaseQuarantineAsDraft(token, item.id, officer!, text.trim()),
    onSuccess: (created) => { void done(); onClose(); navigate(`/registrations/${created.id}`) },
  })
  const close = useMutation({
    mutationFn: () => api.closeQuarantine(token, item.id, text.trim()),
    onSuccess: () => { void done(); onClose('The offline data was closed and kept on record.') },
  })

  return (
    <Modal opened onClose={() => onClose()} title={<Text fw={700}>Offline data from {item.deviceId}</Text>} size="lg" radius="lg" centered>
      {data.isPending ? <Loader color="teal" /> : data.error ? <ApiError error={data.error} /> : <SnapshotView snapshot={data.data!} />}
      <Group mt="lg" gap="xs">
        <Button size="xs" radius="xl" variant={mode === 'draft' ? 'filled' : 'default'} color="teal" onClick={() => setMode('draft')}>Turn into a draft</Button>
        <Button size="xs" radius="xl" variant={mode === 'close' ? 'filled' : 'default'} color="red" onClick={() => setMode('close')}>Close without using it</Button>
      </Group>
      {mode === 'draft' ? (
        <Stack gap="sm" mt="md">
          <Select label="Onboarding Officer who continues it" placeholder="Choose an officer" value={officer} onChange={setOfficer}
            data={(officers.data ?? []).map((o) => ({ value: o.id, label: o.displayName }))} />
          <Textarea label="Note" required placeholder="Why it is being continued, and anything the officer should know"
            value={text} onChange={(e) => setText(e.currentTarget.value)} autosize minRows={2} maxLength={2000} />
          <Text size="xs" c="dimmed">A new draft is created with this data. The officer checks it with the company, adds documents and signatures, and submits it.</Text>
          {draft.error && <ApiError error={draft.error} />}
          <Group justify="flex-end"><Button color="teal" radius="xl" disabled={!officer || !text.trim()} loading={draft.isPending} onClick={() => draft.mutate()}>Create draft</Button></Group>
        </Stack>
      ) : (
        <Stack gap="sm" mt="md">
          <Textarea label="Reason" required placeholder="e.g. Test data from a training tablet" value={text}
            onChange={(e) => setText(e.currentTarget.value)} autosize minRows={2} maxLength={2000} />
          {close.error && <ApiError error={close.error} />}
          <Group justify="flex-end"><Button color="red" radius="xl" disabled={!text.trim()} loading={close.isPending} onClick={() => close.mutate()}>Close it</Button></Group>
        </Stack>
      )}
    </Modal>
  )
}

/** Company details and people from a tablet, read-only. */
export function SnapshotView({ snapshot }: { snapshot: DraftSnapshot }) {
  const d = snapshot.details
  return (
    <div>
      <dl className="fact-list">
        <dt>Company</dt><dd>{d.legalName || '—'}</dd>
        <dt>PACRA number</dt><dd>{d.registrationNumber || '—'}</dd>
        <dt>RTSA licence</dt><dd>{d.operatingLicenceNumber || '—'}</dd>
        <dt>District</dt><dd>{[d.district, d.province].filter(Boolean).join(', ') || '—'}</dd>
        <dt>Contact</dt><dd>{[d.contactPhone, d.contactEmail].filter(Boolean).join(' · ') || '—'}</dd>
        <dt>People</dt><dd>{snapshot.representatives.map((r) => [r.details.fullName, r.details.capacity].filter(Boolean).join(', ')).join('; ') || '—'}</dd>
      </dl>
    </div>
  )
}
