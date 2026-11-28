import { Text } from '@mantine/core'
import { company } from '../brand/company'
import { Logo } from '../brand/Logo'

type Link = { label: string; href: string }

/** Only links that lead somewhere real are shown; business details appear once filled in (brand/company.ts). */
const columns: Array<{ title: string; links: Link[] }> = [
  {
    title: 'Company',
    links: [
      { label: 'About us', href: '/about' },
      { label: 'Services', href: '/services' },
      { label: 'Contact', href: '/contact' },
    ],
  },
  {
    title: 'Bus companies',
    links: [
      { label: 'Register your company', href: '/register' },
      { label: 'What you will need', href: '/#join' },
      { label: 'Services for companies', href: '/services#operators' },
    ],
  },
  {
    title: 'Passengers',
    links: [
      { label: 'Travelling with TransitHub', href: '/#passengers' },
      { label: 'Services for passengers', href: '/services#travellers' },
      ...(company.email ? [{ label: 'Customer support', href: `mailto:${company.email}` }] : []),
    ],
  },
]

const legalLinks: Link[] = [
  ...(company.privacyNoticeUrl ? [{ label: 'Privacy notice', href: company.privacyNoticeUrl }] : []),
  ...(company.termsUrl ? [{ label: 'Terms of use', href: company.termsUrl }] : []),
]

const socialLabels: Record<keyof typeof company.social, string> = {
  facebook: 'Facebook', linkedin: 'LinkedIn', x: 'X', whatsapp: 'WhatsApp',
}

export function SiteFooter() {
  const year = new Date().getFullYear()
  const owner = company.legalName || 'TransitHub'
  const socials = (Object.entries(company.social) as Array<[keyof typeof company.social, string]>).filter(([, url]) => url)

  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <div className="footer-top">
          <div className="footer-brand">
            <Logo tone="onDark" size={28} />
            <Text size="sm" className="footer-muted" mt="md" maw={340} lh={1.65}>
              Bus travel across Zambia, made simple for passengers and the companies that carry them.
            </Text>
            <address className="footer-contact">
              {company.phone && (
                <a className="footer-contact-item" href={`tel:${company.phone.replace(/\s/g, '')}`}>
                  <span className="footer-contact-icon" aria-hidden><IconPhone /></span>{company.phone}
                </a>
              )}
              {company.email && (
                <a className="footer-contact-item" href={`mailto:${company.email}`}>
                  <span className="footer-contact-icon" aria-hidden><IconMail /></span>{company.email}
                </a>
              )}
              {company.address && (
                <span className="footer-contact-item">
                  <span className="footer-contact-icon" aria-hidden><IconPin /></span>{company.address}
                </span>
              )}
              {company.supportHours && <span className="footer-muted footer-hours">Support: {company.supportHours}</span>}
            </address>
            <span className="footer-badge"><span className="footer-badge-dot" aria-hidden />Made for Zambia</span>
          </div>

          {columns.map((column) => (
            <nav key={column.title} className="footer-column" aria-label={column.title}>
              <span className="footer-heading">{column.title}</span>
              {column.links.map((link) => <a key={link.label} href={link.href}>{link.label}</a>)}
            </nav>
          ))}
        </div>

        <div className="footer-bottom">
          <div className="footer-legal">
            <span>
              © {year} {owner}.{company.registrationNumber && <> Registered in Zambia, PACRA no. {company.registrationNumber}.</>}
            </span>
            <span className="footer-legal-note">Personal data is handled in line with the Data Protection Act, 2021.</span>
          </div>
          <div className="footer-bottom-links">
            {legalLinks.map((link) => <a key={link.label} href={link.href}>{link.label}</a>)}
            {socials.map(([key, url]) => <a key={key} href={url} target="_blank" rel="noreferrer">{socialLabels[key]}</a>)}
            <button type="button" className="footer-top-button" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
              Back to top <span aria-hidden>↑</span>
            </button>
          </div>
        </div>
      </div>
    </footer>
  )
}

const icon = { width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const }
function IconPhone() { return <svg {...icon}><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z" /></svg> }
function IconMail() { return <svg {...icon}><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 7l9 6 9-6" /></svg> }
function IconPin() { return <svg {...icon}><path d="M12 21s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z" /><circle cx="12" cy="9" r="2.5" /></svg> }
