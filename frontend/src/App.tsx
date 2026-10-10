import { Route, Routes } from 'react-router-dom'
import './App.css'

function BaselinePage() {
  return (
    <main className="baseline-shell">
      <section className="baseline-card" aria-labelledby="baseline-title">
        <p className="baseline-eyebrow">University document requests</p>
        <h1 id="baseline-title">UniDoc</h1>
        <p className="baseline-copy">
          The local development baseline is ready. Student, staff, and
          administrator workflows will be added in their approved implementation
          issues.
        </p>
        <span className="badge rounded-pill baseline-badge">Issue 1 baseline</span>
      </section>
    </main>
  )
}

function App() {
  return (
    <Routes>
      <Route path="*" element={<BaselinePage />} />
    </Routes>
  )
}

export default App
