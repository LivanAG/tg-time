package com.controlhorario.importexport.excel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Utilidades de tests para construir y manipular ficheros .xlsx. */
public final class TestWorkbooks {

    public static final String REAL_EXCEL = "/excel/HORAS_IZERTIS_2026-27.xlsx";

    private TestWorkbooks() {
    }

    public static byte[] realExcel() {
        try (InputStream in = TestWorkbooks.class.getResourceAsStream(REAL_EXCEL)) {
            return Objects.requireNonNull(in).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] bytes(XSSFWorkbook workbook) {
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Añade una entrada al zip sin declararla en [Content_Types].xml. */
    public static byte[] withEntry(byte[] xlsx, String name, byte[] data) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(xlsx));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    out.putNextEntry(new ZipEntry(entry.getName()));
                    out.write(in.readAllBytes());
                    out.closeEntry();
                }
                out.putNextEntry(new ZipEntry(name));
                out.write(data);
                out.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Añade un proyecto VBA (xl/vbaProject.bin declarado en [Content_Types].xml), como un .xlsm renombrado. */
    public static byte[] withVbaProject(byte[] xlsx) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(xlsx));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    byte[] content = in.readAllBytes();
                    if (entry.getName().equals("[Content_Types].xml")) {
                        content = new String(content, StandardCharsets.UTF_8)
                                .replace("</Types>", "<Override PartName=\"/xl/vbaProject.bin\" "
                                        + "ContentType=\"application/vnd.ms-office.vbaProject\"/></Types>")
                                .getBytes(StandardCharsets.UTF_8);
                    }
                    out.putNextEntry(new ZipEntry(entry.getName()));
                    out.write(content);
                    out.closeEntry();
                }
                out.putNextEntry(new ZipEntry("xl/vbaProject.bin"));
                out.write(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1, 2, 3, 4});
                out.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
