/**
 * The TransitHub mark: a teal hub where three corridors meet, forming a T
 * ("One connected platform. Every transport operation."). Paths come from
 * the brand identity board; keep them exactly as drawn.
 */

const HUB = `M 30.8 11.2 Q 32 10 33.2 11.2 L 41.8 19.8 Q 43 21 41.8 22.2
  L 33.2 30.8 Q 32 32 30.8 30.8 L 22.2 22.2 Q 21 21 22.2 19.8 Z
  M 32.6 17.4 Q 32 16.8 31.4 17.4 L 28.4 20.4 Q 27.8 21 28.4 21.6
  L 31.4 24.6 Q 32 25.2 32.6 24.6 L 35.6 21.6 Q 36.2 21 35.6 20.4 Z`

const CORRIDORS = [
  'M 6 18 Q 6 16 8 16 L 20 16 Q 21 16 20.3 16.7 L 16.7 20.3 Q 16 21 16.7 21.7 L 20.3 25.3 Q 21 26 20 26 L 8 26 Q 6 26 6 24 Z',
  'M 58 24 Q 58 26 56 26 L 44 26 Q 43 26 43.7 25.3 L 47.3 21.7 Q 48 21 47.3 20.3 L 43.7 16.7 Q 43 16 44 16 L 56 16 Q 58 16 58 18 Z',
  'M 27 33 Q 27 32 27.7 32.7 L 31.3 36.3 Q 32 37 32.7 36.3 L 36.3 32.7 Q 37 32 37 33 L 37 52 Q 37 54 35 54 L 29 54 Q 27 54 27 52 Z',
]

export const brand = {
  navy: '#101C35',
  teal: '#12B8A6',
  sky: '#3997F5',
  amber: '#F2A93B',
  cloud: '#F5F8FC',
} as const

type Tone = 'onLight' | 'onDark' | 'mono'

/** The symbol alone; `tone` picks corridor colours for light or navy backgrounds. */
export function LogoMark({ size = 32, tone = 'onLight', title }: { size?: number; tone?: Tone; title?: string }) {
  // On light surfaces the corridors follow the colour scheme, so the mark stays whole in dark mode.
  const corridor = tone === 'onDark' ? brand.cloud : tone === 'mono' ? 'currentColor' : `var(--logo-corridor, ${brand.navy})`
  const hub = tone === 'mono' ? 'currentColor' : brand.teal
  return (
    <svg viewBox="0 6 64 52" width={size * (64 / 52)} height={size} role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true} aria-label={title}>
      <path d={HUB} fill={hub} fillRule="evenodd" />
      {CORRIDORS.map((d) => <path key={d} d={d} fill={corridor} />)}
    </svg>
  )
}

/** Primary horizontal lockup: symbol followed by the wordmark. */
export function Logo({ tone = 'onLight', size = 30 }: { tone?: Tone; size?: number }) {
  return (
    <span className={`th-logo th-logo-${tone}`} style={{ fontSize: size * 0.82 }}>
      <LogoMark size={size} tone={tone} />
      <span className="th-wordmark">TransitHub</span>
    </span>
  )
}
