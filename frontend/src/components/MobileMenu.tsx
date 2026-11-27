import { Burger, Button, Divider, Drawer, Group, Stack, Text } from '@mantine/core'
import { useDisclosure } from '@mantine/hooks'
import { Link, NavLink } from 'react-router'
import { Logo } from '../brand/Logo'
import { ColorSchemeToggle } from './ColorSchemeToggle'

export const siteLinks = [
  { to: '/', label: 'Home', end: true },
  { to: '/services', label: 'Services' },
  { to: '/about', label: 'About' },
  { to: '/contact', label: 'Contact' },
]

/**
 * The site menu on phones and tablets: a menu button that opens a panel with
 * the pages, Sign in and the light/dark switch. Choosing a page closes it.
 */
export function MobileMenu() {
  const [opened, { toggle, close }] = useDisclosure(false)
  return (
    <>
      <Burger opened={opened} onClick={toggle} color="white" size="sm" className="mobile-menu-trigger"
        aria-label={opened ? 'Close menu' : 'Open menu'} aria-expanded={opened} aria-controls="mobile-menu" />
      <Drawer opened={opened} onClose={close} position="right" size="80%" id="mobile-menu"
        title={<Logo size={24} />} classNames={{ content: 'mobile-menu', header: 'mobile-menu-header' }}
        aria-label="Site menu">
        <nav aria-label="Main">
          <Stack gap={4}>
            {siteLinks.map((link) => (
              <NavLink key={link.to} to={link.to} end={link.end} onClick={close} className="mobile-menu-link">
                {link.label}
              </NavLink>
            ))}
          </Stack>
        </nav>
        <Divider my="lg" />
        <Button fullWidth radius="xl" color="teal" size="md" component={Link} to="/sign-in" onClick={close}>Sign in</Button>
        <Button fullWidth radius="xl" variant="default" size="md" mt="sm" component={Link} to="/register" onClick={close}>
          Register your company
        </Button>
        <Group justify="space-between" mt="xl" className="mobile-menu-theme">
          <Text size="sm" c="dimmed">Appearance</Text>
          <ColorSchemeToggle />
        </Group>
      </Drawer>
    </>
  )
}
