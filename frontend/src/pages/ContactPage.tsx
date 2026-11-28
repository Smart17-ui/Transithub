import { useState, type FormEvent } from 'react'
import { Alert, Button, Checkbox, Paper, Select, SimpleGrid, Text, TextInput, Textarea, Title } from '@mantine/core'
import { useMutation } from '@tanstack/react-query'
import { api } from '../api/client'
import type { EnquiryTopic, NewEnquiry } from '../api/types'
import { company } from '../brand/company'
import { ApiError } from '../components/common'
import { PublicPage } from '../components/PublicPage'
import { IconBuilding, IconShield, IconTicket } from './LandingPage'

const topics: Array<{ value: EnquiryTopic; label: string }> = [
  { value: 'COMPANY_REGISTRATION', label: 'I run a bus company and want to join' },
  { value: 'TRAVEL', label: 'I have a question about travelling' },
  { value: 'PARTNERSHIP', label: 'Partnerships or media' },
  { value: 'OTHER', label: 'Something else' },
]

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE = /^\+?[0-9 -]{7,20}$/

const empty: NewEnquiry = {
  topic: 'COMPANY_REGISTRATION', fullName: '', email: '', phone: '', companyName: '', message: '',
  consentGiven: false, website: '',
}

export function ContactPage() {
  return (
    <PublicPage eyebrow="Contact" title="We’d love to hear from you"
      lead="Whether you run a bus company, are planning a journey or would like to work with us, send us a message and our team will get back to you."
      aside={<QuickContact />}>
      <section className="landing-section" id="message" aria-label="Contact TransitHub">
        <div className="contact-layout">
          <ContactForm />
          <ContactAside />
        </div>
      </section>
    </PublicPage>
  )
}

function ContactForm() {
  const [form, setForm] = useState<NewEnquiry>(empty)
  const [touched, setTouched] = useState(false)
  const set = <K extends keyof NewEnquiry>(key: K, value: NewEnquiry[K]) => setForm((f) => ({ ...f, [key]: value }))
  const send = useMutation({
    mutationFn: () => api.submitEnquiry({
      ...form, fullName: form.fullName.trim(), email: form.email.trim(), message: form.message.trim(),
      phone: form.phone?.trim() || null, companyName: form.companyName?.trim() || null,
    }),
  })

  const errors = {
    fullName: !form.fullName.trim() ? 'Please enter your name' : null,
    email: !form.email.trim() ? 'Please enter your email address'
      : !EMAIL.test(form.email.trim()) ? 'Please enter a valid email address' : null,
    phone: form.phone?.trim() && !PHONE.test(form.phone.trim()) ? 'Please enter a valid phone number, for example +260 977 123 456' : null,
    message: form.message.trim().length < 10 ? 'Please tell us a little more (at least 10 characters)' : null,
    consent: !form.consentGiven ? 'Please confirm so that we can reply to you' : null,
  }
  const valid = Object.values(errors).every((e) => !e)
  const show = (error: string | null) => (touched ? error : null)

  function submit(event: FormEvent) {
    event.preventDefault()
    setTouched(true)
    if (valid) send.mutate()
  }

  if (send.isSuccess) {
    const firstName = form.fullName.trim().split(/\s+/)[0]
    return (
      <Paper className="service-card contact-form" radius="xl" p="xl">
        <span className="success-mark" aria-hidden>✓</span>
        <Title order={2} className="contact-success-title">Thank you, {firstName}.</Title>
        <Text c="dimmed" mt="sm">
          Your message has been received. We will reply to <b>{form.email.trim()}</b> as soon as possible.
        </Text>
        {send.data.reference && (
          <Text mt="md">Your reference number is <b>{send.data.reference}</b>. Please quote it if you contact us again.</Text>
        )}
        <Button variant="default" radius="xl" mt="xl" onClick={() => { setForm(empty); setTouched(false); send.reset() }}>
          Send another message
        </Button>
      </Paper>
    )
  }

  const asksForCompany = form.topic === 'COMPANY_REGISTRATION' || form.topic === 'PARTNERSHIP'
  return (
    <Paper component="form" className="service-card contact-form" radius="xl" p="xl" onSubmit={submit} noValidate>
      <Title order={2} className="contact-form-title">Send us a message</Title>
      <Text size="sm" c="dimmed" mt={4}>Fields marked with * are required.</Text>

      <Select label="How can we help?" mt="lg" data={topics} value={form.topic} allowDeselect={false}
        onChange={(v) => set('topic', (v ?? 'OTHER') as EnquiryTopic)} required />
      <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="sm" mt="sm">
        <TextInput label="Full name" autoComplete="name" required value={form.fullName} error={show(errors.fullName)}
          onChange={(e) => set('fullName', e.currentTarget.value)} maxLength={200} />
        <TextInput label="Email address" type="email" autoComplete="email" required value={form.email} error={show(errors.email)}
          onChange={(e) => set('email', e.currentTarget.value)} maxLength={254} />
        <TextInput label="Phone number" type="tel" autoComplete="tel" placeholder="Optional" value={form.phone ?? ''}
          error={show(errors.phone)} onChange={(e) => set('phone', e.currentTarget.value)} />
        {asksForCompany && (
          <TextInput label="Company name" autoComplete="organization" placeholder="Optional" value={form.companyName ?? ''}
            onChange={(e) => set('companyName', e.currentTarget.value)} maxLength={200} />
        )}
      </SimpleGrid>
      <Textarea label="Your message" required mt="sm" autosize minRows={5} maxLength={4000}
        placeholder={form.topic === 'COMPANY_REGISTRATION'
          ? 'Tell us about your company, for example your routes and the number of buses you run.'
          : 'How can we help you?'}
        value={form.message} error={show(errors.message)} onChange={(e) => set('message', e.currentTarget.value)} />

      {/* Hidden from people; automated spam fills it in. */}
      <div className="hp-field" aria-hidden="true">
        <label>Leave this field empty<input tabIndex={-1} autoComplete="off" value={form.website}
          onChange={(e) => set('website', e.currentTarget.value)} /></label>
      </div>

      <Checkbox mt="md" checked={form.consentGiven} error={show(errors.consent)}
        onChange={(e) => set('consentGiven', e.currentTarget.checked)}
        label="I agree that TransitHub may use these details to reply to my message, in line with the Data Protection Act, 2021." />

      {send.error && <ApiError error={send.error} />}
      <Button type="submit" size="md" radius="xl" color="teal" mt="lg" loading={send.isPending}>
        Send message
      </Button>
    </Paper>
  )
}

const channels = [
  { icon: <IconBuilding />, title: 'Bus and coach companies', text: 'Questions about joining TransitHub or about your application.' },
  { icon: <IconTicket />, title: 'Passengers', text: 'Questions about journeys, tickets and parcels.' },
  { icon: <IconShield />, title: 'Partners and media', text: 'Partnership, media and other business enquiries.' },
]

function ContactAside() {
  const hasDetails = company.phone || company.email || company.address
  return (
    <div className="contact-channels">
      {channels.map((c) => (
        <Paper key={c.title} className="service-card" radius="lg" p="lg">
          <div className="contact-channel">
            <span className="module-icon" aria-hidden>{c.icon}</span>
            <div>
              <Text fw={700}>{c.title}</Text>
              <Text size="sm" c="dimmed" mt={4}>{c.text}</Text>
            </div>
          </div>
        </Paper>
      ))}
      {!hasDetails && (
        <Alert variant="light" color="sky" title="Prefer to call or visit?">
          Our phone number and office address will be shown here soon. In the meantime, the form is the quickest way to reach us.
        </Alert>
      )}
    </div>
  )
}

/** The fastest ways to reach the team, shown in the page header. */
function QuickContact() {
  return (
    <div className="quick-contact">
      {company.phone && (
        <a className="quick-contact-item" href={`tel:${company.phone.replace(/\s/g, '')}`}>
          <span className="quick-contact-icon" aria-hidden><IconPhone /></span>
          <span><span className="quick-contact-label">Call us</span><span className="quick-contact-value">{company.phone}</span></span>
        </a>
      )}
      {company.email && (
        <a className="quick-contact-item" href={`mailto:${company.email}`}>
          <span className="quick-contact-icon" aria-hidden><IconMail /></span>
          <span><span className="quick-contact-label">Email us</span><span className="quick-contact-value">{company.email}</span></span>
        </a>
      )}
      <a className="quick-contact-item" href="#message">
        <span className="quick-contact-icon" aria-hidden><IconSend /></span>
        <span><span className="quick-contact-label">Send a message</span><span className="quick-contact-value">Use the form below</span></span>
      </a>
    </div>
  )
}

const icon = { width: 20, height: 20, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const }
function IconPhone() { return <svg {...icon}><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z" /></svg> }
function IconMail() { return <svg {...icon}><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 7l9 6 9-6" /></svg> }
function IconSend() { return <svg {...icon}><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z" /></svg> }
