import { useState } from 'react'
import { Anchor, Badge, Button, Group, Paper, Text, Title } from '@mantine/core'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import coachHighway from '../assets/photos/coach-highway.jpg'
import type { CompanyDetails } from '../api/types'
import { ApiError, CenterMessage, useMe } from '../components/common'
import { DetailsFields, emptyDetails, normalizeDetails } from '../components/DetailsFields'

export function NewRegistrationPage({ token }: { token: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [details, setDetails] = useState<CompanyDetails>(emptyDetails)
  const { me, can } = useMe(token)
  const mutation = useMutation({
    mutationFn: () => api.startRegistration(token, normalizeDetails(details)),
    onSuccess: (registration) => {
      queryClient.invalidateQueries({ queryKey: ['registrations'] })
      navigate(`/registrations/${registration.id}`)
    },
  })

  if (me && !can('registration.capture')) {
    return <CenterMessage title="Application preparation is not available for your role" detail="Applications assigned to you will appear on your Applications page." />
  }

  return (
    <div className="content-grid">
      <section>
        <div className="eyebrow"><Link to="/">Applications</Link> / new application</div>
        <Title order={1} className="page-title">New application</Title>
        <Text c="dimmed" mt={6} mb="xl">Begin with the company details. Representatives, supporting documents and declarations can be added next.</Text>
        <Paper className="panel" radius="lg" p="xl">
          <Group justify="space-between" mb="lg" align="flex-start">
            <div><Text fw={700} size="lg">Company details</Text><Text size="sm" c="dimmed" mt={4}>All details may be edited until the application is submitted.</Text></div>
            <Badge color="teal" variant="light">Online application</Badge>
          </Group>
          <DetailsFields value={details} onChange={setDetails} />
          {mutation.error && <ApiError error={mutation.error} />}
          <Group justify="space-between" mt="lg">
            <Anchor component={Link} to="/" size="sm" c="dimmed">Cancel</Anchor>
            <Button color="teal" radius="xl" onClick={() => mutation.mutate()} loading={mutation.isPending}>
              Create application <span className="button-arrow">→</span>
            </Button>
          </Group>
        </Paper>
      </section>
      <aside className="side-column">
        <Paper className="tip-card" radius="lg">
          <img src={coachHighway} alt="A coach on an open road" className="tip-photo" />
          <div className="tip-body">
            <Text size="xs" fw={700} tt="uppercase" lts={1} className="tip-kicker">Field capture</Text>
            <Text fw={600} mt="sm">Registering in the field?</Text>
            <Text size="sm" mt={6} className="tip-copy">Offline capture on TransitHub field devices is planned for a future release. This form is for online applications.</Text>
          </div>
        </Paper>
      </aside>
    </div>
  )
}
