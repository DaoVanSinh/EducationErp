package com.eduerp.modules.academic.internal.excel;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.GradebookResponse;
import com.eduerp.modules.academic.dto.StudentGradesRow;
import com.eduerp.modules.academic.dto.StudentProgressRow;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Đọc lại chính file vừa sinh bằng POI: khẳng định trên nội dung thật, không phải trên độ dài byte. */
class AcademicWorkbooksTest {

    private final UUID midtermId = UUID.randomUUID();
    private final UUID finalId = UUID.randomUUID();

    private final List<GradeColumnResponse> columns = List.of(
            new GradeColumnResponse(midtermId, "Giữa kỳ", new BigDecimal("40.00"),
                    new BigDecimal("10.00"), 0),
            new GradeColumnResponse(finalId, "Cuối kỳ", new BigDecimal("60.00"),
                    new BigDecimal("10.00"), 1));

    /** Mỗi helper tự mở rồi đóng workbook và chỉ trả về GIÁ TRỊ đã đọc: trả về một {@code Sheet}
     * sau khi {@code XSSFWorkbook} đã đóng là đọc trên object đã giải phóng. */
    private static List<String> cellTextsOf(byte[] content, int rowIndex) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Row row = workbook.getSheetAt(0).getRow(rowIndex);
            var texts = new java.util.ArrayList<String>();
            for (int cell = 0; cell < row.getLastCellNum(); cell++) {
                var current = row.getCell(cell);
                texts.add(current == null ? "" : switch (current.getCellType()) {
                    case STRING -> current.getStringCellValue();
                    case NUMERIC -> new BigDecimal(String.valueOf(current.getNumericCellValue()))
                            .stripTrailingZeros().toPlainString();
                    default -> "";
                });
            }
            return texts;
        }
    }

    private static String sheetNameOf(byte[] content) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            return workbook.getSheetName(0);
        }
    }

    private GradebookResponse gradebookWith(StudentGradesRow... rows) {
        return new GradebookResponse(UUID.randomUUID(), UUID.randomUUID(), columns, List.of(rows));
    }

    @Test
    void writesTheGradebookHeaderFromTheConfiguredColumnsInOrder() throws Exception {
        var content = AcademicWorkbooks.gradebook(gradebookWith());

        assertThat(sheetNameOf(content)).isEqualTo(AcademicConstants.Excel.GRADEBOOK_SHEET_NAME);
        assertThat(cellTextsOf(content, 0)).containsExactly(
                AcademicConstants.Excel.HEADER_STUDENT_NAME, "Giữa kỳ", "Cuối kỳ",
                AcademicConstants.Excel.HEADER_FINAL_SCORE);
    }

    @Test
    void writesOneRowPerStudentWithScoresUnderTheMatchingColumn() throws Exception {
        var content = AcademicWorkbooks.gradebook(gradebookWith(new StudentGradesRow(
                UUID.randomUUID(), UUID.randomUUID(), "Nguyễn Văn A",
                EnrollmentConstants.EnrollmentStatus.ACTIVE,
                Map.of(midtermId, new BigDecimal("8.00"), finalId, new BigDecimal("9.00")),
                new BigDecimal("86.00"))));

        assertThat(cellTextsOf(content, 1))
                .containsExactly("Nguyễn Văn A", "8", "9", "86");
    }

    /** Review Focus #3 trong file Excel: ô điểm tổng kết KHÔNG được in "null" hay 0 - nó phải nói
     * thành chữ rằng chưa đủ điểm, và ô cột thiếu điểm để trống. */
    @Test
    void writesAReadableLabelInsteadOfNullForAMissingFinalScore() throws Exception {
        var content = AcademicWorkbooks.gradebook(gradebookWith(new StudentGradesRow(
                UUID.randomUUID(), UUID.randomUUID(), "Trần Thị B",
                EnrollmentConstants.EnrollmentStatus.ACTIVE,
                Map.of(midtermId, new BigDecimal("8.00")), null)));

        assertThat(cellTextsOf(content, 1)).containsExactly("Trần Thị B", "8", "",
                AcademicConstants.Excel.CELL_NOT_ENOUGH_SCORES);
    }

    /** Học viên không tra được tên vẫn phải có một dòng, ô tên để trống chứ không in "null". */
    @Test
    void writesAnEmptyNameCellWhenTheStudentNameIsUnknown() throws Exception {
        var content = AcademicWorkbooks.gradebook(gradebookWith(new StudentGradesRow(
                UUID.randomUUID(), UUID.randomUUID(), null,
                EnrollmentConstants.EnrollmentStatus.ACTIVE, Map.of(), null)));

        assertThat(cellTextsOf(content, 1).get(0)).isEmpty();
    }

    /** Review Focus #9: khóa chưa cấu hình cột điểm - file vẫn mở được, chỉ có hai cột khung. */
    @Test
    void writesAGradebookWithoutAnyConfiguredColumn() throws Exception {
        var content = AcademicWorkbooks.gradebook(new GradebookResponse(UUID.randomUUID(),
                UUID.randomUUID(), List.of(), List.of()));

        assertThat(cellTextsOf(content, 0)).containsExactly(
                AcademicConstants.Excel.HEADER_STUDENT_NAME,
                AcademicConstants.Excel.HEADER_FINAL_SCORE);
    }

    @Test
    void writesTheProgressHeaderAndRow() throws Exception {
        var content = AcademicWorkbooks.progress(new ClassProgressResponse(UUID.randomUUID(),
                UUID.randomUUID(), 24, List.of(new StudentProgressRow(UUID.randomUUID(),
                        UUID.randomUUID(), "Nguyễn Văn A",
                        EnrollmentConstants.EnrollmentStatus.ACTIVE,
                        Instant.parse("2026-01-05T01:00:00Z"), null, 6, 24, 25))));

        assertThat(sheetNameOf(content)).isEqualTo(AcademicConstants.Excel.PROGRESS_SHEET_NAME);
        assertThat(cellTextsOf(content, 0)).containsExactly(
                AcademicConstants.Excel.HEADER_STUDENT_NAME, AcademicConstants.Excel.HEADER_STATUS,
                AcademicConstants.Excel.HEADER_ENROLLED_AT,
                AcademicConstants.Excel.HEADER_ELAPSED_SESSIONS,
                AcademicConstants.Excel.HEADER_STANDARD_SESSIONS,
                AcademicConstants.Excel.HEADER_PROGRESS_PERCENT);
        var row = cellTextsOf(content, 1);
        assertThat(row.get(0)).isEqualTo("Nguyễn Văn A");
        assertThat(row.get(1)).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE.name());
        assertThat(row.get(3)).isEqualTo("6");
        assertThat(row.get(4)).isEqualTo("24");
        assertThat(row.get(5)).isEqualTo("25");
    }

    /** Review Focus #8 trong file Excel: chưa cấu hình tổng số buổi chuẩn thì ô % phải nói thành chữ,
     * không in 0 (người đọc sẽ hiểu sai là học viên chưa học buổi nào). */
    @Test
    void writesAReadableLabelInsteadOfZeroWhenTheSessionCountIsNotConfigured() throws Exception {
        var content = AcademicWorkbooks.progress(new ClassProgressResponse(UUID.randomUUID(),
                UUID.randomUUID(), null, List.of(new StudentProgressRow(UUID.randomUUID(),
                        UUID.randomUUID(), "Nguyễn Văn A",
                        EnrollmentConstants.EnrollmentStatus.ACTIVE,
                        Instant.parse("2026-01-05T01:00:00Z"), null, 6, null, null))));

        var row = cellTextsOf(content, 1);
        assertThat(row.get(3)).as("số buổi đã học vẫn là số thật").isEqualTo("6");
        assertThat(row.get(4)).isEmpty();
        assertThat(row.get(5)).isEqualTo(AcademicConstants.Excel.CELL_SESSION_COUNT_NOT_CONFIGURED);
    }

    @Test
    void writesAProgressSheetForAClassWithoutAnyStudent() throws Exception {
        var content = AcademicWorkbooks.progress(new ClassProgressResponse(UUID.randomUUID(),
                UUID.randomUUID(), 24, List.of()));

        assertThat(content).isNotEmpty();
        assertThat(cellTextsOf(content, 0)).hasSize(6);
    }
}
