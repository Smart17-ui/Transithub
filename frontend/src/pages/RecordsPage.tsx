import { useState } from 'react'
import { Anchor, Badge, Button, Group, Loader, Paper, Stack, Text, TextInput, Title } from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { Evidence, RegistrationListItem } from '../api/types'
import { ApiError, StatusBadge, evidenceTypeLabels, formatDate, formatDateTime } from '../components/common'
import { DocumentPreview } from '../components/DocumentPreview'

/**
 * Company records: find any company that has applied, at any stage, and see its
 * documents in one place. Search by company or trading name, PACRA number, RTSA
 * licence number or application reference. Shows only what the person's role may
 * see; documents are never deleted (a removed one stays in the application history).
 */
export function RecordsPage({ token }: { token: string }) {
  const [search, setSearch] = useState('')
  const [term] = useDebouncedValue(search.trim(), 300)
  const results = useQuery({
    queryKey: ['records', term],
    queryFn: () => api.listRegistrations(token, { statuses: [], search: term, size: 50 }),
    placeholderData: keepPreviousData,
  })
  const [open, setOpen] = useState<string | null>(null)

  return (
    <Stack gap="lg">
      <div>
        <div className="eyebrow"><Link to="/">Applications</Link> / Company records</div>
        <Title order={1} className="page-title">Company records</Title>
        <Text c="dimmed" mt={6} maw={720}>
          Every company that has applied to TransitHub, with its details and documents. Search by name, PACRA number,
          RTSA licence number or application reference.
        </Text>
      </div>

      <TextInput size="md" radius="xl" placeholder="e.g. Smart travels, 123212121233 or REG-2026-000004"
        value={search} onChange={(e) => setSearch(e.currentTarget.value)} aria-label="Search company records" data-autofocus />

      {results.error && <ApiError error={results.error} />}
      {results.isPending && <Group justify="center" py="xl"><Loader color="teal" /></Group>}
      {results.data && (
        <>
          <Text size="sm" c="dimmed">
            {results.data.total === 0 ? 'No companies match your search.'
              : `${results.data.total} ${results.data.total === 1 ? 'company' : 'companies'}${term ? ` matching “${term}”` : ''}`}
          </Text>
          <Stack gap="sm">
            {results.data.items.map((item) => (
              <RecordCard key={item.id} token={token} item={item} expanded={open === item.id}
                onToggle={() => setOpen(open === item.id ? null : item.id)} />
            ))}
          </Stack>
        </>
      )}
    </Stack>
  )
}

function RecordCard({ token, item, expanded, onToggle }: {
  token: string; item: RegistrationListItem; expanded: boolean; onToggle: () => void
}) {
  return (
    <Paper className="panel record-card" radius="lg" p="lg">
      <Group justify="space-between" wrap="wrap" gap="sm">
        <div>
          <Group gap="sm">
            <Text fw={700}>{item.legalName || 'Company name not given yet'}</Text>
            <StatusBadge status={item.status} />
          </Group>
          <Text size="xs" c="dimmed" mt={2}>{item.reference}{item.district ? ` · ${item.district}` : ''} · updated {formatDateTime(item.updatedAt)}</Text>
        </div>
        <Group gap="xs">
          <Button size="xs" radius="xl" variant={expanded ? 'filled' : 'light'} color="teal" onClick={onToggle}>
            {expanded ? 'Hide documents' : 'Show documents'}
          </Button>
          <Button size="xs" radius="xl" variant="default" component={Link} to={`/registrations/${item.id}`}>Open application</Button>
        </Group>
      </Group>
      {expanded && <RecordDocuments token={token} id={item.id} />}
    </Paper>
  )
}

function RecordDocuments({ token, id }: { token: string; id: string }) {
  const registration = useQuery({ queryKey: ['registration', id], queryFn: () => api.getRegistration(token, id) })
  const [preview, setPreview] = useState<Evidence | null>(null)
  const [error, setError] = useState<Error | null>(null)
  const r = registration.data

  if (registration.isPending) return <Group py="md"><Loader size="sm" color="teal" /></Group>
  if (registration.error || !r) return <ApiError error={registration.error ?? new Error('Not available')} />

  return (
    <div className="record-documents">
      <dl className="fact-list fact-list-row">
        <dt>PACRA number</dt><dd>{r.details.registrationNumber || '—'}</dd>
        <dt>RTSA licence</dt><dd>{r.details.operatingLicenceNumber || '—'}</dd>
        <dt>Contact</dt><dd>{[r.details.contactPhone, r.details.contactEmail].filter(Boolean).join(' · ') || '—'}</dd>
        <dt>Directors</dt><dd>{r.representatives.map((p) => p.fullName).join(', ') || '—'}</dd>
      </dl>
      {r.evidence.length === 0 ? <Text size="sm" c="dimmed" mt="sm">No documents on this application.</Text> : (
        <Stack gap="xs" mt="sm">
          {r.evidence.map((e) => (
            <Group key={e.id} justify="space-between" className="list-row" wrap="nowrap">
              <div>
                <Anchor component="button" size="sm" fw={600} onClick={() => setPreview(e)}>{e.fileName}</Anchor>
                <Text size="xs" c="dimmed">{evidenceTypeLabels[e.evidenceType]} · {(e.sizeBytes / 1024).toFixed(0)} KB · {formatDate(e.uploadedAt)}</Text>
              </div>
              <Group gap="sm" wrap="nowrap">
                <Badge variant="light" color="gray">{e.contentType === 'application/pdf' ? 'PDF' : 'Photo'}</Badge>
                <Button size="xs" variant="default" onClick={() => setPreview(e)}>View</Button>
                <Anchor component="button" size="xs" onClick={() => {
                  setError(null)
                  api.downloadEvidence(token, id, e.id, e.fileName).catch((err: Error) => setError(err))
                }}>Download</Anchor>
              </Group>
            </Group>
          ))}
        </Stack>
      )}
      {error && <ApiError error={error} />}
      {preview && <DocumentPreview token={token} registrationId={id} evidence={preview} onClose={() => setPreview(null)} />}
    </div>
  )
}
