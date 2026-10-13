import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { FieldErrors } from '../api/contracts'
import { getApiError, getSafeErrorMessage } from '../api/errors'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FeedbackBanner } from '../components/FeedbackBanner'
import { focusFirstInvalidField } from '../components/formFocus'
import { changeDocumentTypeStatus, createDocumentType, updateDocumentType } from './documentTypesApi'
import type { AdminDocumentType } from './documentTypesApi'
import { asForm, BLANK_DOCUMENT_TYPE, toPayload, validateDocumentType } from './documentTypesValidation'
import type { DocumentTypeForm } from './documentTypesValidation'

type Alert = { message: string; requestId?: string | null; warning?: boolean }
type TextField = 'code' | 'name' | 'description' | 'requirements' | 'collectionLocation' | 'collectionInstructions'

function focusInvalid(form: HTMLFormElement | null, errors: FieldErrors) {
  const first = ['code', 'name', 'description', 'requirements', 'electronicAvailable', 'collectionLocation', 'collectionInstructions']
    .find((field) => Boolean(errors[field]))
  if (first) focusFirstInvalidField(form, [first])
}

function TextInput({ name, label, value, onChange, error, multiline = false, maxLength, optional = false, disabled }: {
  name: TextField
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  multiline?: boolean
  maxLength: number
  optional?: boolean
  disabled: boolean
}) {
  const control = {
    id: `catalogue-field-${name}`, name, value, maxLength, disabled,
    className: `form-control${error ? ' is-invalid' : ''}`,
    'aria-invalid': Boolean(error),
    'aria-describedby': error ? `catalogue-error-${name}` : undefined,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange(event.target.value),
  }
  return (
    <div className="form-field">
      <label className="form-label" htmlFor={control.id}>{label}{optional && <span className="profile-optional"> · Optional</span>}</label>
      {multiline ? <textarea {...control} rows={name === 'requirements' ? 4 : 3} /> : <input {...control} type="text" />}
      {error && <div id={`catalogue-error-${name}`} className="invalid-feedback">{error}</div>}
    </div>
  )
}

export function DocumentTypeFormDialog({ existing, onClose, onSaved, onUncertain }: {
  existing?: AdminDocumentType
  onClose: () => void
  onSaved: (result: AdminDocumentType, operation: 'created' | 'updated') => void
  onUncertain: (id: number | null, code: string) => void
}) {
  const formRef = useRef<HTMLFormElement>(null)
  const [form, setForm] = useState<DocumentTypeForm>(() => existing ? asForm(existing) : { ...BLANK_DOCUMENT_TYPE })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [alert, setAlert] = useState<Alert | null>(null)
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)

  function change<K extends keyof DocumentTypeForm>(field: K, value: DocumentTypeForm[K]) {
    setForm((old) => ({ ...old, [field]: value }))
    setErrors((old) => ({ ...old, [field]: '' }))
    setAlert(null)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || uncertain) return
    setAlert(null)
    const nextErrors = validateDocumentType(form)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length) { focusInvalid(formRef.current, nextErrors); return }
    setBusy(true)
    try {
      const values = toPayload(form)
      const result = existing
        ? await updateDocumentType(existing.id, values)
        : await createDocumentType(values)
      onSaved(result, existing ? 'updated' : 'created')
    } catch (error: unknown) {
      const response = getApiError(error)
      if (!response) {
        setUncertain(true)
        onUncertain(existing?.id ?? null, form.code)
        setAlert({ warning: true, message: 'The response was interrupted. The catalogue is being checked against the server. Close this dialog and verify the saved entry before retrying.' })
      } else {
        const serverErrors: FieldErrors = { ...(response.fieldErrors ?? {}) }
        if (response.code === 'DOCUMENT_TYPE_CODE_EXISTS') serverErrors.code = 'A document type already uses this code.'
        if (response.code === 'DOCUMENT_TYPE_NAME_EXISTS') serverErrors.name = 'A document type already uses this name.'
        setErrors(serverErrors)
        focusInvalid(formRef.current, serverErrors)
        setAlert({ message: getSafeErrorMessage(error, 'Unable to save the document type.'), requestId: response.requestId })
      }
    } finally {
      setBusy(false)
    }
  }

  const edit = Boolean(existing)
  return (
    <ConfirmDialog open title={edit ? 'Edit document type' : 'Create document type'}
      description={edit ? `Review and replace the details for ${existing?.name}. Existing requests retain their saved document snapshots.` : 'Set the requirements and delivery options. New entries can be created inactive.'}
      confirmLabel={edit ? 'Save changes' : 'Create document type'} busy={busy} disabledConfirm={uncertain} onCancel={onClose}
      onConfirm={() => formRef.current?.requestSubmit()}>
      <form ref={formRef} className="catalogue-type-form" noValidate onSubmit={(event) => void submit(event)}>
        {alert && <div className="mb-3"><FeedbackBanner variant={alert.warning ? 'warning' : 'error'} message={alert.message} requestId={alert.requestId} /></div>}
        <TextInput name="code" label="Document code" maxLength={50} value={form.code} disabled={busy || uncertain}
          error={errors.code} onChange={(value) => change('code', value)} />
        <p className="small text-muted mt-n1">2–50 letters, numbers, underscores or hyphens. Saved in uppercase.</p>
        <TextInput name="name" label="Document name" maxLength={150} value={form.name} disabled={busy || uncertain}
          error={errors.name} onChange={(value) => change('name', value)} />
        <TextInput name="description" label="Description" maxLength={2000} multiline value={form.description} disabled={busy || uncertain}
          error={errors.description} onChange={(value) => change('description', value)} />
        <TextInput name="requirements" label="Requirements and supporting evidence" maxLength={4000} multiline value={form.requirements}
          disabled={busy || uncertain} error={errors.requirements} onChange={(value) => change('requirements', value)} />
        <div className="catalogue-type-form-group" aria-labelledby="catalogue-delivery-heading">
          <h3 id="catalogue-delivery-heading" className="h6">Available delivery methods</h3>
          <div className="form-check">
            <input type="checkbox" className="form-check-input" id="catalogue-electronic" name="electronicAvailable"
              checked={form.electronicAvailable} disabled={busy || uncertain}
              aria-invalid={Boolean(errors.electronicAvailable)} aria-describedby={errors.electronicAvailable ? 'catalogue-delivery-error' : undefined}
              onChange={(event) => change('electronicAvailable', event.target.checked)} />
            <label className="form-check-label" htmlFor="catalogue-electronic">Electronic document</label>
          </div>
          <div className="form-check">
            <input type="checkbox" className="form-check-input" id="catalogue-physical" name="physicalAvailable"
              checked={form.physicalAvailable} disabled={busy || uncertain}
              onChange={(event) => change('physicalAvailable', event.target.checked)} />
            <label className="form-check-label" htmlFor="catalogue-physical">Physical collection</label>
          </div>
          {errors.electronicAvailable && <p role="alert" id="catalogue-delivery-error" className="catalogue-field-error">{errors.electronicAvailable}</p>}
        </div>
        {form.physicalAvailable && <>
          <TextInput name="collectionLocation" label="Collection location" maxLength={255} value={form.collectionLocation}
            disabled={busy || uncertain} error={errors.collectionLocation} onChange={(value) => change('collectionLocation', value)} />
          <TextInput name="collectionInstructions" label="Collection instructions" optional maxLength={2000} multiline
            value={form.collectionInstructions} disabled={busy || uncertain} error={errors.collectionInstructions}
            onChange={(value) => change('collectionInstructions', value)} />
        </>}
        {!form.physicalAvailable && <p className="small text-muted">Physical collection is disabled. Previously entered collection guidance is retained for a future change.</p>}
        <div className="catalogue-type-form-group">
          <div className="form-check">
            <input type="checkbox" className="form-check-input" id="catalogue-requires-support" name="supportingDocumentsRequired"
              checked={form.supportingDocumentsRequired} disabled={busy || uncertain}
              onChange={(event) => change('supportingDocumentsRequired', event.target.checked)} />
            <label className="form-check-label" htmlFor="catalogue-requires-support">Require supporting documents from students</label>
          </div>
        </div>
        {edit ? (
          <p className="catalogue-editor-note">Status: <strong>{form.active ? 'Active' : 'Inactive'}</strong>. Use the separate {form.active ? 'Deactivate' : 'Activate'} action to change visibility with confirmation.</p>
        ) : (
          <div className="form-check catalogue-type-form-group">
            <input type="checkbox" className="form-check-input" id="catalogue-initial-active" name="active"
              checked={form.active} disabled={busy || uncertain} onChange={(event) => change('active', event.target.checked)} />
            <label className="form-check-label" htmlFor="catalogue-initial-active">Make this document type visible in the active catalogue</label>
          </div>
        )}
      </form>
    </ConfirmDialog>
  )
}

export function DocumentTypeStatusDialog({ item, onClose, onChanged, onUncertain }: {
  item: AdminDocumentType
  onClose: () => void
  onChanged: (item: AdminDocumentType) => void
  onUncertain: (id: number, code: string) => void
}) {
  const next = !item.active
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [alert, setAlert] = useState<Alert | null>(null)

  async function confirm() {
    if (busy || uncertain) return
    setBusy(true)
    try { onChanged(await changeDocumentTypeStatus(item.id, next)) }
    catch (error: unknown) {
      const response = getApiError(error)
      if (!response) {
        setUncertain(true)
        onUncertain(item.id, item.code)
        setAlert({ warning: true, message: 'Unable to confirm the status change. The current server status is being checked; close this dialog and review it before another action.' })
      } else {
        setAlert({ message: getSafeErrorMessage(error, 'Unable to change document visibility.'), requestId: response.requestId })
      }
    } finally { setBusy(false) }
  }

  return (
    <ConfirmDialog open title={next ? 'Activate document type' : 'Deactivate document type'}
      description={`${next ? 'Activate' : 'Deactivate'} ${item.name} (${item.code})?`}
      confirmLabel={next ? 'Activate document type' : 'Deactivate document type'}
      destructive={!next} busy={busy} disabledConfirm={uncertain} onCancel={onClose} onConfirm={() => void confirm()}>
      <p className="catalogue-editor-note">{next
        ? 'This document type will become available to all authenticated users and eligible students may select it for future requests.'
        : 'This document type will disappear from the active catalogue and cannot be selected for new requests. Existing requests and their snapshots are preserved.'}</p>
      {alert && <FeedbackBanner variant={alert.warning ? 'warning' : 'error'} message={alert.message} requestId={alert.requestId} />}
    </ConfirmDialog>
  )
}
