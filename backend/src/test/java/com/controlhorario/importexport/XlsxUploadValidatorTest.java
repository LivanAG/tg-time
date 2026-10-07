package com.controlhorario.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.controlhorario.common.web.FieldErrorDto;
import com.controlhorario.common.web.ValidationException;
import com.controlhorario.importexport.excel.TestWorkbooks;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class XlsxUploadValidatorTest {

    private final XlsxUploadValidator validator = new XlsxUploadValidator();

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    @Test
    void acceptsTheRealExcel() {
        byte[] content = TestWorkbooks.realExcel();
        assertThat(validator.validate(file("HORAS.xlsx", XlsxUploadValidator.XLSX_CONTENT_TYPE, content)))
                .isEqualTo(content);
        assertThat(validator.validate(file("horas.XLSX", "application/octet-stream", content))).isEqualTo(content);
    }

    @Test
    void rejectsMissingOrEmptyFiles() {
        assertError(() -> validator.validate(null), "Selecciona un fichero .xlsx");
        assertError(() -> validator.validate(file("a.xlsx", XlsxUploadValidator.XLSX_CONTENT_TYPE, new byte[0])),
                "Selecciona un fichero .xlsx");
    }

    @Test
    void rejectsOtherExtensionsAndContentTypes() {
        byte[] content = TestWorkbooks.realExcel();
        assertError(() -> validator.validate(file("horas.xlsm", XlsxUploadValidator.XLSX_CONTENT_TYPE, content)),
                "El fichero debe tener la extensión .xlsx");
        assertError(() -> validator.validate(file("horas.xlsx", "text/plain", content)),
                "El tipo de fichero no corresponde a un Excel .xlsx");
        assertError(() -> validator.validate(file("horas.xlsx", null, content)),
                "El tipo de fichero no corresponde a un Excel .xlsx");
        assertError(() -> validator.validate(file("horas.xlsx", "application/vnd.ms-excel.sheet.macroEnabled.12",
                content)), "El tipo de fichero no corresponde a un Excel .xlsx");
    }

    @Test
    void rejectsContentWithoutTheZipSignature() {
        byte[] text = "ENTRADA;SALIDA\n07:25;17:59\n".getBytes(StandardCharsets.UTF_8);
        assertError(() -> validator.validate(file("horas.xlsx", XlsxUploadValidator.XLSX_CONTENT_TYPE, text)),
                "El fichero no es un Excel .xlsx válido");
    }

    @Test
    void filesOverTwoMegabytesAreTooLarge() {
        byte[] big = new byte[(int) XlsxUploadValidator.MAX_BYTES + 1];
        assertThatThrownBy(() -> validator.validate(file("horas.xlsx", XlsxUploadValidator.XLSX_CONTENT_TYPE, big)))
                .isInstanceOf(MaxUploadSizeExceededException.class);
    }

    @Test
    void contentTypeParametersAreIgnored() {
        assertThat(XlsxUploadValidator.isAllowedContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; charset=binary")).isTrue();
        assertThat(XlsxUploadValidator.isAllowedContentType("no es un tipo")).isFalse();
    }

    @Test
    void displayNameDropsPathsAndControlCharacters() {
        assertThat(XlsxUploadValidator.displayName("C:\\Users\\livan\\HORAS.xlsx")).isEqualTo("HORAS.xlsx");
        assertThat(XlsxUploadValidator.displayName("../../etc/HORAS\u0000.xlsx")).isEqualTo("HORAS.xlsx");
        assertThat(XlsxUploadValidator.displayName(null)).isNull();
    }

    private static void assertError(ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ValidationException.class, e ->
                assertThat(e.getErrors()).containsExactly(new FieldErrorDto("file", message)));
    }
}
