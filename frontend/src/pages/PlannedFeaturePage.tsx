import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { roleHome } from '../auth/routing'
import { PageHeader } from '../components/PageHeader'

export interface PlannedFeaturePageProps {
  eyebrow: string
  title: string
  description: string
  issue: number
}

/** Deliberately honest placeholders: features are implemented in later issues. */
export function PlannedFeaturePage({ eyebrow, title, description, issue }: PlannedFeaturePageProps) {
  const { user } = useAuth()
  return (
    <>
      <PageHeader eyebrow={eyebrow} title={title} description={description} />
      <section className="planned-card" aria-labelledby="planned-title">
        <div className="planned-decoration" aria-hidden="true"><span>UD</span></div>
        <div className="planned-copy">
          <p className="section-eyebrow mb-2">Workspace ready</p>
          <h2 id="planned-title">This area is being built.</h2>
          <p>The secure navigation and session handling are ready. This feature will be connected to its backend and completed in implementation Issue {issue}.</p>
          <div className="planned-actions">
            <Link to="/account" className="btn btn-primary">View my account</Link>
            {user && <Link to={roleHome(user.role)} className="btn btn-outline-secondary">Workspace home</Link>}
          </div>
        </div>
      </section>
    </>
  )
}
