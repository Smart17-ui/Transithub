import { Button, Paper, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import coachHighway from '../assets/photos/coach-highway.jpg'
import driver from '../assets/photos/driver.jpg'
import { company } from '../brand/company'
import { PublicPage } from '../components/PublicPage'
import { RouteArt } from '../components/RouteArt'
import { IconChart, IconLock, IconRoute, IconScale, IconShield } from './LandingPage'

/**
 * About TransitHub. Only statements the company can stand behind: mission,
 * what we do and our principles. Company history, leadership and figures
 * are added when the business supplies them.
 */
const principles = [
  { icon: <IconShield />, title: 'We check every company',
    text: 'Every bus company is checked by our team before it can sell tickets, so you know who you are travelling with.' },
  { icon: <IconRoute />, title: 'Made for Zambia',
    text: 'Designed around how people in Zambia travel and pay: intercity coaches, booking offices and mobile money.' },
  { icon: <IconLock />, title: 'Your information is safe',
    text: 'We protect your personal details in line with Zambia’s Data Protection Act.' },
  { icon: <IconScale />, title: 'Fair and open',
    text: 'Every decision comes with clear reasons, and companies can appeal if they disagree.' },
]

const whatWeDo = [
  {
    image: driver, alt: 'A smiling coach driver welcoming passengers', title: 'For passengers',
    text: 'One place to find a trusted bus, book a seat and pay with mobile money, with your ticket delivered the way you prefer.',
    points: ['Only checked bus companies', 'Book online or at the counter', 'Your ticket instantly, your way'],
  },
  {
    image: coachHighway, alt: 'A coach on an open road', title: 'For bus and coach companies',
    text: 'Simple tools to sell more tickets and run the whole business, from buses and staff to parcels and money.',
    points: ['Sell online, at counters and through agents', 'Ticket money paid to your own account', 'Everything in one place'],
  },
]

export function AboutPage() {
  return (
    <PublicPage eyebrow="About us" title="About TransitHub"
      lead="TransitHub brings Zambia’s bus and coach travel together in one place: an easy, trusted way for passengers to travel, and simple tools for bus companies to run their business."
      aside={<MissionStatement />}>

      <section className="landing-section" aria-labelledby="what-title">
        <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">What we do</Text>
        <Title order={2} id="what-title" className="section-title">Two sides of every journey</Title>
        <div className="about-split">
          {whatWeDo.map((side) => (
            <Paper key={side.title} className="about-card" radius="xl">
              <img src={side.image} alt={side.alt} className="about-card-photo" width={612} height={300} loading="lazy" />
              <div className="about-card-body">
                <Text fw={700} size="xl">{side.title}</Text>
                <Text c="dimmed" mt={8} lh={1.65}>{side.text}</Text>
                <ul className="tick-list">{side.points.map((p) => <li key={p}>{p}</li>)}</ul>
              </div>
            </Paper>
          ))}
        </div>
      </section>

      <section className="landing-section" aria-labelledby="principles-title">
        <div className="section-heading section-heading-center">
          <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">Our principles</Text>
          <Title order={2} id="principles-title" className="section-title">What we stand for</Title>
        </div>
        <div className="principle-grid">
          {principles.map((p) => (
            <article key={p.title} className="trust-card">
              <span className="trust-icon" aria-hidden>{p.icon}</span>
              <Text fw={700} size="lg" mt="lg">{p.title}</Text>
              <Text size="sm" c="dimmed" mt={8} lh={1.65}>{p.text}</Text>
            </article>
          ))}
        </div>
      </section>

      <section className="cta-band" aria-labelledby="about-cta-title">
        <RouteArt />
        <div className="cta-content">
          <Text size="xs" fw={700} tt="uppercase" lts={1} className="cta-eyebrow">Join us</Text>
          <Title order={2} id="about-cta-title" className="cta-title">Run a bus or coach company?</Title>
          <Text className="cta-lead">Join TransitHub and give your passengers an easy, trusted way to book with you.</Text>
        </div>
        <div className="cta-actions">
          <Button size="lg" radius="xl" color="teal" component={Link} to="/register" fullWidth>
            Register your company <span className="button-arrow">→</span>
          </Button>
          <Button size="lg" radius="xl" variant="outline" className="hero-secondary" component={Link} to="/contact" fullWidth>
            Talk to our team
          </Button>
          {company.phone && (
            <Text size="sm" className="cta-phone">
              Prefer to talk? Call <a href={`tel:${company.phone.replace(/\s/g, '')}`}>{company.phone}</a>
            </Text>
          )}
        </div>
      </section>
    </PublicPage>
  )
}

/** The mission, shown as a highlighted statement in the page header. */
function MissionStatement() {
  return (
    <figure className="mission-quote">
      <span className="trust-icon" aria-hidden><IconChart /></span>
      <Text size="xs" fw={700} tt="uppercase" lts={1} className="page-hero-eyebrow" mt="md">Our mission</Text>
      <blockquote className="mission-quote-text">To make bus travel in Zambia safer, simpler and more reliable.</blockquote>
    </figure>
  )
}
