import type { IssueDto } from '../api/types'

/** Lista de avisos de cálculo (IssueDto). */
export function IssueList({ issues, className = '' }: { issues: IssueDto[]; className?: string }) {
  if (issues.length === 0) {
    return null
  }
  return (
    <ul className={`space-y-1 ${className}`} aria-label="Avisos">
      {issues.map((issue, index) => (
        <li key={`${issue.code}-${index}`} className="flex gap-2 text-sm text-amber-900">
          <span aria-hidden="true">⚠</span>
          <span>{issue.message}</span>
        </li>
      ))}
    </ul>
  )
}
