import { Outlet } from 'react-router-dom'
import { Brand } from '../components/Brand'

export function PublicLayout() {
  return (
    <main className="auth-shell">
      <section className="auth-aside d-flex flex-column justify-content-between" aria-label="About UniDoc">
        <Brand subtitle />
        <div className="auth-aside-content">
          <span className="hero-kicker">YOUR DOCUMENTS, SIMPLIFIED</span>
          <h2>A better way to request what matters.</h2>
          <p className="auth-aside-lead">One place to request university documents, follow their progress, and know when they are ready.</p>
          <div className="hero-rule" aria-hidden="true" />
          <div className="hero-steps">
            <div><span>01</span><p>Choose your document</p></div>
            <div><span>02</span><p>Send your request</p></div>
            <div><span>03</span><p>Stay informed</p></div>
          </div>
        </div>
        <p className="auth-aside-footer">UniDoc · University Document Request Management System</p>
      </section>
      <section className="auth-form-pane">
        <div className="auth-mobile-brand"><Brand subtitle /></div>
        <div className="auth-form-wrap"><Outlet /></div>
        <p className="auth-form-footer">Secure access to the UniDoc request portal.</p>
      </section>
    </main>
  )
}
