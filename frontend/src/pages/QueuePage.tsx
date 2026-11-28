import { useState } from 'react'
import { Badge, Button, Group, Pagination, Paper, Table, Tabs, Text, TextInput, Title } from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { ApplicantHome } from './ApplicantHome'
import type { RegistrationListItem, RegistrationStatus } from '../api/types'
import { ApiError, StatusBadge, formatDateTime, roleIntro, useMe } from '../components/common'

type Stage = { key: string; label: string; hint: string; statuses: RegistrationStatus[] }

/** Work queues follow the application lifecycle: one Compliance Officer reviews and decides. */
const stages: Stage[] = [
  { key: 'drafts', label: 'Drafts', hint: 'Applications in preparation, not yet submitted.', statuses: ['DRAFT'] },
  { key: 'submitted', label: 'Awaiting review', hint: 'Submitted applications awaiting assignment to a Compliance Officer.', statuses: ['SUBMITTED'] },
  { key: 'review', label: 'Under review', hint: 'Applications under compliance review, including those awaiting additional information.', statuses: ['UNDER_VERIFICATION', 'AWAITING_INFORMATION', 'RECOMMENDED'] },
  { key: 'closed', label: 'Completed', hint: 'Approved, declined and withdrawn applications.', statuses: ['APPROVED', 'REJECTED', 'WITHDRAWN'] },
  { key: 'all', label: 'All', hint: 'Every application available to your role.', statuses: [] },
]

const PAGE_SIZE = 20

export function QueuePage({ token }: { token: string }) {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { me, error: meError, can } = useMe(token)
  const page = Math.max(1, Number(params.get('page')) || 1)
  const [search, setSearch] = useState(params.get('search') ?? '')
  const [debouncedSearch] = useDebouncedValue(search, 300)

  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
    setParams(next, { replace: true })
  }

  // Small count queries so each tab shows how much is in it.
  const counts = useQueries({
    queries: stages.map((s) => ({
      queryKey: ['registrations', s.key, debouncedSearch, 'count'],
      queryFn: () => api.listRegistrations(token, { statuses: s.statuses, search: debouncedSearch, size: 1 }),
    })),
  })
  const countsReady = counts.every((c) => c.data)
  const firstWithWork = stages.find((s, i) => s.key !== 'all' && (counts[i]?.data?.total ?? 0) > 0) ?? stages.at(-1)!
  const stage = stages.find((s) => s.key === params.get('stage')) ?? (countsReady ? firstWithWork : null)

  const list = useQuery({
    enabled: !!stage,
    queryKey: ['registrations', stage?.key, debouncedSearch, page],
    queryFn: () => api.listRegistrations(token, { statuses: stage!.statuses, search: debouncedSearch, page: page - 1, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  // Until we know who this is, show nothing role-specific: never the staff queue by default.
  if (!me) {
    return meError
      ? <Paper className="panel center-message" radius="lg" p="xl"><ApiError error={meError} /></Paper>
      : <Paper className="panel center-message" radius="lg" p="xl"><Text c="dimmed">Loading your account…</Text></Paper>
  }

  if (!can('registration.view')) {
    if (can('enquiry.manage')) return <Navigate to="/enquiries" replace />
    // No roles at all: someone who signed up to bring their bus company to TransitHub.
    if (me.emailNotConfirmed || me.roles.length === 0) return <ApplicantHome token={token} name={me.displayName} emailNotConfirmed={!!me.emailNotConfirmed} />
    return <CompanyHome name={me.displayName} />
  }

  const totalPages = list.data ? Math.max(1, Math.ceil(list.data.total / PAGE_SIZE)) : 1

  return (
    <div>
      <Group justify="space-between" align="flex-end" mb="lg" className="queue-heading">
        <div>
          <div className="eyebrow">Workspace / registrations</div>
          <Title order={1} className="page-title">Applications</Title>
          <Text c="dimmed" mt={6} maw={680}>
            {me?.roles.length ? roleIntro(me.roles) : 'Applications available to your role, ordered by most recent activity.'}
          </Text>
        </div>
        <Group gap="sm">
        <Button variant="default" radius="xl" component={Link} to="/records">Company records</Button>
        {can('enquiry.manage') && (
          <Button variant="default" radius="xl" component={Link} to="/enquiries">Enquiries</Button>
        )}
        {can('registration.capture') && (
          <Button color="teal" radius="xl" component={Link} to="/registrations/new">
            New application <span className="button-arrow">+</span>
          </Button>
        )}
        </Group>
      </Group>

      <Paper className="panel" radius="lg" p={0}>
        <Tabs value={stage?.key ?? null} onChange={(key) => update({ stage: key, page: null })} color="teal">
          <Tabs.List px="md" pt="sm">
            {stages.map((s, i) => (
              <Tabs.Tab key={s.key} value={s.key}
                rightSection={counts[i].data ? <Badge size="sm" variant="light" color={s.key === stage?.key ? 'teal' : 'gray'}>{counts[i].data.total}</Badge> : null}>
                {s.label}
              </Tabs.Tab>
            ))}
          </Tabs.List>
        </Tabs>

        <Group justify="space-between" px="lg" py="md" gap="sm">
          <Text size="sm" c="dimmed">{stage?.hint ?? "Finding your work…"}</Text>
          <TextInput placeholder="Search by name, PACRA number or reference" value={search} w={300}
            onChange={(event) => { setSearch(event.currentTarget.value); update({ search: event.currentTarget.value || null, page: null }) }} />
        </Group>

        {(list.error ?? counts.find((c) => c.error)?.error)
          ? <div className="queue-padding"><ApiError error={(list.error ?? counts.find((c) => c.error)?.error)!} /></div> : (
          <Table.ScrollContainer minWidth={760}>
            <Table highlightOnHover verticalSpacing="sm" className="queue-table">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Company</Table.Th><Table.Th>Reference</Table.Th><Table.Th>Status</Table.Th>
                  <Table.Th>Prepared by</Table.Th><Table.Th>Compliance Officer</Table.Th><Table.Th>Last updated</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {list.data?.items.map((item) => (
                  <Table.Tr key={item.id} className="queue-row" onClick={() => navigate(`/registrations/${item.id}`)}>
                    <Table.Td>
                      <Text fw={600} size="sm">{item.legalName || 'Untitled application'}</Text>
                      {item.district && <Text size="xs" c="dimmed">{item.district}</Text>}
                    </Table.Td>
                    <Table.Td><Text size="sm" ff="monospace">{item.reference}</Text></Table.Td>
                    <Table.Td><QueueStatus item={item} /></Table.Td>
                    <Table.Td><Text size="sm">{item.capturedByName ?? '—'}</Text></Table.Td>
                    <Table.Td><Text size="sm" c={item.assignedVerifierName ? undefined : 'dimmed'}>{item.assignedVerifierName ?? 'Unassigned'}</Text></Table.Td>
                    <Table.Td><Text size="sm" c="dimmed">{formatDateTime(item.updatedAt)}</Text></Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
        {list.data?.items.length === 0 && (
          <Text c="dimmed" ta="center" py="xl" size="sm">
            {debouncedSearch ? 'No applications match your search.' : 'There are no applications at this stage.'}
          </Text>
        )}
        {list.isPending && !counts.some((c) => c.error) && <Text c="dimmed" ta="center" py="xl" size="sm">Loading applications…</Text>}
        {totalPages > 1 && (
          <Group justify="center" py="md">
            <Pagination total={totalPages} value={page} onChange={(p) => update({ page: String(p) })} color="teal" size="sm" />
          </Group>
        )}
      </Paper>
    </div>
  )
}

function QueueStatus({ item }: { item: RegistrationListItem }) {
  return <StatusBadge status={item.status} />
}

/** Transport-company users have no registration queue; their company workspace is still to come. */
function CompanyHome({ name }: { name: string | null }) {
  return (
    <Paper className="panel center-message" radius="lg" p="xl">
      <Title order={2}>Welcome{name ? `, ${name}` : ''}</Title>
      <Text c="dimmed" mt="sm">
        Your company account is active on TransitHub. Tools to manage your workforce, fleet, trips and ticketing
        will become available here as they are released.
      </Text>
    </Paper>
  )
}
