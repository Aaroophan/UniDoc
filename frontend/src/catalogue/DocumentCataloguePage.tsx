import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { DELIVERY_METHOD_LABELS } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { listActiveDocumentTypes } from './documentTypesApi'
import type { CatalogueDocumentType } from './documentTypesApi'
import './catalogue.css'

type LoadState = 'loading' | 'error' | 'ready' | 'refreshing' | 'stale'

export function DocumentCataloguePage() {
  const { user } = useAuth()
  const [state, setState] = useState<LoadState>('loading')
  const [items, setItems] = useState<CatalogueDocumentType[]>([])
  const [error, setError] = useState<{ message: string; requestId?: string | null } | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    async function load() {
      setState((current) => current === 'ready' || current === 'stale' ? 'refreshing' : 'loading')
      try {
        const result = await listActiveDocumentTypes(controller.signal)
        if (!controller.signal.aborted) { setItems(result); setError(null); setState('ready') }
      } catch (failure: unknown) {
        if (!controller.signal.aborted) {
          setError({ message: getSafeErrorMessage(failure, 'Unable to load the document catalogue.'), requestId: getApiError(failure)?.requestId })
          setState((current) => current === 'refreshing' ? 'stale' : 'error')
        }
      }
    }
    void load()
    return () => controller.abort()
  }, [reload])

  return (
    <>
      <PageHeader eyebrow="Document services" title="Document catalogue"
        description="Explore available university documents, their requirements and the supported delivery methods."
        actions={<div className="d-flex gap-2 flex-wrap">
          {user?.role === 'ADMIN' && <Link to="/admin/document-types" className="btn btn-outline-primary">Manage document types</Link>}
          <button type="button" className="btn btn-outline-secondary" disabled={state === 'loading' || state === 'refreshing'} onClick={() => setReload((n) => n + 1)}>Refresh</button>
        </div>} />
      {state === 'loading' && <PageState status="loading" message="Loading available document types..." />}
      {state === 'refreshing' && <p role="status" className="small text-muted mb-3">Refreshing catalogue…</p>}
      {state === 'stale' && <div role="alert" className="mb-3"><p className="request-inline-error">Unable to refresh the catalogue. Previously loaded entries remain visible. {error?.message}</p><button type="button" className="btn btn-outline-primary btn-sm" onClick={() => setReload((n) => n + 1)}>Try again</button></div>}
      {state === 'error' && <PageState status="error" title="Catalogue unavailable"
        message={error?.message ?? 'Unable to load the catalogue.'} requestId={error?.requestId}
        onRetry={() => setReload((n) => n + 1)} />}
      {(state === 'ready' || state === 'refreshing' || state === 'stale') && items.length === 0 && <PageState status="empty" title="No documents available yet"
        message="There are no active document types to request. Please check again later or contact the university administration." />}
      {(state === 'ready' || state === 'refreshing' || state === 'stale') && items.length > 0 && (
        <>
          <p className="catalogue-summary">{items.length} available document {items.length === 1 ? 'type' : 'types'} · Ordered by name</p>
          <div className="catalogue-grid">
            {items.map((type) => (
              <article key={type.id} className="surface-card catalogue-card" aria-labelledby={`catalogue-title-${type.id}`}>
                <div className="catalogue-card-top">
                  <p className="section-eyebrow mb-2">{type.code}</p>
                  <h2 className="h4 mb-0" id={`catalogue-title-${type.id}`}>{type.name}</h2>
                  <p className="catalogue-description">{type.description}</p>
                </div>
                <div className="catalogue-detail-row">
                  <h3>What you need</h3>
                  <p className="catalogue-break-text">{type.requirements}</p>
                  <p className="catalogue-support">{type.supportingDocumentsRequired
                    ? 'At least one supporting document is required.'
                    : 'Supporting documents are optional.'}</p>
                </div>
                <div className="catalogue-detail-row">
                  <h3>Delivery options</h3>
                  <p>{type.availableDeliveryMethods.map((method) => DELIVERY_METHOD_LABELS[method]).join(' · ')}</p>
                  {type.availableDeliveryMethods.includes('PHYSICAL') && (
                    <div className="catalogue-collection-info">
                      <strong>Physical collection</strong>
                      <p className="catalogue-break-text mb-0">{type.collectionLocation}</p>
                      {type.collectionInstructions && <p className="catalogue-break-text mt-2 mb-0">{type.collectionInstructions}</p>}
                    </div>
                  )}
                </div>
                {user?.role === 'STUDENT' && (
                  <Link className="btn btn-primary catalogue-request-action"
                    to={`/requests/new?documentTypeId=${encodeURIComponent(String(type.id))}`}>
                    Request this document
                  </Link>
                )}
              </article>
            ))}
          </div>
          {user?.role === 'STUDENT' && <p className="small text-muted mt-4">A complete academic profile is required before submitting a document request. You can select an available document above to continue to the request form.</p>}
        </>
      )}
    </>
  )
}
