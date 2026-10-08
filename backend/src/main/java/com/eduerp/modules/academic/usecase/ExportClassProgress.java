package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ExportedFileResponse;
import com.eduerp.modules.academic.internal.excel.AcademicWorkbooks;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bảng tiến trình học tập dưới dạng {@code .xlsx} (spec mục 7) - cùng lý do uỷ quyền hoàn toàn cho
 * usecase đọc như {@link ExportClassGradebook}. */
@Service
public class ExportClassProgress {

    private final GetClassProgress getClassProgress;

    ExportClassProgress(GetClassProgress getClassProgress) {
        this.getClassProgress = getClassProgress;
    }

    @Transactional(readOnly = true)
    public ExportedFileResponse execute(UUID actorAccountId, UUID classId) {
        var progress = getClassProgress.execute(actorAccountId, classId);
        return new ExportedFileResponse(AcademicConstants.Excel.PROGRESS_FILE_NAME,
                AcademicWorkbooks.progress(progress));
    }
}
