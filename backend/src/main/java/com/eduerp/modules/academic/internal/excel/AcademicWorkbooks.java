package com.eduerp.modules.academic.internal.excel;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.GradebookResponse;
import com.eduerp.modules.academic.dto.StudentGradesRow;
import com.eduerp.modules.academic.dto.StudentProgressRow;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Serialize hai báo cáo của spec mục 7 thành {@code .xlsx}. Chỉ BIẾN ĐỔI ĐỊNH DẠNG: mọi con số đi
 * vào đây đã được {@code GetClassGradebook}/{@code GetClassProgress} tính xong, class này không
 * cộng, chia hay làm tròn gì - đó là cách duy nhất bảo đảm số trên web khớp số trong file
 * (spec mục 11.7).
 *
 * <p>Ô trống không in "null": {@code finalScore} null nghĩa là "chưa đủ điểm" và
 * {@code progressPercent} null nghĩa là "chưa cấu hình tổng số buổi chuẩn" - hai câu đó phải đọc
 * được bằng tiếng Việt trong file, không phải suy từ một ô rỗng.
 */
public final class AcademicWorkbooks {

    private AcademicWorkbooks() {
    }

    /** Bảng điểm: Học viên | &lt;từng cột điểm theo displayOrder&gt; | Điểm tổng kết. */
    public static byte[] gradebook(GradebookResponse gradebook) {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(AcademicConstants.Excel.GRADEBOOK_SHEET_NAME);
            writeGradebookHeader(sheet, gradebook.columns());
            for (int index = 0; index < gradebook.rows().size(); index++) {
                writeGradebookRow(sheet.createRow(index + 1), gradebook.rows().get(index),
                        gradebook.columns());
            }
            autoSize(sheet, gradebook.columns().size() + 2);
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bảng tiến trình: Học viên | Trạng thái | Ngày ghi danh | Số buổi đã học | Tổng số buổi chuẩn | %. */
    public static byte[] progress(ClassProgressResponse progress) {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(AcademicConstants.Excel.PROGRESS_SHEET_NAME);
            writeRowOfTexts(sheet.createRow(0), List.of(
                    AcademicConstants.Excel.HEADER_STUDENT_NAME,
                    AcademicConstants.Excel.HEADER_STATUS,
                    AcademicConstants.Excel.HEADER_ENROLLED_AT,
                    AcademicConstants.Excel.HEADER_ELAPSED_SESSIONS,
                    AcademicConstants.Excel.HEADER_STANDARD_SESSIONS,
                    AcademicConstants.Excel.HEADER_PROGRESS_PERCENT));
            for (int index = 0; index < progress.rows().size(); index++) {
                writeProgressRow(sheet.createRow(index + 1), progress.rows().get(index));
            }
            autoSize(sheet, 6);
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeGradebookHeader(Sheet sheet, List<GradeColumnResponse> columns) {
        var headers = new java.util.ArrayList<String>();
        headers.add(AcademicConstants.Excel.HEADER_STUDENT_NAME);
        columns.forEach(column -> headers.add(column.name()));
        headers.add(AcademicConstants.Excel.HEADER_FINAL_SCORE);
        writeRowOfTexts(sheet.createRow(0), headers);
    }

    private static void writeGradebookRow(Row row, StudentGradesRow data,
            List<GradeColumnResponse> columns) {
        setText(row, 0, data.studentName());
        for (int index = 0; index < columns.size(); index++) {
            setNumberOrBlank(row, index + 1, data.scoresByColumnId().get(columns.get(index).id()));
        }
        int finalScoreAt = columns.size() + 1;
        if (data.finalScore() == null) {
            setText(row, finalScoreAt, AcademicConstants.Excel.CELL_NOT_ENOUGH_SCORES);
            return;
        }
        setNumberOrBlank(row, finalScoreAt, data.finalScore());
    }

    private static void writeProgressRow(Row row, StudentProgressRow data) {
        setText(row, 0, data.studentName());
        setText(row, 1, data.status().name());
        setText(row, 2, data.enrolledAt().toString());
        setNumberOrBlank(row, 3, BigDecimal.valueOf(data.elapsedSessions()));
        setNumberOrBlank(row, 4, data.standardSessionCount() == null ? null
                : BigDecimal.valueOf(data.standardSessionCount()));
        if (data.progressPercent() == null) {
            setText(row, 5, AcademicConstants.Excel.CELL_SESSION_COUNT_NOT_CONFIGURED);
            return;
        }
        setNumberOrBlank(row, 5, BigDecimal.valueOf(data.progressPercent()));
    }

    private static void writeRowOfTexts(Row row, List<String> texts) {
        for (int index = 0; index < texts.size(); index++) {
            setText(row, index, texts.get(index));
        }
    }

    /** {@code null} thành ô chuỗi RỖNG, không bao giờ thành chữ "null". */
    private static void setText(Row row, int at, String value) {
        row.createCell(at).setCellValue(value == null ? "" : value);
    }

    private static void setNumberOrBlank(Row row, int at, BigDecimal value) {
        var cell = row.createCell(at);
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        cell.setCellValue(value.doubleValue());
    }

    private static void autoSize(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private static byte[] toBytes(Workbook workbook) throws IOException {
        try (var out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
