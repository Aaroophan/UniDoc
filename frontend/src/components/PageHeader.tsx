import type { ReactNode } from 'react'

export interface PageHeaderProps {
  eyebrow?: string
  title: string
  description?: string
  actions?: ReactNode
}

export function PageHeader({ eyebrow, title, description, actions }: PageHeaderProps) {
  return (
    <header className="page-header d-flex flex-column flex-sm-row justify-content-between align-items-sm-start gap-3">
      <div>
        {eyebrow && <p className="section-eyebrow mb-2">{eyebrow}</p>}
        <h1 className="page-title">{title}</h1>
        {description && <p className="page-lead mb-0">{description}</p>}
      </div>
      {actions && <div className="page-actions d-flex gap-2 flex-wrap">{actions}</div>}
    </header>
  )
}
