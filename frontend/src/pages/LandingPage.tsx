import { useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react'
import { Button, Group, Paper, Text, Title } from '@mantine/core'
import { Link } from 'react-router'
import coachCity from '../assets/photos/coach-city.jpg'
import coachHighway from '../assets/photos/coach-highway.jpg'
import driver from '../assets/photos/driver.jpg'
import { company } from '../brand/company'
import { RouteArt } from '../components/RouteArt'
import { SiteFooter } from '../components/SiteFooter'

/**
 * The public home page, written for customers, not for the team that builds
 * TransitHub: passengers first, then bus and coach companies. It speaks in
 * benefits, never in system mechanics, and says plainly what is available
 * today (company registration) and what is launching soon (booking).
 */
export function LandingPage() {
  return (
    <div className="landing">
      <Hero />
      <ForPassengers />
      <ForCompanies />
      <Joining />
      <Trust />
      <ClosingBand />
      <SiteFooter />
    </div>
  )
}

function Hero() {
  return (
    <section className="hero" aria-labelledby="hero-title">
      <div className="hero-copy">
        <span className="hero-kicker"><span className="hero-dot" aria-hidden />Online booking launching soon</span>
        <Title order={1} id="hero-title" className="hero-title">Bus travel in Zambia,<br /><span>made simple.</span></Title>
        <Text className="hero-lead">
          Find your bus, book your seat and pay with mobile money, all with bus companies that TransitHub has checked.
        </Text>
        <Text className="hero-sublead">
          Run a bus company? TransitHub helps you sell more tickets and run your business with ease.
        </Text>
        <Group mt="xl" gap="md">
          <Button size="md" radius="xl" color="teal" component="a" href="#passengers">
            I’m travelling <span className="button-arrow">→</span>
          </Button>
          <Button size="md" radius="xl" variant="outline" className="hero-secondary" component="a" href="#companies">
            I run a bus company
          </Button>
        </Group>
      </div>

      <div className="hero-visual">
        <img src={coachCity} alt="A modern intercity coach ready for boarding" className="hero-photo" width={640} height={427} />
        <TicketPreview />
      </div>

      <ul className="hero-strip" aria-label="Why travel with TransitHub">
        {heroPoints.map((p) => (
          <li key={p.title}>
            <span className="hero-strip-icon" aria-hidden>{p.icon}</span>
            <span><span className="hero-strip-title">{p.title}</span><span className="hero-strip-text">{p.text}</span></span>
          </li>
        ))}
      </ul>
    </section>
  )
}

const heroPoints = [
  { icon: <IconShield />, title: 'Checked companies', text: 'Every bus company is checked by our team' },
  { icon: <IconWallet />, title: 'Mobile money', text: 'Pay with MTN MoMo or Airtel Money' },
  { icon: <IconTicket />, title: 'Instant tickets', text: 'On your phone, by SMS or email, or printed' },
]

/**
 * An example of the ticket a passenger receives. Illustrative only.
 * Visitors can drag it around the hero (or nudge it with the arrow keys);
 * it stays inside the hero section, and double-click or Escape puts it back.
 */
function TicketPreview() {
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [dragging, setDragging] = useState(false)
  const drag = useRef<{ pointerX: number; pointerY: number; startX: number; startY: number; min: { x: number; y: number }; max: { x: number; y: number } } | null>(null)

  /** How far the ticket may move from its current offset without leaving the hero section. */
  function limits(el: HTMLElement) {
    const box = el.getBoundingClientRect()
    const bounds = (el.closest('.hero') ?? document.body).getBoundingClientRect()
    return {
      min: { x: offset.x + bounds.left - box.left, y: offset.y + bounds.top - box.top },
      max: { x: offset.x + bounds.right - box.right, y: offset.y + bounds.bottom - box.bottom },
    }
  }
  const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi)

  function onPointerDown(e: PointerEvent<HTMLElement>) {
    if (e.button !== 0) return
    e.currentTarget.setPointerCapture(e.pointerId)
    drag.current = { pointerX: e.clientX, pointerY: e.clientY, startX: offset.x, startY: offset.y, ...limits(e.currentTarget) }
    setDragging(true)
  }
  function onPointerMove(e: PointerEvent<HTMLElement>) {
    const d = drag.current
    if (!d) return
    setOffset({
      x: clamp(d.startX + e.clientX - d.pointerX, d.min.x, d.max.x),
      y: clamp(d.startY + e.clientY - d.pointerY, d.min.y, d.max.y),
    })
  }
  function endDrag() {
    drag.current = null
    setDragging(false)
  }
  function onKeyDown(e: KeyboardEvent<HTMLElement>) {
    const step = e.shiftKey ? 40 : 10
    const moves: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }
    if (e.key === 'Escape') { setOffset({ x: 0, y: 0 }); return }
    const move = moves[e.key]
    if (!move) return
    e.preventDefault()
    const { min, max } = limits(e.currentTarget)
    setOffset({ x: clamp(offset.x + move[0], min.x, max.x), y: clamp(offset.y + move[1], min.y, max.y) })
  }

  return (
    <figure className={`hero-ticket ${dragging ? 'is-dragging' : ''}`} aria-label="Example of a TransitHub ticket. Drag or use the arrow keys to move it."
      tabIndex={0} title="Drag me around" style={{ translate: `${offset.x}px ${offset.y}px` }}
      onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={endDrag} onPointerCancel={endDrag}
      onDoubleClick={() => setOffset({ x: 0, y: 0 })} onKeyDown={onKeyDown}>
      <div className="hero-ticket-head">
        <span className="hero-ticket-kicker">Your ticket</span>
        <span className="hero-ticket-status">✓ Paid</span>
      </div>
      <div className="hero-ticket-route">
        <span><span className="hero-ticket-label">From</span><b>Lusaka</b></span>
        <span className="hero-ticket-line" aria-hidden />
        <span className="hero-ticket-to"><span className="hero-ticket-label">To</span><b>Livingstone</b></span>
      </div>
      <div className="hero-ticket-meta">
        <span><span className="hero-ticket-label">Departs</span>06:00</span>
        <span><span className="hero-ticket-label">Seat</span>14A</span>
        <span><span className="hero-ticket-label">Paid with</span>MTN MoMo</span>
      </div>
    </figure>
  )
}

/* ------------------------------------------------------------------ */
/* Passengers                                                           */
/* ------------------------------------------------------------------ */

const journey = [
  { icon: <IconSearch />, title: 'Find your bus', text: 'Search by town and date, and compare departure times, prices and free seats across bus companies.' },
  { icon: <IconSeat />, title: 'Choose your seat', text: 'Pick your seat on the bus layout. No account needed: just your name and phone number.' },
  { icon: <IconWallet />, title: 'Pay with mobile money', text: 'Pay securely with MTN MoMo or Airtel Money. Your seat is held for you while you pay.' },
  { icon: <IconTicket />, title: 'Get your ticket', text: 'Your ticket arrives the moment you pay: on your phone, by SMS or email, or printed at the office.' },
]

function ForPassengers() {
  return (
    <section className="landing-section" id="passengers" aria-labelledby="passengers-title">
      <div className="section-heading section-heading-split">
        <div>
          <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">For passengers</Text>
          <Title order={2} id="passengers-title" className="section-title">Your journey, made easy</Title>
        </div>
        <Text c="dimmed" className="section-lead">
          Booking a bus seat should be as easy as sending money. Here is how it will work when online booking opens.
        </Text>
      </div>

      <ol className="journey-grid">
        {journey.map((step, i) => (
          <li key={step.title} className="journey-card">
            <div className="journey-card-head">
              <span className="module-icon journey-icon" aria-hidden>{step.icon}</span>
              <span className="journey-step">Step {i + 1}</span>
            </div>
            <Text fw={700} size="lg" mt="lg" className="journey-title">{step.title}</Text>
            <Text size="sm" c="dimmed" mt={6} lh={1.65}>{step.text}</Text>
          </li>
        ))}
      </ol>

      <div className="passenger-banner">
        <img src={driver} alt="A smiling coach driver at the wheel" className="passenger-banner-photo" width={480} height={320} loading="lazy" />
        <div className="passenger-banner-body">
          <span className="soon-pill"><span className="hero-dot" aria-hidden />Launching soon</span>
          <Title order={3} className="passenger-banner-title">Online booking is on its way</Title>
          <Text c="dimmed" mt={8} lh={1.65} maw={560}>
            We are bringing Zambia’s bus companies on board first, so that every company you book with has been
            checked by our team. Online booking opens as soon as they are ready.
          </Text>
          <div className="passenger-banner-actions">
            <Button radius="xl" color="teal" component={Link} to="/register">
              Bring your bus company on board <span className="button-arrow">→</span>
            </Button>
            <Button radius="xl" variant="default" component={Link} to="/contact">Ask us a question</Button>
          </div>
        </div>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/* Bus and coach companies                                              */
/* ------------------------------------------------------------------ */

const companyBenefits = [
  'Sell more seats: reach passengers online, at your counters and through agents',
  'No double bookings: every seat is sold only once, whichever way it is sold',
  'Get paid directly: ticket money goes straight to your company’s own account',
  'Run your business in one place: buses, staff, trips, parcels and money',
  'Stand out as trusted: passengers know your company has been checked',
]

function ForCompanies() {
  return (
    <section className="landing-section" id="companies" aria-labelledby="companies-title">
      <div className="company-layout">
        <img src={coachHighway} alt="A coach travelling on an open road" className="company-photo" width={612} height={459} loading="lazy" />
        <div>
          <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">For bus and coach companies</Text>
          <Title order={2} id="companies-title" className="section-title">Grow your business with TransitHub</Title>
          <Text c="dimmed" mt={8}>
            Whether you run one bus or a national fleet, TransitHub gives you the tools to fill more seats and spend
            less time on paperwork.
          </Text>
          <ul className="tick-list">{companyBenefits.map((b) => <li key={b}>{b}</li>)}</ul>
          <Group mt="xl" gap="md">
            <Button radius="xl" color="teal" component={Link} to="/register">Register your company</Button>
            <Button radius="xl" variant="default" component={Link} to="/services">See all services</Button>
          </Group>
          <Text size="xs" c="dimmed" mt="md">Registration is open now. Ticket sales and business tools launch in stages.</Text>
        </div>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/* How to join: pick a step to see what happens in it.                 */
/* ------------------------------------------------------------------ */

type JoinStep = {
  icon: ReactNode
  title: string
  summary: string
  who: string
  time: string
  detailTitle: string
  detail: string[]
  note: string
}

const joinSteps: JoinStep[] = [
  {
    icon: <IconDocument />, title: 'Apply', summary: 'Tell us about your company and share a few documents.',
    who: 'You, with help from our team if you would like it', time: 'Usually one sitting',
    detailTitle: 'What you will need',
    detail: ['Your Certificate of Incorporation from PACRA', 'Your Road Service Licence from RTSA', 'An NRC or passport for each director who will sign', 'Your ZRA tax clearance, if you have one'],
    note: 'A clear photo or scan of each document is fine.',
  },
  {
    icon: <IconShield />, title: 'We check your company', summary: 'Our team confirms your company is registered and licensed.',
    who: 'The TransitHub verification team', time: 'After you apply',
    detailTitle: 'What we check',
    detail: ['Your company is registered and active with PACRA', 'Your Road Service Licence is valid', 'Your documents are genuine and up to date'],
    note: 'If anything is missing, we will tell you exactly what we need.',
  },
  {
    icon: <IconMessage />, title: 'Get your answer', summary: 'We let you know the outcome, with clear reasons.',
    who: 'The TransitHub verification team', time: 'As soon as our checks are complete',
    detailTitle: 'What to expect',
    detail: ['A clear decision on your application', 'The reasons, if we cannot approve it yet', 'The right to appeal within 30 days'],
    note: 'Applications with complete, valid documents move through fastest.',
  },
  {
    icon: <IconKey />, title: 'Start using TransitHub', summary: 'Receive your company code and set up your account.',
    who: 'Your company’s main contact', time: 'Straight after approval',
    detailTitle: 'What you receive',
    detail: ['Your own TransitHub company code', 'An invitation to set up your company account', 'Access to new tools as they launch, including ticket sales'],
    note: 'Welcome aboard.',
  },
]

function Joining() {
  const [active, setActive] = useState(0)
  const step = joinSteps[active]!
  return (
    <section className="landing-section" aria-labelledby="join-title" id="join">
      <SectionHeading eyebrow="Joining is simple" id="join-title" title="Register your bus company in four steps"
        lead="Select a step to see what it involves." />
      <div className="join-layout">
        <ol className="step-track" role="tablist" aria-label="Steps to join" aria-orientation="vertical">
          {joinSteps.map((s, i) => (
            <li key={s.title} className="step-track-item">
              <button type="button" role="tab" id={`join-tab-${i}`} aria-controls="join-panel"
                aria-selected={i === active} tabIndex={i === active ? 0 : -1}
                className={`step-tab ${i === active ? 'is-active' : ''} ${i < active ? 'is-past' : ''}`}
                onMouseEnter={() => setActive(i)} onFocus={() => setActive(i)} onClick={() => setActive(i)}
                onKeyDown={(e) => {
                  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                    e.preventDefault()
                    const next = (i + (e.key === 'ArrowDown' ? 1 : joinSteps.length - 1)) % joinSteps.length
                    document.getElementById(`join-tab-${next}`)?.focus()
                  }
                }}>
                <span className="step-number" aria-hidden>{i < active ? '✓' : i + 1}</span>
                <span className="step-text">
                  <span className="step-title">{s.title}</span>
                  <span className="step-summary">{s.summary}</span>
                </span>
              </button>
            </li>
          ))}
        </ol>

        <Paper className="step-panel" radius="xl" p="xl" role="tabpanel" id="join-panel" aria-labelledby={`join-tab-${active}`} key={active}>
          <div className="step-panel-head">
            <span className="trust-icon" aria-hidden>{step.icon}</span>
            <div>
              <Text size="xs" fw={700} tt="uppercase" lts={0.8} c="var(--th-accent-text)">Step {active + 1} of {joinSteps.length}</Text>
              <Text fw={700} size="xl" mt={2}>{step.title}</Text>
            </div>
          </div>
          <div className="step-facts">
            <div><Text size="xs" c="dimmed">Who</Text><Text size="sm" fw={600}>{step.who}</Text></div>
            <div><Text size="xs" c="dimmed">When</Text><Text size="sm" fw={600}>{step.time}</Text></div>
          </div>
          <Text fw={700} mt="lg">{step.detailTitle}</Text>
          <ul className="tick-list">{step.detail.map((d) => <li key={d}>{d}</li>)}</ul>
          <Text size="sm" c="dimmed" mt="md">{step.note}</Text>
          <div className="step-panel-cta">
            <Button radius="xl" color="teal" component={Link} to="/register">Start your registration <span className="button-arrow">→</span></Button>
            <Button radius="xl" variant="subtle" color="teal" component={Link} to="/contact">Ask us a question</Button>
          </div>
        </Paper>
      </div>
    </section>
  )
}

/** Three promises, each with its own symbol, so the section reads at a glance. */
function Trust() {
  const promises = [
    { icon: <IconShield />, title: 'Checked companies',
      text: 'We check every bus company before it can sell a single ticket, so you always know who you are travelling with.' },
    { icon: <IconLock />, title: 'Your information is safe',
      text: 'Your personal details are protected in line with Zambia’s Data Protection Act, and used only to serve you.' },
    { icon: <IconScale />, title: 'Fair and open',
      text: 'Every decision we make comes with clear reasons, and bus companies always have the right to appeal.' },
  ]
  return (
    <section className="landing-section" aria-labelledby="trust-title">
      <div className="section-heading section-heading-center">
        <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">Our promise</Text>
        <Title order={2} id="trust-title" className="section-title">Built on trust</Title>
        <Text c="dimmed" mt={8}>Whether you are travelling or running a bus company, this is what you can count on.</Text>
      </div>
      <div className="trust-grid">
        {promises.map((p) => (
          <article key={p.title} className="trust-card">
            <span className="trust-icon" aria-hidden>{p.icon}</span>
            <Text fw={700} size="lg" mt="lg">{p.title}</Text>
            <Text size="sm" c="dimmed" mt={8} lh={1.65}>{p.text}</Text>
          </article>
        ))}
      </div>
    </section>
  )
}

/** The final call to action for bus companies, with reassurance and a direct line. */
function ClosingBand() {
  const points = ['Registration is open now', 'Our team helps you apply', 'Be ready when online booking launches']
  return (
    <section className="cta-band" aria-labelledby="closing-title">
      <RouteArt />
      <div className="cta-content">
        <Text size="xs" fw={700} tt="uppercase" lts={1} className="cta-eyebrow">For bus and coach companies</Text>
        <Title order={2} id="closing-title" className="cta-title">Ready to grow with TransitHub?</Title>
        <Text className="cta-lead">
          Join the bus companies getting ready for online booking in Zambia. Registering is simple, and our team is here
          to help you every step of the way.
        </Text>
        <ul className="cta-points">{points.map((point) => <li key={point}>{point}</li>)}</ul>
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
  )
}

/* ------------------------------------------------------------------ */

function SectionHeading({ eyebrow, title, lead, id }: { eyebrow: string; title: string; lead: string; id: string }) {
  return (
    <div className="section-heading">
      <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">{eyebrow}</Text>
      <Title order={2} id={id} className="section-title">{title}</Title>
      <Text c="dimmed" mt={8} maw={640}>{lead}</Text>
    </div>
  )
}

const svg = { width: 20, height: 20, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const }
export function IconBuilding(): ReactNode { return <svg {...svg}><path d="M4 21V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v16M15 9h4a1 1 0 0 1 1 1v11M3 21h18M8 8h3M8 12h3M8 16h3" /></svg> }
export function IconRoute(): ReactNode { return <svg {...svg}><circle cx="6" cy="19" r="2" /><circle cx="18" cy="5" r="2" /><path d="M8 19h8.5a3.5 3.5 0 0 0 0-7h-9a3.5 3.5 0 0 1 0-7H16" /></svg> }
export function IconTicket(): ReactNode { return <svg {...svg}><path d="M3 8a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4z" /><path d="M13 6v12" strokeDasharray="2 2" /></svg> }
export function IconSearch(): ReactNode { return <svg {...svg}><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg> }
export function IconSeat(): ReactNode { return <svg {...svg}><path d="M7 4a2 2 0 0 1 2 2v7h7l2 7M5 13h4M10 13v7M6 20h8" /></svg> }
export function IconWallet(): ReactNode { return <svg {...svg}><path d="M4 7a2 2 0 0 1 2-2h11v2" /><rect x="3" y="7" width="18" height="13" rx="2" /><path d="M16 13.5h2" /></svg> }
export function IconChart(): ReactNode { return <svg {...svg}><path d="M4 20V10M10 20V4M16 20v-7M22 20H2" /></svg> }
export function IconShield(): ReactNode { return <svg {...svg}><path d="M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6z" /><path d="M9 12l2 2 4-4" /></svg> }
export function IconLock(): ReactNode { return <svg {...svg}><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4M12 15v2" /></svg> }
export function IconScale(): ReactNode { return <svg {...svg}><path d="M12 3v18M7 21h10M5 7h14M5 7l-3 7a3 3 0 0 0 6 0zM19 7l-3 7a3 3 0 0 0 6 0z" /></svg> }
export function IconDocument(): ReactNode { return <svg {...svg}><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5M9 13h6M9 17h4" /></svg> }
export function IconMessage(): ReactNode { return <svg {...svg}><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z" /><path d="M8.5 12h7M8.5 9h5" /></svg> }
export function IconKey(): ReactNode { return <svg {...svg}><circle cx="8" cy="15" r="4" /><path d="M10.8 12.2L20 3M16 7l3 3M14 9l2 2" /></svg> }
