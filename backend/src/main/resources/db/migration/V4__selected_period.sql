-- Periodo con el que trabaja el usuario (selector global de la app). Como mucho uno por usuario; si
-- no hay ninguno marcado se usa el que contiene hoy o, si no, el más reciente.
alter table work_periods add column selected boolean not null default false;

create unique index ux_work_periods_selected on work_periods (user_id) where selected;
