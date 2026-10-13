import axios from 'axios'
import { useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { focusFirstInvalidField } from '../components/formFocus'
import { useNotificationCount } from '../notifications/NotificationCountContext'
import { formatFileSize } from './requestFormat'
import { getStudentRequest, submitStudentInformationResponse } from './requestsApi'
import type { StudentRequestDetail } from './requestsApi'
import { validateSupportingFiles } from './requestValidation'
import { validateReviewText } from './reviewValidation'

export type ResponseNotice = { variant: 'success' | 'warning' | 'error'; message: string; requestId?: string | null }

export function StudentInformationResponseForm({ details, onUpdate }: {
  details: StudentRequestDetail
  onUpdate: (updated: StudentRequestDetail, notice: ResponseNotice) => void
}) {
  const { refreshUnreadCount } = useNotificationCount()
  const [message, setMessage] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [messageError, setMessageError] = useState<string | null>(null)
  const [filesError, setFilesError] = useState<string | null>(null)
  const [feedback, setFeedback] = useState<ResponseNotice | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [outcomeUnknown, setOutcomeUnknown] = useState(false)
  const [checking, setChecking] = useState(false)
  const uploadInput = useRef<HTMLInputElement>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const submittingLock = useRef(false)

  function onFilesSelected(event: ChangeEvent<HTMLInputElement>) {
    const incoming = Array.from(event.currentTarget.files ?? [])
    event.currentTarget.value = ''
    if (submittingLock.current || outcomeUnknown || incoming.length === 0) return
    const next = [...files, ...incoming]
    const invalid = validateSupportingFiles(next, false)
    if (invalid) { setFilesError(invalid); return }
    setFiles(next)
    setFilesError(null)
  }

  async function refreshAfterUncertain(sentMessage: string): Promise<boolean> {
    try {
      const latest = await getStudentRequest(details.id)
      const last = latest.history.at(-1)
      const confirmed = last?.newStatus === 'SUBMITTED' && last.previousStatus === 'ADDITIONAL_INFORMATION_REQUIRED' &&
        last.changedBy.id === details.student.id && last.note === sentMessage
      if (confirmed) {
        void refreshUnreadCount()
        onUpdate(latest, { variant: 'success', message: 'Your information response was submitted. Your request is awaiting review again.' })
      } else {
        onUpdate(latest, { variant: 'warning', message: 'The latest status was loaded. Check its history before deciding whether to submit again.' })
      }
      return confirmed
    } catch {
      setFeedback({ variant: 'warning', message: 'The outcome could not be confirmed. Check your connection and refresh the request before submitting again.' })
      return false
    }
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingLock.current || outcomeUnknown || details.status !== 'ADDITIONAL_INFORMATION_REQUIRED'
      || !details.availableActions.includes('RESPOND')) return
    const cleaned = message.trim()
    const invalidMessage = validateReviewText(cleaned, 'information-response')
    const invalidFiles = validateSupportingFiles(files, false)
    setMessageError(invalidMessage)
    setFilesError(invalidFiles)
    setFeedback(null)
    if (invalidMessage || invalidFiles) {
      focusFirstInvalidField(formRef.current, invalidMessage ? ['message'] : ['files'])
      return
    }
    submittingLock.current = true
    setSubmitting(true)
    try {
      const updated = await submitStudentInformationResponse(details.id,
        { message: cleaned, expectedVersion: details.version }, files)
      if (updated.status !== 'SUBMITTED') {
        setOutcomeUnknown(true)
        await refreshAfterUncertain(cleaned)
      } else {
        void refreshUnreadCount()
        onUpdate(updated, { variant: 'success', message: 'Additional information submitted. Staff can now review your request again.' })
        setMessage('')
        setFiles([])
      }
    } catch (failure: unknown) {
      const api = getApiError(failure)
      if (api?.code === 'REQUEST_VERSION_CONFLICT' || api?.code === 'INVALID_REQUEST_TRANSITION') {
        setOutcomeUnknown(true)
        const confirmed = await refreshAfterUncertain(cleaned)
        if (!confirmed) setFeedback({ variant: 'warning', message: 'This request changed since you opened it. Review the latest status and history before replying again.', requestId: api.requestId })
      } else if (axios.isAxiosError(failure) && !failure.response) {
        setOutcomeUnknown(true)
        const confirmed = await refreshAfterUncertain(cleaned)
        if (!confirmed) setFeedback({ variant: 'warning', message: 'Could not confirm whether your response was submitted. Check the refreshed status and history before retrying.' })
      } else {
        setMessageError(api?.fieldErrors?.message ?? null)
        setFilesError(api?.fieldErrors?.files ?? (
          api?.code === 'FILE_TOO_LARGE' ? 'Each file must be 5 MiB or smaller.' :
          api?.code === 'FILE_COUNT_EXCEEDED' ? 'Choose no more than three files.' :
          api?.code === 'UNSUPPORTED_FILE_TYPE' || api?.code === 'FILE_EMPTY'
            ? 'The selected file was rejected. Use a non-empty PDF, JPG, or PNG.' : null))
        focusFirstInvalidField(formRef.current)
        setFeedback({ variant: 'error', message: getSafeErrorMessage(failure, 'Unable to submit your response.'), requestId: api?.requestId })
      }
    } finally {
      submittingLock.current = false
      setSubmitting(false)
    }
  }

  async function manuallyCheckStatus() {
    setChecking(true)
    try {
      const latest = await getStudentRequest(details.id)
      onUpdate(latest, { variant: 'warning', message: 'Latest details loaded. Check the history and the staff message before taking another action.' })
      setOutcomeUnknown(false)
      setFeedback(null)
    } catch (failure: unknown) {
      setFeedback({ variant: 'error', message: getSafeErrorMessage(failure, 'Unable to reload this request.'), requestId: getApiError(failure)?.requestId })
    } finally {
      setChecking(false)
    }
  }

  return <section className="surface-card request-detail-section" aria-labelledby="information-response-title">
    <p className="section-eyebrow mb-2">Action required</p>
    <h2 className="h5 mb-2" id="information-response-title">Respond to the information request</h2>
    <p className="request-callout-intro">Write a response to the staff message above. You may also attach up to three PDF, JPG, or PNG files (5 MiB each). Your original request details cannot be edited.</p>
    <form ref={formRef} onSubmit={(event) => void onSubmit(event)} noValidate>
      <div className="mb-3">
        <label className="form-label fw-semibold" htmlFor="additional-response-message">Your response <span aria-hidden="true">*</span></label>
        <textarea id="additional-response-message" name="message" className={`form-control ${messageError ? 'is-invalid' : ''}`}
          rows={5} minLength={2} maxLength={2000} value={message}
          onChange={(event) => { setMessage(event.target.value); setMessageError(null) }}
          aria-required="true" aria-invalid={Boolean(messageError)} aria-describedby="information-response-message-help information-response-message-error"
          disabled={submitting || checking || outcomeUnknown} />
        <div id="information-response-message-help" className="form-text">2–2000 characters. Your response is recorded in request history.</div>
        {messageError && <p id="information-response-message-error" role="alert" className="request-inline-error">{messageError}</p>}
      </div>
      <div className="mb-3">
        <label className="form-label fw-semibold" htmlFor="additional-response-files">Additional documents (optional)</label>
        <input ref={uploadInput} id="additional-response-files" name="files" type="file" className={`form-control ${filesError ? 'is-invalid' : ''}`}
          accept=".pdf,.jpg,.jpeg,.png" multiple onChange={onFilesSelected}
          aria-invalid={Boolean(filesError)} aria-describedby="information-response-file-help information-response-file-error"
          disabled={submitting || checking || outcomeUnknown} />
        <div id="information-response-file-help" className="form-text">Maximum 3 files per response, 5 MiB each. Existing attachments remain unchanged.</div>
        {filesError && <p id="information-response-file-error" className="request-inline-error" role="alert">{filesError}</p>}
        {files.length > 0 && <ul className="request-download-list mt-2">
          {files.map((file, index) => <li key={`${file.name}-${index}-${file.size}`} className="request-download-item">
            <span className="request-wrap">{file.name} <span className="text-muted small">({formatFileSize(file.size)})</span></span>
            <button type="button" className="btn btn-outline-secondary btn-sm" disabled={submitting || outcomeUnknown}
              onClick={() => { setFiles((current) => current.filter((_, at) => at !== index)); setFilesError(null); if (uploadInput.current) uploadInput.current.value = '' }}>Remove</button>
          </li>)}</ul>}
      </div>
      {feedback && <div className="mb-3"><FeedbackBanner variant={feedback.variant} message={feedback.message} requestId={feedback.requestId} /></div>}
      {outcomeUnknown && <div className="mb-3">
        <p role="status" className="request-callout-intro">A previous response may have reached the server. Do not automatically resubmit it.</p>
        <button type="button" className="btn btn-outline-primary" disabled={checking || submitting}
          onClick={() => void manuallyCheckStatus()}>{checking ? 'Checking...' : 'Check latest status before retrying'}</button>
      </div>}
      <button type="submit" className="btn btn-primary" disabled={submitting || checking || outcomeUnknown}>
        {submitting ? 'Submitting response...' : 'Submit additional information'}</button>
    </form>
  </section>
}
