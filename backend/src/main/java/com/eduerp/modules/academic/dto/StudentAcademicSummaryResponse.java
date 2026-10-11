package com.eduerp.modules.academic.dto;

import java.util.List;
import java.util.UUID;

/** Danh sách rỗng khi học viên chưa ghi danh lớp nào, HOẶC khi người xem (giáo viên) không được
 * phép thấy lớp nào của học viên này - hai tình huống khác nhau nhưng cùng một hình dạng phản hồi
 * (spec hiện có của {@code AcademicConstants} chưa có điều này, mirror triết lý "rỗng không phải
 * lỗi" của {@code getGradeColumns}). */
public record StudentAcademicSummaryResponse(UUID studentProfileId, List<StudentClassSummaryRow> classes) {
}
