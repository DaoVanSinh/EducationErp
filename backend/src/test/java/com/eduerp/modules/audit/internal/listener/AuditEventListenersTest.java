package com.eduerp.modules.audit.internal.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eduerp.modules.academic.AcademicEvents;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.internal.model.AuditLog;
import com.eduerp.modules.audit.internal.repository.AuditLogRepository;
import com.eduerp.modules.billing.BillingEvents;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditEventListenersTest {

    private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    private final AuditEventListeners listeners = new AuditEventListeners(auditLogs);

    private final UUID comboId = UUID.randomUUID();
    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private AuditLog captureSavedLog() {
        var saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void logsComboCreationAgainstTheComboEntityType() {
        listeners.on(new BillingEvents.ComboCreated(comboId, actorAccountId, actorBranchId));

        var log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AuditConstants.Actions.COMBO_CREATE);
        assertThat(log.getEntityType()).isEqualTo(AuditConstants.EntityTypes.COMBO);
        assertThat(log.getEntityId()).isEqualTo(comboId.toString());
        assertThat(log.getActorAccountId()).isEqualTo(actorAccountId);
        assertThat(log.getBranchId()).isEqualTo(actorBranchId);
    }

    /** Huỷ combo là XOÁ CỨNG - dòng audit này là dấu vết duy nhất còn lại cho thấy combo từng tồn
     * tại, nên nó phải mang đúng comboId đã bị xoá. */
    @Test
    void logsComboCancellationWithTheIdOfTheDeletedCombo() {
        listeners.on(new BillingEvents.ComboCancelled(comboId, actorAccountId, actorBranchId));

        var log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AuditConstants.Actions.COMBO_CANCEL);
        assertThat(log.getEntityType()).isEqualTo(AuditConstants.EntityTypes.COMBO);
        assertThat(log.getEntityId()).isEqualTo(comboId.toString());
    }

    /**
     * Spec mục 9. {@code entityId} là {@code enrollmentId} chứ không phải id của dòng
     * {@code GradeEntry}: sự kiện không mang id đó (upsert nên nó chưa chắc tồn tại lúc publish), và
     * câu hỏi mà người đọc nhật ký đặt ra luôn là "điểm của lượt ghi danh nào vừa bị sửa".
     * {@code courseGradeColumnId} có trong sự kiện nhưng {@code audit_logs} chỉ có một cột
     * {@code entity_id} - không nhồi hai id vào một cột.
     */
    @Test
    void logsAGradeRecordAgainstTheEnrolmentItBelongsTo() {
        var enrollmentId = UUID.randomUUID();

        listeners.on(new AcademicEvents.GradeRecorded(enrollmentId, UUID.randomUUID(), actorAccountId,
                actorBranchId));

        var log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AuditConstants.Actions.GRADE_RECORD);
        assertThat(log.getEntityType()).isEqualTo(AuditConstants.EntityTypes.GRADE_ENTRY);
        assertThat(log.getEntityId()).isEqualTo(enrollmentId.toString());
        assertThat(log.getActorAccountId()).isEqualTo(actorAccountId);
        assertThat(log.getBranchId()).isEqualTo(actorBranchId);
    }
}
