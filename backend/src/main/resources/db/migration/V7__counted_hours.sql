-- Horario computable de cada periodo: solo cuenta el tiempo trabajado entre count_from y count_until.
-- Se puede fichar antes o después, pero esa parte no suma. Los periodos existentes quedan en 07:00-20:00.
alter table work_periods
    add column count_from  time not null default '07:00',
    add column count_until time not null default '20:00',
    add constraint ck_work_periods_count_window check (count_from < count_until);
