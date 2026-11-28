import { useState } from 'react'
import {
  Alert, Anchor, Badge, Button, Checkbox, Group, Loader, Modal, Paper, Select, SimpleGrid, Stack, Table, Text, TextInput,
  Textarea, Title,
} from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { StaffUser } from '../api/types'
import { ApiError, formatDate, roleNames, useMe } from '../components/common'

/**
 * Staff (backlog #4): Platform Administrators add TransitHub staff, give and remove
 * roles, and disable or re-enable accounts. Nobody changes their own access; every
 * change is recorded in the audit trail.
 */

const staffRoles: { code: string; text: string }[] = [
  { code: 'REGISTRATION_OFFICER', text: 'Prepares and submits applications for bus companies, including in the field.' },
  { code: 'REGISTRATION_SUPERVISOR', text: 'Oversees all applications, assigns reviews, answers enquiries.' },
  { code: 'VERIFICATION_OFFICER', text: 'Checks applications with PACRA and RTSA, then approves or declines.' },
  { code: 'PLATFORM_ADMINISTRATOR', text: 'Manages staff and their access, and views the audit trail.' },
]

type Dialog =
  | { kind: 'add' }
  | { kind: 'grant'; user: StaffUser }
  | { kind: 'revoke'; user: StaffUser; role: string }
  | { kind: 'enable' | 'disable'; user: StaffUser }

export function StaffPage({ token }: { token: string }) {
  const { me, can } = useMe(token)
  const staff = useQuery({ queryKey: ['staff-users'], queryFn: () => api.staffUsers(token), enabled: can('platform-user.manage') })
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const resend = useMutation({
    mutationFn: (user: StaffUser) => api.resendStaffSetup(token, user.id),
    onSuccess: (_, user) => setNotice(`A new set-up email was sent to ${user.email ?? user.displayName}.`),
  })
  const close = (message?: string) => {
    setDialog(null)
    if (message) setNotice(message)
    queryClient.invalidateQueries({ queryKey: ['staff-users'] })
  }

  if (me && !can('platform-user.manage')) {
    return <Alert color="red" title="Administrators only">Only Platform Administrators manage staff.</Alert>
  }

  const list = staff.data ?? []
  const active = list.filter((u) => u.status === 'ACTIVE').length

  return (
    <Stack gap="lg">
      <Group justify="space-between" align="flex-end">
        <div>
          <div className="eyebrow">Workspace / staff</div>
          <Title order={1} className="page-title">Staff</Title>
          <Text c="dimmed" mt={6} maw={680}>
            Everyone who works on TransitHub and what they may do. New staff receive an email to choose a password and
            set up an authenticator app. Changes take effect immediately.
          </Text>
        </div>
        <Button color="teal" radius="xl" onClick={() => setDialog({ kind: 'add' })}>Add staff member <span className="button-arrow">+</span></Button>
      </Group>

      {notice && <Alert color="teal" withCloseButton onClose={() => setNotice(null)}>{notice}</Alert>}
      {resend.error && <ApiError error={resend.error} />}

      <Paper className="panel queue-panel" radius="lg">
        <Group justify="space-between" className="queue-padding">
          <Text size="sm" c="dimmed">{staff.isPending ? 'Loading staff…' : `${active} active · ${list.length - active} disabled`}</Text>
        </Group>
        {staff.error ? <div className="queue-padding"><ApiError error={staff.error} /></div> : staff.isPending
          ? <Group justify="center" py="xl"><Loader color="teal" /></Group> : (
          <Table.ScrollContainer minWidth={760}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr><Table.Th>Person</Table.Th><Table.Th>Roles</Table.Th><Table.Th>Status</Table.Th><Table.Th /></Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {list.map((user) => {
                  const self = user.id === me?.userId
                  const disabled = user.status === 'DISABLED'
                  return (
                    <Table.Tr key={user.id} className={disabled ? 'staff-disabled' : undefined}>
                      <Table.Td>
                        <Text fw={600} size="sm">{user.displayName}{self && <Badge ml={6} size="xs" variant="light">You</Badge>}</Text>
                        <Text size="xs" c="dimmed">{user.email ?? '—'} · since {formatDate(user.createdAt)}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Group gap={6}>
                          {user.roles.length === 0 && <Text size="xs" c="dimmed">No roles</Text>}
                          {user.roles.map((r) => (
                            <Badge key={r.role} variant="light" color="teal" radius="xl"
                              rightSection={!self && !disabled ? (
                                <Anchor component="button" size="xs" c="dimmed" aria-label={`Remove ${roleNames[r.role] ?? r.role}`}
                                  onClick={() => setDialog({ kind: 'revoke', user, role: r.role })}>✕</Anchor>
                              ) : null}>
                              {roleNames[r.role] ?? r.role}
                            </Badge>
                          ))}
                        </Group>
                      </Table.Td>
                      <Table.Td>
                        <Badge variant="light" color={disabled ? 'gray' : 'green'}>{disabled ? 'Disabled' : 'Active'}</Badge>
                      </Table.Td>
                      <Table.Td ta="right">
                        {!self && (
                          <Group gap="xs" justify="flex-end" wrap="nowrap">
                            {!disabled && <Button size="xs" variant="light" color="teal" onClick={() => setDialog({ kind: 'grant', user })}>Add role</Button>}
                            {!disabled && <Anchor component="button" size="xs" onClick={() => resend.mutate(user)}>Resend set-up email</Anchor>}
                            <Anchor component="button" size="xs" c={disabled ? undefined : 'var(--th-danger-text)'}
                              onClick={() => setDialog({ kind: disabled ? 'enable' : 'disable', user })}>{disabled ? 'Re-enable' : 'Disable'}</Anchor>
                          </Group>
                        )}
                      </Table.Td>
                    </Table.Tr>
                  )
                })}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Paper>

      {dialog?.kind === 'add' && <AddStaffDialog token={token} onClose={close} />}
      {dialog?.kind === 'grant' && <GrantDialog token={token} user={dialog.user} onClose={close} />}
      {(dialog?.kind === 'revoke' || dialog?.kind === 'enable' || dialog?.kind === 'disable') &&
        <ReasonDialog token={token} dialog={dialog} onClose={close} />}
    </Stack>
  )
}

function AddStaffDialog({ token, onClose }: { token: string; onClose: (message?: string) => void }) {
  const [form, setForm] = useState({ email: '', firstName: '', lastName: '' })
  const [roles, setRoles] = useState<string[]>([])
  const emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())
  const add = useMutation({
    mutationFn: () => api.addStaff(token, { ...form, email: form.email.trim(), roles }),
    onSuccess: (result) => onClose(result.reusedExistingAccount
      ? `${form.firstName} already had a TransitHub account; it now has the new roles. They will be asked to set up an authenticator at their next sign-in.`
      : `${form.firstName} was added. A set-up email is on its way to ${form.email.trim()}.`),
  })
  return (
    <Modal opened onClose={() => onClose()} title={<Text fw={700}>Add a staff member</Text>} radius="lg" size="lg" centered>
      <Stack gap="sm">
        <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm">
          <TextInput label="First name" required value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.currentTarget.value })} data-autofocus />
          <TextInput label="Last name" required value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.currentTarget.value })} />
        </SimpleGrid>
        <TextInput label="Work email" type="email" required value={form.email} placeholder="name@transithub.co.zm"
          error={form.email && !emailOk ? 'Enter a valid email address' : null}
          onChange={(e) => setForm({ ...form, email: e.currentTarget.value })}
          description="If this person already has a TransitHub account with this email, it is reused." />
        <Text fw={600} size="sm" mt="xs">Roles</Text>
        {staffRoles.map((r) => (
          <Checkbox key={r.code} checked={roles.includes(r.code)} label={roleNames[r.code] ?? r.code} description={r.text}
            onChange={(e) => setRoles(e.currentTarget.checked ? [...roles, r.code] : roles.filter((x) => x !== r.code))} />
        ))}
        {add.error && <ApiError error={add.error} />}
        <Group justify="flex-end" mt="sm">
          <Button variant="default" radius="xl" onClick={() => onClose()}>Cancel</Button>
          <Button color="teal" radius="xl" loading={add.isPending} onClick={() => add.mutate()}
            disabled={!form.firstName.trim() || !form.lastName.trim() || !emailOk || roles.length === 0}>Add and send set-up email</Button>
        </Group>
      </Stack>
    </Modal>
  )
}

function GrantDialog({ token, user, onClose }: { token: string; user: StaffUser; onClose: (message?: string) => void }) {
  const held = new Set(user.roles.map((r) => r.role))
  const options = staffRoles.filter((r) => !held.has(r.code))
  const [role, setRole] = useState<string | null>(options[0]?.code ?? null)
  const grant = useMutation({
    mutationFn: () => api.grantStaffRole(token, user.id, role!),
    onSuccess: () => onClose(`${user.displayName} is now also ${roleNames[role!] ?? role}.`),
  })
  return (
    <Modal opened onClose={() => onClose()} title={<Text fw={700}>Add a role for {user.displayName}</Text>} radius="lg" centered>
      {options.length === 0 ? <Text size="sm" c="dimmed">{user.displayName} already holds every staff role.</Text> : (
        <Stack gap="sm">
          <Select data={options.map((r) => ({ value: r.code, label: roleNames[r.code] ?? r.code }))} value={role}
            onChange={setRole} allowDeselect={false} label="Role" />
          <Text size="sm" c="dimmed">{options.find((r) => r.code === role)?.text}</Text>
          <Text size="xs" c="dimmed">A person may hold several roles. They still cannot check or decide an application they helped prepare.</Text>
          {grant.error && <ApiError error={grant.error} />}
          <Group justify="flex-end"><Button color="teal" radius="xl" loading={grant.isPending} onClick={() => grant.mutate()}>Add role</Button></Group>
        </Stack>
      )}
    </Modal>
  )
}

function ReasonDialog({ token, dialog, onClose }: {
  token: string
  dialog: { kind: 'revoke'; user: StaffUser; role: string } | { kind: 'enable' | 'disable'; user: StaffUser }
  onClose: (message?: string) => void
}) {
  const [reason, setReason] = useState('')
  const { user } = dialog
  const run = useMutation({
    mutationFn: () => dialog.kind === 'revoke'
      ? api.revokeStaffRole(token, user.id, dialog.role, reason.trim())
      : api.setStaffEnabled(token, user.id, dialog.kind === 'enable', reason.trim()),
    onSuccess: () => onClose(dialog.kind === 'revoke' ? `${roleNames[dialog.role] ?? dialog.role} was removed from ${user.displayName}.`
      : dialog.kind === 'disable' ? `${user.displayName} can no longer sign in.` : `${user.displayName} can sign in again.`),
  })
  const copy = dialog.kind === 'revoke'
    ? { title: `Remove ${roleNames[dialog.role] ?? dialog.role} from ${user.displayName}?`, text: 'It stops working on their next action.', button: 'Remove role', color: 'red' }
    : dialog.kind === 'disable'
      ? { title: `Disable ${user.displayName}?`, text: 'They will not be able to sign in or do anything in TransitHub. Their past work stays on record.', button: 'Disable account', color: 'red' }
      : { title: `Re-enable ${user.displayName}?`, text: 'They can sign in again with the roles they still hold.', button: 'Re-enable', color: 'teal' }
  return (
    <Modal opened onClose={() => onClose()} title={<Text fw={700}>{copy.title}</Text>} radius="lg" centered>
      <Text size="sm" c="dimmed">{copy.text}</Text>
      <Textarea mt="md" label="Reason" required placeholder="Recorded in the audit trail" value={reason}
        onChange={(e) => setReason(e.currentTarget.value)} autosize minRows={2} maxLength={2000} />
      {run.error && <ApiError error={run.error} />}
      <Group justify="flex-end" mt="md">
        <Button variant="default" radius="xl" onClick={() => onClose()}>Cancel</Button>
        <Button color={copy.color} radius="xl" disabled={!reason.trim()} loading={run.isPending} onClick={() => run.mutate()}>{copy.button}</Button>
      </Group>
    </Modal>
  )
}
