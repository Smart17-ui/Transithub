import { useState } from 'react'
import { Anchor, Badge, Button, Group, Paper, SegmentedControl, Stack, Text, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { Enquiry, EnquiryTopic } from '../api/types'
import { ApiError, CenterMessage, formatDateTime, useMe, useStaffNames } from '../components/common'

const topicLabels: Record<EnquiryTopic, string> = {
  COMPANY_REGISTRATION: 'Bus company wants to join',
  TRAVEL: 'Travel question',
  PARTNERSHIP: 'Partnership or media',
  OTHER: 'Other',
}

/** Messages sent through the website's Contact form. */
export function EnquiriesPage({ token }: { token: string }) {
  const { me, can } = useMe(token)
  const [filter, setFilter] = useState<'NEW' | 'RESOLVED' | 'ALL'>('NEW')
  const query = useQuery({
    queryKey: ['enquiries', filter],
    queryFn: () => api.enquiries(token, filter === 'ALL' ? null : filter),
    enabled: !!me && can('enquiry.manage'),
  })
  const name = useStaffNames(token, query.data?.items.map((e) => e.resolvedBy) ?? [])

  if (me && !can('enquiry.manage')) {
    return <CenterMessage title="Enquiries are not available for your role" detail="Contact your administrator if you need access." />
  }

  return (
    <Stack gap="lg">
      <div className="eyebrow"><Link to="/">Applications</Link> / enquiries</div>
      <Group justify="space-between" align="flex-end">
        <div>
          <Title order={1} className="page-title">Enquiries</Title>
          <Text c="dimmed" mt={6}>Messages sent through the Contact page of the TransitHub website.</Text>
        </div>
        <SegmentedControl value={filter} onChange={(v) => setFilter(v as typeof filter)}
          data={[{ value: 'NEW', label: 'To answer' }, { value: 'RESOLVED', label: 'Answered' }, { value: 'ALL', label: 'All' }]} />
      </Group>
      {query.error && <ApiError error={query.error} />}
      {query.isPending && <Text c="dimmed">Loading enquiries…</Text>}
      {query.data?.items.length === 0 && (
        <Paper className="panel" radius="lg" p="xl"><Text c="dimmed" ta="center">
          {filter === 'NEW' ? 'All caught up. There are no enquiries waiting for an answer.' : 'No enquiries to show.'}
        </Text></Paper>
      )}
      {query.data?.items.map((enquiry) => (
        <EnquiryCard key={enquiry.id} token={token} enquiry={enquiry} resolvedByName={name(enquiry.resolvedBy)} />
      ))}
    </Stack>
  )
}

function EnquiryCard({ token, enquiry, resolvedByName }: { token: string; enquiry: Enquiry; resolvedByName: string }) {
  const queryClient = useQueryClient()
  const resolve = useMutation({
    mutationFn: () => api.resolveEnquiry(token, enquiry.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['enquiries'] }),
  })
  const replySubject = encodeURIComponent(`Your message to TransitHub (${enquiry.reference})`)
  return (
    <Paper className="panel" radius="lg" p="xl">
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <div>
          <Group gap="xs">
            <Text fw={700} size="lg">{enquiry.fullName}</Text>
            {enquiry.companyName && <Text c="dimmed">· {enquiry.companyName}</Text>}
          </Group>
          <Text size="sm" c="dimmed">
            {enquiry.reference} · {topicLabels[enquiry.topic]} · received {formatDateTime(enquiry.receivedAt)}
          </Text>
        </div>
        {enquiry.status === 'NEW'
          ? <Badge color="orange" variant="light" radius="xl">To answer</Badge>
          : <Badge color="green" variant="light" radius="xl">Answered</Badge>}
      </Group>
      <Text mt="md" style={{ whiteSpace: 'pre-wrap' }}>{enquiry.message}</Text>
      <Group mt="md" gap="lg">
        <Anchor href={`mailto:${enquiry.email}?subject=${replySubject}`}>{enquiry.email}</Anchor>
        {enquiry.phone && <Anchor href={`tel:${enquiry.phone.replace(/\s/g, '')}`}>{enquiry.phone}</Anchor>}
      </Group>
      <Group justify="space-between" mt="lg">
        <Text size="xs" c="dimmed">
          {enquiry.status === 'RESOLVED' ? `Marked as answered by ${resolvedByName} on ${formatDateTime(enquiry.resolvedAt)}` : ''}
        </Text>
        {enquiry.status === 'NEW' && (
          <Button size="xs" radius="xl" variant="light" color="teal" loading={resolve.isPending} onClick={() => resolve.mutate()}>
            Mark as answered
          </Button>
        )}
      </Group>
      {resolve.error && <ApiError error={resolve.error} />}
    </Paper>
  )
}
