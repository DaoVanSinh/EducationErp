package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboHasInvoicesException;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class CancelComboTest {

    private final ComboRepository combos = mock(ComboRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CancelCombo useCase = new CancelCombo(combos, invoices, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();

    private Combo stubbedCombo() {
        var combo = new Combo(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("21000000"),
                new BigDecimal("15"), LocalDate.of(2027, 1, 31), actorAccountId);
        ReflectionTestUtils.setField(combo, "id", comboId);
        when(combos.findById(comboId)).thenReturn(Optional.of(combo));
        when(invoices.countByComboId(comboId)).thenReturn(0L);
        return combo;
    }

    @Test
    void rejectsAnUnknownCombo() {
        when(combos.findById(comboId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(comboId, actorAccountId, actorBranchId))
                .isInstanceOf(ComboNotFoundException.class);
    }

    @Test
    void hardDeletesAComboThatHasNotIssuedAnyInvoice() {
        var combo = stubbedCombo();

        useCase.execute(comboId, actorAccountId, actorBranchId);

        verify(combos).delete(combo);
    }

    /**
     * Review Focus #6: combo đã phát hành ít nhất một hoá đơn là chứng từ tài chính đã chốt - xoá
     * cứng nó sẽ để lại hoá đơn trỏ về một combo không còn tồn tại, và mất luôn căn cứ của số tiền
     * đã thu.
     */
    @Test
    void refusesToCancelAComboThatAlreadyIssuedAnInvoice() {
        stubbedCombo();
        when(invoices.countByComboId(comboId)).thenReturn(1L);

        assertThatThrownBy(() -> useCase.execute(comboId, actorAccountId, actorBranchId))
                .isInstanceOf(ComboHasInvoicesException.class);

        verify(combos, never()).delete(any());
        verify(events, never()).publishEvent(any());
    }

    /**
     * Review Focus #6, mặt dễ lọt: hoá đơn đã HUỶ vẫn tính. {@code countByComboId} cố ý không lọc
     * trạng thái - một combo từng phát hành rồi huỷ hoá đơn vẫn là combo có lịch sử chứng từ, không
     * phải bản nháp. (Khác hẳn hạn mức 3 đợt, nơi CANCELLED không chiếm chỗ.)
     */
    @Test
    void countsCancelledInvoicesTooWhenDecidingWhetherTheComboMayBeDeleted() {
        stubbedCombo();
        when(invoices.countByComboId(comboId)).thenReturn(2L);

        assertThatThrownBy(() -> useCase.execute(comboId, actorAccountId, actorBranchId))
                .isInstanceOf(ComboHasInvoicesException.class);

        // Không được gọi biến thể lọc trạng thái ở đây - đó là câu hỏi của hạn mức đợt, không phải
        // câu hỏi "combo này đã từng có chứng từ chưa".
        verify(invoices, never()).countByComboIdAndStatusNot(any(), any());
    }

    /**
     * Huỷ combo là XOÁ CỨNG: nếu không phát event thì sau đó không còn dấu vết nào cho thấy combo
     * từng tồn tại. Phải phát TRƯỚC khi xoá, trong cùng transaction (spec mục 6) - listener audit
     * chỉ đọc comboId dạng UUID thuần nên không cần bản ghi còn sống.
     */
    @Test
    void publishesComboCancelledBeforeDeletingTheRow() {
        var combo = stubbedCombo();

        useCase.execute(comboId, actorAccountId, actorBranchId);

        var published = ArgumentCaptor.forClass(BillingEvents.ComboCancelled.class);
        verify(events, times(1)).publishEvent(published.capture());
        assertThat(published.getValue().comboId()).isEqualTo(comboId);
        assertThat(published.getValue().actorAccountId()).isEqualTo(actorAccountId);
        assertThat(published.getValue().actorBranchId()).isEqualTo(actorBranchId);

        var order = inOrder(events, combos);
        order.verify(events).publishEvent(any(BillingEvents.ComboCancelled.class));
        order.verify(combos).delete(combo);
    }
}
