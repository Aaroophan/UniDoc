import { Component } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface Props { children: ReactNode }
interface State { failed: boolean }

/** Last-resort view for render crashes; never echo exception data to students. */
export class AppErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }


  render() {
    if (!this.state.failed) return this.props.children
    return (
      <main className="session-gate" id="main-content" tabIndex={-1}>
        <div className="session-gate-card" role="alert">
          <h1 className="h4">This page could not be displayed</h1>
          <p className="text-muted">Your changes may still have been saved. Review the latest information before repeating an action.</p>
          <div className="d-flex flex-wrap gap-2 justify-content-center">
            <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>Reload page</button>
            <Link to="/dashboard" className="btn btn-outline-secondary">Return to dashboard</Link>
          </div>
        </div>
      </main>
    )
  }
}
