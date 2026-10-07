-- Fuera las imputaciones JIRA/IZERTIA (no se usan) y el máximo de días de teletrabajo al mes:
-- el límite de teletrabajo es solo el porcentaje (max_remote_pct).
alter table workdays drop column jira_minutes;
alter table workdays drop column izertia_minutes;

alter table work_periods drop column max_remote_days_month;
