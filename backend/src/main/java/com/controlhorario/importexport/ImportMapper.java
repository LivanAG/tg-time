package com.controlhorario.importexport;

import com.controlhorario.importexport.dto.DetectedSettingsDto;
import com.controlhorario.importexport.dto.ImportBreakDto;
import com.controlhorario.importexport.dto.ImportSheetDto;
import com.controlhorario.importexport.excel.DetectedSettings;
import com.controlhorario.importexport.excel.ParsedSheet;
import com.controlhorario.workday.calc.BreakInput;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Datos leídos del Excel → DTOs de la respuesta. */
@Mapper
public interface ImportMapper {

    DetectedSettingsDto toDto(DetectedSettings settings);

    ImportSheetDto toDto(ParsedSheet sheet);

    @Mapping(target = "startTime", source = "start")
    @Mapping(target = "endTime", source = "end")
    ImportBreakDto toDto(BreakInput breakInput);
}
