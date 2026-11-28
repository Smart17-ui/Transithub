import { useEffect, type ReactNode } from 'react'
import { Text, Title } from '@mantine/core'
import { useLocation } from 'react-router'
import { RouteArt } from './RouteArt'
import { SiteFooter } from './SiteFooter'

/**
 * Layout for the public pages (Services, About, Contact): a page header in
 * the brand navy, the content, and the site footer.
 */
export function PublicPage({ eyebrow, title, lead, aside, children }: {
  eyebrow: string; title: string; lead: string; aside?: ReactNode; children: ReactNode
}) {
  return (
    <div className="landing public-page">
      <header className={`page-hero ${aside ? 'has-aside' : ''}`}>
        <RouteArt className="page-hero-route" />
        <div className="page-hero-copy">
          <Text size="xs" fw={700} tt="uppercase" lts={1} className="page-hero-eyebrow">{eyebrow}</Text>
          <Title order={1} className="page-hero-title">{title}</Title>
          <Text className="page-hero-lead">{lead}</Text>
        </div>
        {aside && <div className="page-hero-aside">{aside}</div>}
      </header>
      {children}
      <SiteFooter />
    </div>
  )
}

/**
 * Moving to another page starts at the top, or at the section named in the
 * address (for example /services#travellers), as visitors expect.
 */
export function ScrollToLocation() {
  const { pathname, hash } = useLocation()
  useEffect(() => {
    if (hash) {
      const target = document.getElementById(decodeURIComponent(hash.slice(1)))
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' })
        return
      }
    }
    window.scrollTo({ top: 0 })
  }, [pathname, hash])
  return null
}
