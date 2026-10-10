import type { PageResponse } from '../api/contracts'

type PageMeta = Pick<PageResponse<unknown>, 'page' | 'size' | 'totalItems' | 'totalPages' | 'first' | 'last'>

export interface PaginationProps {
  page: PageMeta
  onPageChange: (zeroBasedPage: number) => void
  disabled?: boolean
}

export function Pagination({ page, onPageChange, disabled = false }: PaginationProps) {
  const start = page.totalItems === 0 ? 0 : page.page * page.size + 1
  const end = Math.min((page.page + 1) * page.size, page.totalItems)
  if (page.totalPages <= 1 && page.totalItems === 0) return null
  return (
    <nav className="pagination-shell d-flex flex-wrap justify-content-between align-items-center gap-3" aria-label="Results pagination">
      <span className="small text-muted">Showing {start}–{end} of {page.totalItems} results</span>
      <div className="d-flex gap-2 align-items-center">
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => onPageChange(page.page - 1)} disabled={disabled || page.first} aria-label="Previous page">Previous</button>
        <span className="small px-2" aria-current="page">Page {page.page + 1} of {Math.max(page.totalPages, 1)}</span>
        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => onPageChange(page.page + 1)} disabled={disabled || page.last} aria-label="Next page">Next</button>
      </div>
    </nav>
  )
}
