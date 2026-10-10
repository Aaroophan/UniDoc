import { Link } from 'react-router-dom'

export function Brand({ subtitle = false, compact = false }: { subtitle?: boolean; compact?: boolean }) {
  return (
    <Link to="/" className={`brand text-decoration-none ${compact ? 'brand-compact' : ''}`} aria-label="UniDoc home">
      <span className="brand-mark" aria-hidden="true">UD<span className="brand-mark-dot">.</span></span>
      <span className="brand-name-block">
        <span className="brand-name">UniDoc</span>
        {subtitle && <span className="brand-subtitle">University Document Request Management System</span>}
      </span>
    </Link>
  )
}
