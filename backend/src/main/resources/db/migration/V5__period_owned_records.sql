-- Cada periodo tiene sus propios fichajes y ausencias, y los periodos pueden solaparse (p. ej. uno de
-- pruebas junto al real). Hasta ahora no se solapaban: cada registro pasa al periodo que contiene su
-- fecha. Los que no estaban en ningún periodo (de periodos ya borrados) no se veían en ninguna
-- pantalla y se descartan. Al borrar un periodo se borran sus fichajes y ausencias.
alter table work_periods drop constraint ex_work_periods_overlap;

-- ---------------------------------------------------------------- fichajes
alter table workdays add column period_id uuid references work_periods (id) on delete cascade;
update workdays w
   set period_id = p.id
  from work_periods p
 where p.user_id = w.user_id and w.date between p.start_date and p.end_date;
delete from workdays where period_id is null;
alter table workdays alter column period_id set not null;
alter table workdays drop constraint ux_workdays_user_date;
alter table workdays add constraint ux_workdays_period_date unique (period_id, date);
create index ix_workdays_user on workdays (user_id);

-- ---------------------------------------------------------------- ausencias
alter table absences add column period_id uuid references work_periods (id) on delete cascade;
update absences a
   set period_id = p.id
  from work_periods p
 where p.user_id = a.user_id and a.date between p.start_date and p.end_date;
delete from absences where period_id is null;
alter table absences alter column period_id set not null;
alter table absences drop constraint ux_absences_user_date;
alter table absences add constraint ux_absences_period_date unique (period_id, date);
create index ix_absences_user on absences (user_id);
