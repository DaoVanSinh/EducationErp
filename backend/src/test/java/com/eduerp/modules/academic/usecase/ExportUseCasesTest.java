package com.eduerp.modules.academic.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.GradebookResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Review Focus #7 ở mức đơn vị: export PHẢI đi qua chính usecase đọc, không có đường dữ liệu riêng.
 * Hai khẳng định {@code verify(...)} dưới đây là thứ chặn một lần refactor tương lai âm thầm thay
 * {@code GetClassGradebook} bằng một query riêng "cho nhanh".
 */
class ExportUseCasesTest {

    private final GetClassGradebook getClassGradebook = mock(GetClassGradebook.class);
    private final GetClassProgress getClassProgress = mock(GetClassProgress.class);
    private final ExportClassGradebook exportGradebook = new ExportClassGradebook(getClassGradebook);
    private final ExportClassProgress exportProgress = new ExportClassProgress(getClassProgress);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();

    @Test
    void gradebookExportDelegatesToTheReadUseCaseAndNamesTheFile() {
        when(getClassGradebook.execute(actorAccountId, classId)).thenReturn(
                new GradebookResponse(classId, UUID.randomUUID(), List.of(), List.of()));

        var exported = exportGradebook.execute(actorAccountId, classId);

        verify(getClassGradebook).execute(actorAccountId, classId);
        assertThat(exported.fileName()).isEqualTo(AcademicConstants.Excel.GRADEBOOK_FILE_NAME);
        assertThat(exported.content()).isNotEmpty();
    }

    @Test
    void progressExportDelegatesToTheReadUseCaseAndNamesTheFile() {
        when(getClassProgress.execute(actorAccountId, classId)).thenReturn(
                new ClassProgressResponse(classId, UUID.randomUUID(), 24, List.of()));

        var exported = exportProgress.execute(actorAccountId, classId);

        verify(getClassProgress).execute(actorAccountId, classId);
        assertThat(exported.fileName()).isEqualTo(AcademicConstants.Excel.PROGRESS_FILE_NAME);
        assertThat(exported.content()).isNotEmpty();
    }

    /** Spec mục 7: "không có đường tắt tải Excel bỏ qua kiểm tra quyền" - lỗi của guard (nằm trong
     * usecase đọc) phải nổi nguyên vẹn ra ngoài, không bị bắt rồi trả file rỗng. */
    @Test
    void gradebookExportPropagatesTheAccessRefusalOfTheReadUseCase() {
        when(getClassGradebook.execute(actorAccountId, classId))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> exportGradebook.execute(actorAccountId, classId))
                .isInstanceOf(NotAssignedToClassException.class);
    }

    @Test
    void progressExportPropagatesTheAccessRefusalOfTheReadUseCase() {
        when(getClassProgress.execute(actorAccountId, classId))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> exportProgress.execute(actorAccountId, classId))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
