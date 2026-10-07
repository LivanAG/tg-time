package com.controlhorario.absence;

import java.util.List;

import org.mapstruct.Mapper;

/** Entidad → DTO de ausencias. */
@Mapper
public interface AbsenceMapper {

    AbsenceDto toDto(Absence absence);

    List<AbsenceDto> toDtos(List<Absence> absences);
}
