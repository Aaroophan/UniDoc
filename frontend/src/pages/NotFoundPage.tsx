import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { roleHome } from '../auth/routing'
import { Brand } from '../components/Brand'

export function NotFoundPage() {
  const { user } = useAuth()
  return (
    <main className="notfound-shell">
      <Brand subtitle />
      <section className="notfound-content">
        <span className="notfound-number" aria-hidden="true">404</span>
        <p className="section-eyebrow">Page not found</p>
        <h1>We couldn’t find that page.</h1>
        <p className="text-muted mb-4">The link may be incorrect, or this page may no longer exist.</p>
        <Link to={user ? roleHome(user.role) : '/login'} className="btn btn-primary">{user ? 'Return to workspace' : 'Go to sign in'}</Link>
      </section>
    </main>
  )
}
