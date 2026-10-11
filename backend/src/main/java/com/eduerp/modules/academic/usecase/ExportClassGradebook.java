package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ExportedFileResponse;
import com.eduerp.modules.academic.internal.excel.AcademicWorkbooks;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảng điểm dưới dạng {@code .xlsx} (spec mục 7). KHÔNG đọc dữ liệu riêng: gọi thẳng
 * {@link GetClassGradebook} rồi serialize, nên (a) số trên web và số trong file không thể lệch nhau
 * (spec mục 11.7) và (b) việc kiểm quyền "chỉ giáo viên được phân công" đi kèm miễn phí vì guard
 * nằm trong usecase đọc - không có đường tắt tải Excel bỏ qua kiểm tra quyền (spec mục 7).
 */
@Service
public class ExportClassGradebook {

    private final GetClassGradebook getClassGradebook;

    ExportClassGradebook(GetClassGradebook getClassGradebook) {
        this.getClassGradebook = getClassGradebook;
    }

    @Transactional(readOnly = true)
    public ExportedFileResponse execute(UUID actorAccountId, UUID classId) {
        var gradebook = getClassGradebook.execute(actorAccountId, classId);
        return new ExportedFileResponse(AcademicConstants.Excel.GRADEBOOK_FILE_NAME,
                AcademicWorkbooks.gradebook(gradebook));
    }
}
