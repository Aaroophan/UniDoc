import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import axios from 'axios'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import type { DeliveryMethod } from '../api/contracts'
import { DELIVERY_METHOD_LABELS } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { getActiveDocumentType } from '../catalogue/documentTypesApi'
import type { CatalogueDocumentType } from '../catalogue/documentTypesApi'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { PageHeader } from '../components/PageHeader'
import { PageState } from '../components/PageState'
import { getStudentProfile, hasCompleteStudentProfile } from '../profile/profileApi'
import { useNotificationCount } from '../notifications/NotificationCountContext'
import type { StudentProfileResponse } from '../profile/profileApi'
import { formatFileSize } from './requestFormat'
import { createStudentRequest, parsePositiveId, SubmissionOutcomeUnconfirmedError } from './requestsApi'
import type { CreateStudentRequest } from './requestsApi'
import { localToday, validateStudentRequest, validateSupportingFiles } from './requestValidation'
import type { RequestFormErrors } from './requestValidation'
import './requests.css'

type LoadState = 'loading' | 'error' | 'unavailable' | 'incomplete' | 'ready' | 'choose-type'
type Feedback = { variant: 'error' | 'warning'; message: string; requestId?: string | null }

function mappedFieldErrors(fields: Record<string, string> | null): RequestFormErrors {
  const result: RequestFormErrors = {}
  if (!fields) return result
  for (const [rawName, message] of Object.entries(fields)) {
    const field = rawName.split('.').at(-1)
    if (field === 'purpose' || field === 'requiredDate' || field === 'deliveryMethod' ||
        field === 'files' || field === 'documentTypeId') result[field] = message
  }
  return result
}

export function NewStudentRequestPage() {
  const navigate = useNavigate()
  const { refreshUnreadCount } = useNotificationCount()
  const [searchParams] = useSearchParams()
  const rawTypeId = searchParams.get('documentTypeId')
  const typeId = parsePositiveId(rawTypeId)
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [catalogueType, setCatalogueType] = useState<CatalogueDocumentType | null>(null)
  const [profile, setProfile] = useState<StudentProfileResponse | null>(null)
  const [loadError, setLoadError] = useState<Feedback | null>(null)
  const [reload, setReload] = useState(0)

  const [purpose, setPurpose] = useState('')
  const [requiredDate, setRequiredDate] = useState('')
  const [deliveryMethod, setDeliveryMethod] = useState<DeliveryMethod | ''>('')
  const [files, setFiles] = useState<File[]>([])
  const [errors, setErrors] = useState<RequestFormErrors>({})
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [outcomeUnknown, setOutcomeUnknown] = useState(false)
  const [today, setToday] = useState(localToday)
  const fileInput = useRef<HTMLInputElement | null>(null)
  const submissionLock = useRef(false)

  useEffect(() => {
    const controller = new AbortController()
    if (typeId === null) {
      setLoadState('choose-type')
      setCatalogueType(null)
      return () => controller.abort()
    }
    async function load() {
      setLoadState('loading')
      setLoadError(null)
      const [profileResult, typeResult] = await Promise.allSettled([
        getStudentProfile(controller.signal),
        getActiveDocumentType(typeId!, controller.signal),
      ])
      if (controller.signal.aborted) return
      if (typeResult.status === 'rejected') {
        const apiError = getApiError(typeResult.reason)
        if (apiError?.status === 404 || apiError?.code === 'DOCUMENT_TYPE_INACTIVE') {
          setLoadState('unavailable')
          return
        }
        setLoadError({ variant: 'error', message: getSafeErrorMessage(typeResult.reason, 'Unable to load this document type.'), requestId: apiError?.requestId })
        setLoadState('error')
        return
      }
      if (profileResult.status === 'rejected') {
        setLoadError({ variant: 'error', message: getSafeErrorMessage(profileResult.reason, 'Unable to check your academic profile.'), requestId: getApiError(profileResult.reason)?.requestId })
        setLoadState('error')
        return
      }
      const freshType = typeResult.value
      setCatalogueType(freshType)
      setProfile(profileResult.value)
      if (!hasCompleteStudentProfile(profileResult.value)) {
        setLoadState('incomplete')
        return
      }
      if (!freshType.availableDeliveryMethods.length) {
        setLoadState('unavailable')
        return
      }
      setDeliveryMethod((previous) => freshType.availableDeliveryMethods.includes(previous as DeliveryMethod)
        ? previous : freshType.availableDeliveryMethods[0])
      setLoadState('ready')
    }
    void load()
    return () => controller.abort()
  }, [typeId, reload])

  // A different selected document must never inherit data or files from the prior selection.
  useEffect(() => {
    setPurpose('')
    setRequiredDate('')
    setDeliveryMethod('')
    setFiles([])
    setErrors({})
    setFeedback(null)
    setOutcomeUnknown(false)
    setToday(localToday())
    if (fileInput.current) fileInput.current.value = ''
  }, [typeId])

  function onFileChange(event: ChangeEvent<HTMLInputElement>) {
    const incoming = Array.from(event.currentTarget.files ?? [])
    event.currentTarget.value = ''
    if (!incoming.length || submissionLock.current) return
    const next = [...files, ...incoming]
    const problem = validateSupportingFiles(next, false)
    if (problem) {
      setErrors((previous) => ({ ...previous, files: problem }))
      return
    }
    setFiles(next)
    setErrors((previous) => ({ ...previous, files: undefined }))
  }

  function removeFile(index: number) {
    if (submissionLock.current) return
    setFiles((current) => current.filter((_, position) => position !== index))
    setErrors((previous) => ({ ...previous, files: undefined }))
    if (fileInput.current) fileInput.current.value = ''
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submissionLock.current || loadState !== 'ready' || !catalogueType || !profile || outcomeUnknown) return
    setToday(localToday())
    const payload: CreateStudentRequest = {
      documentTypeId: catalogueType.id,
      purpose: purpose.trim(),
      requiredDate,
      deliveryMethod: deliveryMethod as DeliveryMethod,
    }
    const nextErrors = validateStudentRequest(payload, catalogueType, files)
    if (!deliveryMethod) nextErrors.deliveryMethod = 'Choose a delivery method.'
    setErrors(nextErrors)
    setFeedback(null)
    if (Object.keys(nextErrors).length) return

    // A ref protects against two same-tick submissions before React commits disabled state.
    submissionLock.current = true
    setSubmitting(true)
    try {
      const created = await createStudentRequest(payload, files)
      if (!Number.isSafeInteger(created.id) || created.id < 1 || created.status !== 'SUBMITTED' || !created.requestNumber) {
        setOutcomeUnknown(true)
        setFeedback({ variant: 'warning', message: 'The server response could not be verified. Check My requests before attempting another submission.' })
        return
      }
      void refreshUnreadCount()
      navigate(`/requests/${created.id}`, { replace: true, state: { submitted: true, requestNumber: created.requestNumber } })
    } catch (failure: unknown) {
      const apiError = getApiError(failure)
      if (apiError?.code === 'PROFILE_INCOMPLETE') {
        setLoadState('incomplete')
        setLoadError({ variant: 'warning', message: 'Your academic profile needs to be completed before submitting.', requestId: apiError.requestId })
      } else if (apiError?.code === 'DOCUMENT_TYPE_NOT_FOUND' || apiError?.code === 'DOCUMENT_TYPE_INACTIVE') {
        setLoadState('unavailable')
      } else if (failure instanceof SubmissionOutcomeUnconfirmedError || (axios.isAxiosError(failure) && !failure.response)) {
        // The POST may have committed before the network failed. Never auto-retry it.
        setOutcomeUnknown(true)
        setFeedback({ variant: 'warning', message: 'The connection ended before we could confirm whether your request was submitted. Check My requests for a new request number before attempting another submission.' })
      } else {
        const newErrors = mappedFieldErrors(apiError?.fieldErrors ?? null)
        switch (apiError?.code) {
          case 'SUPPORTING_DOCUMENT_REQUIRED': newErrors.files = 'Add at least one supporting document.'; break
          case 'FILE_TOO_LARGE': newErrors.files = 'Each supporting document must be 5 MiB or smaller.'; break
          case 'FILE_COUNT_EXCEEDED': newErrors.files = 'Choose no more than three supporting documents.'; break
          case 'UNSUPPORTED_FILE_TYPE': newErrors.files = 'One or more files were rejected by the server. Use a valid PDF, JPG, or PNG.'; break
          case 'INVALID_DELIVERY_METHOD': newErrors.deliveryMethod = 'The selected delivery method is no longer available.'; break
          case 'INVALID_REQUIRED_DATE': newErrors.requiredDate = 'Choose today or a future date.'; break
        }
        setErrors((current) => ({ ...current, ...newErrors }))
        setFeedback({ variant: 'error', message: getSafeErrorMessage(failure, 'Unable to submit the request.'), requestId: apiError?.requestId })
      }
    } finally {
      submissionLock.current = false
      setSubmitting(false)
    }
  }

  const description = 'Check the document requirements and your profile before sending a new request.'
  return (
    <>
      <PageHeader eyebrow="Student workspace" title="New document request" description={description}
        actions={<Link to="/catalogue" className="btn btn-outline-secondary">Back to catalogue</Link>} />
      {loadState === 'loading' && <PageState status="loading" message="Checking your profile and the selected document type..." />}
      {loadState === 'error' && <PageState status="error" title="Unable to prepare your request" message={loadError?.message ?? 'Please try again.'}
        requestId={loadError?.requestId} onRetry={() => setReload((current) => current + 1)} />}
      {loadState === 'choose-type' && <PageState status="empty" title="Choose a document first"
        message="Start in the document catalogue to select an available document type and delivery option."
        action={<Link to="/catalogue" className="btn btn-primary">Browse document catalogue</Link>} />}
      {loadState === 'unavailable' && <PageState status="empty" title="This document is no longer available"
        message="The selected document type is unavailable or has changed. Choose an active document from the catalogue."
        action={<Link to="/catalogue" className="btn btn-primary">Return to catalogue</Link>} />}
      {loadState === 'incomplete' && (
        <section className="surface-card profile-required" aria-labelledby="student-profile-required-title">
          <p className="section-eyebrow mb-2">Action required</p>
          <h2 className="h4 mb-3" id="student-profile-required-title">Complete your academic profile first</h2>
          <p className="text-muted">UniDoc requires your saved registration number, faculty, department, degree programme, and academic year before accepting requests.</p>
          {loadError && <FeedbackBanner variant={loadError.variant} message={loadError.message} requestId={loadError.requestId} />}
          <div className="d-flex flex-wrap gap-2 mt-3">
            <Link className="btn btn-primary" to="/profile">Complete my profile</Link>
            <button type="button" className="btn btn-outline-secondary" onClick={() => setReload((value) => value + 1)}>Check again</button>
          </div>
        </section>
      )}
      {loadState === 'ready' && catalogueType && profile && (
        <>
          <section className="surface-card request-type-overview" aria-labelledby="new-request-type-title">
            <div className="request-type-header">
              <div className="min-width-0">
                <p className="section-eyebrow mb-2">Selected document · {catalogueType.code}</p>
                <h2 className="h4 mb-2" id="new-request-type-title">{catalogueType.name}</h2>
                <p className="request-wrap mb-0">{catalogueType.description}</p>
              </div>
              <Link to="/catalogue" className="btn btn-outline-secondary btn-sm">Change document</Link>
            </div>
            <div className="request-type-details">
              <div>
                <h3 className="h6">Requirements</h3>
                <p className="request-wrap mb-0">{catalogueType.requirements}</p>
              </div>
              <div>
                <h3 className="h6">Supporting documents</h3>
                <p className="mb-0">{catalogueType.supportingDocumentsRequired ? 'At least one supporting document is required.' : 'Supporting documents are optional.'}</p>
                <p className="small text-muted mt-2 mb-0">Maximum 3 files. PDF, JPG/JPEG, or PNG. 5 MiB each.</p>
              </div>
              {catalogueType.availableDeliveryMethods.includes('PHYSICAL') && (
                <div>
                  <h3 className="h6">Physical collection information</h3>
                  <p className="request-wrap mb-0">{catalogueType.collectionLocation || 'Location will be confirmed by the university.'}</p>
                  {catalogueType.collectionInstructions && <p className="request-wrap text-muted mt-1 mb-0">{catalogueType.collectionInstructions}</p>}
                </div>
              )}
            </div>
          </section>
          <div className="request-create-layout">
            <section className="surface-card request-form-card" aria-labelledby="request-form-heading">
              <div className="request-panel-heading">
                <p className="section-eyebrow mb-2">Step 2 of 2</p>
                <h2 className="h4 mb-1" id="request-form-heading">Request details</h2>
                <p className="text-muted mb-0 small">Fields marked with * are required.</p>
              </div>
              <form className="request-form" onSubmit={(event) => { void onSubmit(event) }} noValidate>
                {feedback && <div className="mb-4"><FeedbackBanner variant={feedback.variant} message={feedback.message} requestId={feedback.requestId} onDismiss={() => setFeedback(null)} /></div>}
                {outcomeUnknown && (
                  <div className="request-uncertain" role="alert">
                    <strong>Check before resubmitting</strong>
                    <p className="mb-3">Your original request may already exist. Look for it in My requests before trying again.</p>
                    <div className="d-flex flex-wrap gap-2">
                      <Link className="btn btn-primary btn-sm" to="/requests">Check My requests</Link>
                      <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => { setOutcomeUnknown(false); setFeedback(null) }}>I have checked; allow another attempt</button>
                    </div>
                  </div>
                )}
                <div className="form-field">
                  <label className="form-label" htmlFor="request-purpose">Purpose of request *</label>
                  <textarea id="request-purpose" name="purpose" rows={5} maxLength={1000}
                    className={`form-control request-textarea${errors.purpose ? ' is-invalid' : ''}`}
                    aria-invalid={Boolean(errors.purpose)} aria-describedby={errors.purpose ? 'request-purpose-help request-purpose-error' : 'request-purpose-help'}
                    value={purpose} disabled={submitting || outcomeUnknown}
                    onChange={(event) => { setPurpose(event.target.value); setErrors((current) => ({ ...current, purpose: undefined })) }}
                    placeholder="Explain why you need this document." />
                  <div className="d-flex justify-content-between gap-3 form-text" id="request-purpose-help"><span>Between 5 and 1000 characters.</span><span>{purpose.trim().length}/1000</span></div>
                  {errors.purpose && <div id="request-purpose-error" className="invalid-feedback" role="alert">{errors.purpose}</div>}
                </div>
                <div className="form-field">
                  <label className="form-label" htmlFor="request-required-date">Required by *</label>
                  <input id="request-required-date" name="requiredDate" type="date" min={today}
                    value={requiredDate} disabled={submitting || outcomeUnknown}
                    className={`form-control${errors.requiredDate ? ' is-invalid' : ''}`}
                    aria-invalid={Boolean(errors.requiredDate)} aria-describedby={errors.requiredDate ? 'request-date-help request-date-error' : 'request-date-help'}
                    onChange={(event) => { setRequiredDate(event.target.value); setErrors((current) => ({ ...current, requiredDate: undefined })) }} />
                  <p className="form-text" id="request-date-help">This is your requested date, not a guarantee of delivery.</p>
                  {errors.requiredDate && <div id="request-date-error" className="invalid-feedback" role="alert">{errors.requiredDate}</div>}
                </div>
                <fieldset className="form-field request-delivery-fieldset" aria-describedby={errors.deliveryMethod ? 'request-delivery-help request-delivery-error' : 'request-delivery-help'}>
                  <legend className="form-label mb-2">Delivery method *</legend>
                  {catalogueType.availableDeliveryMethods.length === 1 ? (
                    <div className="request-single-method">{DELIVERY_METHOD_LABELS[catalogueType.availableDeliveryMethods[0]]} delivery</div>
                  ) : (
                    <div className="request-delivery-choices">
                      {catalogueType.availableDeliveryMethods.map((method) => (
                        <label key={method} className={`request-delivery-choice${deliveryMethod === method ? ' is-selected' : ''}`}>
                          <input type="radio" name="deliveryMethod" value={method} checked={deliveryMethod === method}
                            disabled={submitting || outcomeUnknown}
                            onChange={() => { setDeliveryMethod(method); setErrors((current) => ({ ...current, deliveryMethod: undefined })) }} />
                          <span><strong>{DELIVERY_METHOD_LABELS[method]}</strong><small>{method === 'ELECTRONIC' ? 'Digital document when issued' : 'Collect from the university in person'}</small></span>
                        </label>
                      ))}
                    </div>
                  )}
                  <p className="form-text" id="request-delivery-help">Only options allowed for this document type are shown.</p>
                  {errors.deliveryMethod && <div className="catalogue-field-error" id="request-delivery-error" role="alert">{errors.deliveryMethod}</div>}
                  {deliveryMethod === 'PHYSICAL' && (
                    <div className="request-collection-notice">
                      <strong>Collection location</strong>
                      <p className="request-wrap mt-1 mb-0">{catalogueType.collectionLocation || 'Confirm the location with the university.'}</p>
                      {catalogueType.collectionInstructions && <p className="request-wrap mt-2 mb-0">{catalogueType.collectionInstructions}</p>}
                    </div>
                  )}
                </fieldset>
                <div className="form-field">
                  <label className="form-label" htmlFor="request-supporting-files">Supporting documents {catalogueType.supportingDocumentsRequired ? '*' : '(optional)'}</label>
                  <input ref={fileInput} id="request-supporting-files" className={`form-control request-file-input${errors.files ? ' is-invalid' : ''}`}
                    type="file" accept=".pdf,.jpg,.jpeg,.png" multiple
                    aria-invalid={Boolean(errors.files)} aria-describedby={errors.files ? 'request-files-help request-files-error' : 'request-files-help'}
                    disabled={submitting || outcomeUnknown || files.length >= 3} onChange={onFileChange} />
                  <p className="form-text" id="request-files-help">Select PDF/JPG/PNG files (up to 3, 5 MiB each). Files are checked again by the server. You can add files in multiple selections.</p>
                  {errors.files && <div id="request-files-error" className="invalid-feedback" role="alert">{errors.files}</div>}
                  {files.length > 0 && (
                    <div className="request-selected-files" aria-live="polite">
                      <div className="request-selected-files-heading"><strong>Selected files ({files.length}/3)</strong><button type="button" disabled={submitting || outcomeUnknown}
                        className="btn btn-link btn-sm" onClick={() => { setFiles([]); setErrors((current) => ({ ...current, files: undefined })); if (fileInput.current) fileInput.current.value = '' }}>Clear all</button></div>
                      <ul className="request-file-list">
                        {files.map((file, index) => (
                          <li key={`${file.name}-${file.size}-${file.lastModified}-${index}`}>
                            <span className="request-wrap"><strong>{file.name}</strong><small className="d-block text-muted">{formatFileSize(file.size)}</small></span>
                            <button type="button" className="btn btn-outline-secondary btn-sm" disabled={submitting || outcomeUnknown}
                              onClick={() => removeFile(index)} aria-label={`Remove ${file.name}`}>Remove</button>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
                {errors.documentTypeId && <FeedbackBanner variant="error" message={errors.documentTypeId} />}
                <div className="request-form-footer">
                  <p className="small text-muted mb-3">Once submitted, requests cannot be edited or cancelled. Review your information before sending.</p>
                  <button type="submit" className="btn btn-primary request-submit-button" disabled={submitting || outcomeUnknown}>
                    {submitting ? <><span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />Submitting request...</> : 'Submit document request'}
                  </button>
                </div>
              </form>
            </section>
            <aside className="request-create-aside" aria-label="Submission information">
              <section className="surface-card request-aside-card">
                <p className="section-eyebrow mb-2">Step 1 of 2 complete</p>
                <h2 className="h5 mb-3">Your academic profile</h2>
                <dl className="request-mini-facts">
                  <div><dt>Name</dt><dd>{profile.fullName}</dd></div>
                  <div><dt>Registration</dt><dd>{profile.registrationNumber || 'Not set'}</dd></div>
                  <div><dt>Faculty</dt><dd>{profile.faculty || 'Not set'}</dd></div>
                  <div><dt>Programme</dt><dd>{profile.degreeProgramme || 'Not set'}</dd></div>
                </dl>
                <Link to="/profile" className="small fw-semibold">View my profile</Link>
              </section>
              <section className="request-info-note">
                <strong>What happens next?</strong>
                <p className="mb-0">After submission you will receive a request number. Follow progress, staff messages, and supporting documents in My requests.</p>
              </section>
            </aside>
          </div>
        </>
      )}
    </>
  )
}
