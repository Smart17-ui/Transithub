import type { ReactNode } from 'react'
import { Badge, Button, Group, Paper, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import { PublicPage } from '../components/PublicPage'
import { IconBuilding, IconChart, IconRoute, IconShield, IconTicket } from './LandingPage'

type Service = { name: string; text: string; available: boolean; icon: ReactNode }

const operatorServices: Service[] = [
  { name: 'Company registration', available: true, icon: <IconShield />,
    text: 'Apply to join TransitHub. Our team checks your company and licence, and once you are approved you receive your own TransitHub company code.' },
  { name: 'Staff management', available: false, icon: <IconBuilding />,
    text: 'Keep your drivers’ and staff records, licences and training in one place.' },
  { name: 'Routes, timetables and trips', available: false, icon: <IconRoute />,
    text: 'Plan your routes and timetables, set your fares, and assign a driver and a bus to every trip.' },
  { name: 'Bus and maintenance records', available: false, icon: <IconRoute />,
    text: 'Keep track of every bus, its inspections and when it is due for servicing.' },
  { name: 'Ticket sales', available: false, icon: <IconTicket />,
    text: 'Sell tickets online, at your counters and through agents, without ever selling the same seat twice.' },
  { name: 'Luggage and parcels', available: false, icon: <IconTicket />,
    text: 'Accept and track luggage and parcels from drop-off to collection.' },
  { name: 'Payments', available: false, icon: <IconChart />,
    text: 'Accept MTN MoMo and Airtel Money, with ticket money paid straight into your company’s own account.' },
  { name: 'Business reports', available: false, icon: <IconChart />,
    text: 'See how your routes and branches are performing, and how much you are earning.' },
]

const travellerServices: Service[] = [
  { name: 'Find your journey', available: false, icon: <IconRoute />,
    text: 'Compare departure times, prices and available seats across bus companies in one place.' },
  { name: 'Book without an account', available: false, icon: <IconTicket />,
    text: 'Book with just your name and mobile number. No account needed.' },
  { name: 'Your ticket, your way', available: false, icon: <IconTicket />,
    text: 'Get your ticket the moment you pay: on your phone, by email or SMS, or printed at the booking office. Download or reprint it at any time.' },
  { name: 'Pay with mobile money', available: false, icon: <IconChart />,
    text: 'Pay safely with MTN MoMo or Airtel Money. Your ticket is issued as soon as your payment goes through.' },
  { name: 'Change your plans', available: false, icon: <IconShield />,
    text: 'Change or cancel your trip using your booking reference and a code we send to your phone.' },
  { name: 'Your passenger account', available: false, icon: <IconBuilding />,
    text: 'Sign in with your phone number for faster booking and all your trips in one place.' },
  { name: 'Send a parcel', available: false, icon: <IconTicket />,
    text: 'Send parcels with trusted bus companies and follow them all the way to collection.' },
]

export function ServicesPage() {
  return (
    <PublicPage eyebrow="Services" title="Everything you need, in one place"
      lead="We are launching in stages. Company registration is open now, and the services below are on their way.">
      <ServiceSection id="operators" title="For bus and coach companies"
        lead="Fill more seats, get paid faster and run your whole business from one place."
        services={operatorServices}
        action={<Button radius="xl" color="teal" component={Link} to="/register">Register your company</Button>} />
      <ServiceSection id="travellers" title="For travellers"
        lead="A simple way to book your bus and send parcels with companies you can trust."
        services={travellerServices} />
    </PublicPage>
  )
}

function ServiceSection({ id, title, lead, services, action }: {
  id: string; title: string; lead: string; services: Service[]; action?: ReactNode
}) {
  return (
    <section className="landing-section" id={id} aria-labelledby={`${id}-title`}>
      <Group justify="space-between" align="flex-end" mb="lg" gap="md">
        <div>
          <Title order={2} id={`${id}-title`} className="section-title">{title}</Title>
          <Text c="dimmed" mt={8} maw={640}>{lead}</Text>
        </div>
        {action}
      </Group>
      <div className="service-grid">
        {services.map((service) => (
          <Paper key={service.name} className={`service-card ${service.available ? 'is-available' : ''}`} radius="lg" p="lg">
            <Group justify="space-between" align="flex-start" wrap="nowrap">
              <span className="module-icon" aria-hidden>{service.icon}</span>
              <Badge size="sm" radius="xl" variant="light" color={service.available ? 'teal' : 'gray'}>
                {service.available ? 'Available now' : 'Coming soon'}
              </Badge>
            </Group>
            <Text fw={700} mt="md">{service.name}</Text>
            <Text size="sm" c="dimmed" mt={6}>{service.text}</Text>
          </Paper>
        ))}
      </div>
      <Text size="xs" c="dimmed" mt="md">
        Want to know when a service launches? <Link to="/contact">Get in touch</Link> and we will keep you posted.
      </Text>
    </section>
  )
}
