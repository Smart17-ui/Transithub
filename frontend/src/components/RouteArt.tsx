/** A faint bus route with stops: TransitHub's decorative motif. Purely visual. */
export function RouteArt({ className = 'cta-route' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 600 240" preserveAspectRatio="xMaxYMid slice" aria-hidden focusable="false">
      <path d="M-20 200 C 120 200, 140 70, 280 90 S 460 210, 620 40" fill="none" stroke="currentColor" strokeWidth="3"
        strokeDasharray="10 12" strokeLinecap="round" />
      {[[60, 196], [280, 90], [470, 168], [580, 64]].map(([cx, cy]) => (
        <g key={`${cx}-${cy}`}>
          <circle cx={cx} cy={cy} r="14" fill="currentColor" opacity="0.18" />
          <circle cx={cx} cy={cy} r="6" fill="currentColor" />
        </g>
      ))}
    </svg>
  )
}
