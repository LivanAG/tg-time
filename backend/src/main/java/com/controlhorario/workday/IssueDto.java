package com.controlhorario.workday;

/**
 * Aviso de cálculo (no impide guardar): {@code LUNCH_BELOW_MINIMUM}, {@code MISSING_RECORD},
 * {@code JIRA_IZERTIA_MISMATCH}, {@code REMOTE_PCT_EXCEEDED}...
 *
 * @param field campo afectado o null
 */
public record IssueDto(String code, String field, String message) {
}
