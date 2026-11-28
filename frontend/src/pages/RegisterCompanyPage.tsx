import { useState, type FormEvent } from 'react'
import { Anchor, Button, Checkbox, Paper, Select, SimpleGrid, Text, TextInput, Textarea, Title } from '@mantine/core'
import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../api/client'
import { userManager } from '../auth/oidc'
import { company } from '../brand/company'
import { ApiError } from '../components/common'
import { PublicPage } from '../components/PublicPage'

/**
 * "Register your company": a bus company asks to join. Until self-service
 * registration exists, the request reaches the team's Enquiries inbox
 * (topic COMPANY_REGISTRATION) and an Onboarding Officer completes the
 * registration with the company.
 */
type Form = {
  companyName: string
  contactName: string
  phone: string
  email: string
  location: string
  fleetSize: string
  message: string
  consent: boolean
  website: string
}

const empty: Form = { companyName: '', contactName: '', phone: '', email: '', location: '', fleetSize: '', message: '', consent: false, website: '' }
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE = /^\+?[0-9 -]{7,20}$/
const fleetSizes = ['1–5 buses', '6–20 buses', '21–50 buses', 'More than 50 buses']

export function RegisterCompanyPage() {
  return (
    <PublicPage eyebrow="Register your company" title="Bring your bus company to TransitHub"
      lead="Create your account to apply online, or ask our team to help you register.">
      <section className="landing-section" aria-label="Registration request">
        <ApplyOnline />
        <div className="register-layout">
          <RegisterForm />
          <aside className="register-aside">
            <Paper className="service-card" radius="lg" p="lg">
              <Text fw={700}>What happens next</Text>
              <ol className="register-next">
                <li>Our team contacts you, usually by phone.</li>
                <li>We complete your registration together and collect your documents.</li>
                <li>We check your company and let you know the outcome.</li>
                <li>Once approved, you receive your TransitHub company code.</li>
              </ol>
            </Paper>
            <Paper className="service-card" radius="lg" p="lg">
              <Text fw={700}>Have these ready</Text>
              <ul className="tick-list">
                <li>Certificate of Incorporation (PACRA)</li>
                <li>Road Service Licence (RTSA)</li>
                <li>NRC or passport for each director who will sign</li>
              </ul>
            </Paper>
          </aside>
        </div>
      </section>
    </PublicPage>
  )
}

/**
 * Self-service sign-up (Release 1 §9, SS-13): opens the sign-in service's
 * "Create your account" page directly, with what the account is for.
 */
function ApplyOnline() {
  const [error, setError] = useState('')
  const start = () => {
    setError('')
    userManager.signinRedirect({ prompt: 'create', state: { returnTo: '/' } })
      .catch(() => setError('Our sign-in service isn’t responding right now. Please try again in a moment.'))
  }
  return (
    <Paper className="service-card apply-online" radius="lg" p="xl" mb="lg">
      <div>
        <Text size="xs" fw={700} tt="uppercase" lts={1} c="var(--th-accent-text)">Apply online</Text>
        <Title order={2} className="apply-online-title">Create your account to get started</Title>
        <Text c="dimmed" mt={6} maw={620}>
          The account is for you, the owner, director or manager applying for the company. Use your own email address.
          Once your company is approved, the same account manages your company on TransitHub.
        </Text>
        <Text size="sm" c="dimmed" mt="sm">
          Travelling? You don’t need an account to book. TransitHub or bus company staff? Your administrator sets up your account.
        </Text>
        {error && <Text size="sm" c="red" mt="sm" role="alert">{error}</Text>}
      </div>
      <Button size="md" radius="xl" color="teal" onClick={start}>Create your account <span className="button-arrow">→</span></Button>
    </Paper>
  )
}

function RegisterForm() {
  const [form, setForm] = useState<Form>(empty)
  const [touched, setTouched] = useState(false)
  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((f) => ({ ...f, [key]: value }))

  const send = useMutation({
    mutationFn: () => api.submitEnquiry({
      topic: 'COMPANY_REGISTRATION',
      fullName: form.contactName.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      companyName: form.companyName.trim(),
      message: [
        'Registration request from the website.',
        `Company: ${form.companyName.trim()}`,
        form.location.trim() && `Based in: ${form.location.trim()}`,
        form.fleetSize && `Fleet size: ${form.fleetSize}`,
        form.message.trim() && `Message: ${form.message.trim()}`,
      ].filter(Boolean).join('\n'),
      consentGiven: form.consent,
      website: form.website,
    }),
  })

  const errors = {
    companyName: !form.companyName.trim() ? 'Please enter your company name' : null,
    contactName: !form.contactName.trim() ? 'Please enter your name' : null,
    phone: !form.phone.trim() ? 'Please enter a phone number we can call'
      : !PHONE.test(form.phone.trim()) ? 'Please enter a valid phone number, for example +260 977 123 456' : null,
    email: !form.email.trim() ? 'Please enter your email address'
      : !EMAIL.test(form.email.trim()) ? 'Please enter a valid email address' : null,
    consent: !form.consent ? 'Please confirm so that we can contact you' : null,
  }
  const valid = Object.values(errors).every((e) => !e)
  const show = (error: string | null) => (touched ? error : null)

  function submit(event: FormEvent) {
    event.preventDefault()
    setTouched(true)
    if (valid) send.mutate()
  }

  if (send.isSuccess) {
    return (
      <Paper className="service-card contact-form" radius="xl" p="xl">
        <span className="success-mark" aria-hidden>✓</span>
        <Title order={2} className="contact-success-title">Thank you, we have received your request.</Title>
        <Text c="dimmed" mt="sm">
          Our team will contact <b>{form.contactName.trim()}</b> on <b>{form.phone.trim()}</b> to start the registration of{' '}
          <b>{form.companyName.trim()}</b>.
        </Text>
        {send.data.reference && <Text mt="md">Your reference number is <b>{send.data.reference}</b>.</Text>}
        <Button component={Link} to="/" variant="default" radius="xl" mt="xl">Back to the home page</Button>
      </Paper>
    )
  }

  return (
    <Paper component="form" className="service-card contact-form" radius="xl" p="xl" onSubmit={submit} noValidate>
      <Title order={2} className="contact-form-title">Or ask our team to help you</Title>
      <Text size="sm" c="dimmed" mt={4}>It takes about two minutes. Fields marked with * are required.</Text>

      <TextInput label="Company name" required mt="lg" autoComplete="organization" placeholder="As registered with PACRA"
        value={form.companyName} error={show(errors.companyName)} onChange={(e) => set('companyName', e.currentTarget.value)} maxLength={200} />
      <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm" mt="sm">
        <TextInput label="Your name" required autoComplete="name" value={form.contactName} error={show(errors.contactName)}
          onChange={(e) => set('contactName', e.currentTarget.value)} maxLength={200} />
        <TextInput label="Phone number" required type="tel" autoComplete="tel" placeholder="+260 …" value={form.phone}
          error={show(errors.phone)} onChange={(e) => set('phone', e.currentTarget.value)} />
        <TextInput label="Email address" required type="email" autoComplete="email" value={form.email}
          error={show(errors.email)} onChange={(e) => set('email', e.currentTarget.value)} maxLength={254} />
        <TextInput label="Town or district" placeholder="e.g. Lusaka" value={form.location}
          onChange={(e) => set('location', e.currentTarget.value)} maxLength={100} />
      </SimpleGrid>
      <Select label="How many buses do you run?" mt="sm" placeholder="Choose one (optional)" data={fleetSizes}
        value={form.fleetSize || null} onChange={(v) => set('fleetSize', v ?? '')} clearable />
      <Textarea label="Anything else we should know?" mt="sm" placeholder="Optional, for example your main routes"
        autosize minRows={3} maxLength={2000} value={form.message} onChange={(e) => set('message', e.currentTarget.value)} />

      <div className="hp-field" aria-hidden="true">
        <label>Leave this field empty<input tabIndex={-1} autoComplete="off" value={form.website}
          onChange={(e) => set('website', e.currentTarget.value)} /></label>
      </div>

      <Checkbox mt="md" checked={form.consent} error={show(errors.consent)} onChange={(e) => set('consent', e.currentTarget.checked)}
        label="I agree that TransitHub may use these details to contact me about registering my company, in line with the Data Protection Act, 2021." />

      {send.error && <ApiError error={send.error} />}
      <Button type="submit" size="md" radius="xl" color="teal" mt="lg" loading={send.isPending}>
        Send registration request <span className="button-arrow">→</span>
      </Button>
      {company.phone && (
        <Text size="sm" c="dimmed" mt="md">
          Prefer to talk first? Call us on <Anchor href={`tel:${company.phone.replace(/\s/g, '')}`}>{company.phone}</Anchor>.
        </Text>
      )}
    </Paper>
  )
}
