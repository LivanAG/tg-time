package com.controlhorario.workday;

import java.util.List;

import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.workday.calc.WorkdayResult;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Entidad (+ totales calculados) → DTO de fichajes. */
@Mapper
public interface WorkdayMapper {

    @Mapping(target = "date", source = "workday.date")
    @Mapping(target = "startTime", source = "workday.startTime")
    @Mapping(target = "endTime", source = "workday.endTime")
    @Mapping(target = "breaks", source = "workday.breaks")
    @Mapping(target = "location", source = "workday.location")
    @Mapping(target = "officeStart", source = "workday.officeStart")
    @Mapping(target = "officeEnd", source = "workday.officeEnd")
    @Mapping(target = "homeStart", source = "workday.homeStart")
    @Mapping(target = "homeEnd", source = "workday.homeEnd")
    @Mapping(target = "notes", source = "workday.notes")
    @Mapping(target = "version", source = "workday.version")
    @Mapping(target = "totals", source = "result")
    @Mapping(target = "warnings", source = "result.warnings")
    WorkdayDto toDto(Workday workday, WorkdayResult result);

    BreakDto toDto(WorkdayBreak workdayBreak);

    WorkdayTotalsDto toTotals(WorkdayResult result);

    IssueDto toIssue(CalcIssue issue);

    List<IssueDto> toIssues(List<CalcIssue> issues);
}
