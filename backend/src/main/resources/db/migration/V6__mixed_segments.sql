-- Día MIXTO: en vez de "minutos en casa", dos tramos con su entrada y su salida (oficina y casa, en
-- cualquier orden). start_time/end_time pasan a ser la primera entrada y la última salida; el hueco
-- entre los tramos no se trabaja.
alter table workdays
    add column office_start time,
    add column office_end   time,
    add column home_start   time,
    add column home_end     time;

-- Mixtos ya guardados: el tramo en casa era el final del día ("teletrabajo tardes", como la columna N/O
-- del Excel), sin hueco. Si los minutos en casa no dejaban tramo de oficina o de casa, el día pasa a ser
-- de CASA o de OFICINA.
update workdays
   set office_start = start_time,
       office_end   = end_time - make_interval(mins => remote_minutes),
       home_start   = end_time - make_interval(mins => remote_minutes),
       home_end     = end_time
 where location = 'MIXTO'
   and remote_minutes > 0
   and remote_minutes < extract(epoch from (end_time - start_time)) / 60;
update workdays
   set location = 'CASA'
 where location = 'MIXTO' and office_start is null and remote_minutes > 0;
update workdays
   set location = 'OFICINA'
 where location = 'MIXTO' and office_start is null;

alter table workdays drop column remote_minutes;

alter table workdays add constraint ck_workdays_mixed check (
    (location = 'MIXTO' and office_start is not null and office_end is not null
        and home_start is not null and home_end is not null
        and office_start < office_end and home_start < home_end)
    or (location <> 'MIXTO' and office_start is null and office_end is null
        and home_start is null and home_end is null));
