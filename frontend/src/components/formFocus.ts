/** Focus an invalid field after React has committed its validation feedback. */
export function focusFirstInvalidField(form: HTMLFormElement | null, names?: readonly string[]): void {
  if (!form) return
  requestAnimationFrame(() => {
    if (!form.isConnected) return
    let input: HTMLElement | null = null
    if (names) {
      for (const name of names) {
        const candidate = Array.from(form.elements).find((element) =>
          element instanceof HTMLElement && 'name' in element && element.name === name,
        )
        if (candidate instanceof HTMLElement && !candidate.hasAttribute('disabled')) {
          input = candidate
          break
        }
      }
    } else {
      input = form.querySelector<HTMLElement>('[aria-invalid="true"]:not([disabled]), .is-invalid:not([disabled])')
    }
    input?.focus()
  })
}
