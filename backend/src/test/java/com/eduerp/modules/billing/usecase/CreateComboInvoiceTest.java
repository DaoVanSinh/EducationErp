package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.InstallmentLimitExceededException;
import com.eduerp.modules.billing.InvoiceAmountExceedsTuitionException;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class CreateComboInvoiceTest {

    /** 12.000.000 + 9.000.000 = 21.000.000, giảm 15% → 17.850.000. */
    private static final BigDecimal DISCOUNTED_TOTAL = new BigDecimal("17850000");
    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ComboRepository combos = mock(ComboRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateComboInvoice useCase = new CreateComboInvoice(invoices, combos, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID comboBranchId = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();

    private CreateComboInvoiceRequest request(String amount) {
        return new CreateComboInvoiceRequest(comboId, new BigDecimal(amount), DUE_DATE);
    }

    /** Combo lưu thật mới có id; trong unit test gán id bằng reflection để không phải dựng DB. */
    private Combo stubbedCombo() {
        var combo = new Combo(studentProfileId, comboBranchId, new BigDecimal("21000000"),
                new BigDecimal("15"), DUE_DATE, actorAccountId);
        ReflectionTestUtils.setField(combo, "id", comboId);
        when(combos.findById(comboId)).thenReturn(Optional.of(combo));
        return combo;
    }

    private Invoice existingComboInvoice(int installmentNumber, String amount) {
        return Invoice.forCombo(comboId, studentProfileId, comboBranchId, installmentNumber,
                new BigDecimal(amount), DUE_DATE, actorAccountId);
    }

    private void stubExistingInvoices(List<Invoice> existing) {
        when(invoices.countByComboIdAndStatusNot(comboId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn((long) existing.size());
        when(invoices.findAllByComboIdAndStatusNot(comboId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn(existing);
    }

    private void stubSaveEchoesBack() {
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAnUnknownCombo() {
        when(combos.findById(comboId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("5000000")))
                .isInstanceOf(ComboNotFoundException.class);
    }

    /** Hoá đơn combo không neo vào ghi danh/khoá nào; học viên và chi nhánh lấy từ chính Combo. */
    @Test
    void issuesTheFirstInstallmentAgainstTheComboAndPublishesInvoiceCreated() {
        stubbedCombo();
        stubExistingInvoices(List.of());
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request("10000000"));

        assertThat(response.installmentNumber()).isEqualTo(1);
        assertThat(response.comboId()).isEqualTo(comboId);
        assertThat(response.enrollmentId()).isNull();
        assertThat(response.courseId()).isNull();
        assertThat(response.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(response.branchId()).isEqualTo(comboBranchId);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
        assertThat(response.dueDate()).isEqualTo(DUE_DATE);
        verify(events).publishEvent(any(BillingEvents.InvoiceCreated.class));
    }

    /**
     * Final review Important: đánh số theo ĐẾM SỐ ĐỢT CÒN SỐNG, không theo số thứ tự còn trống, nên
     * khi huỷ một đợt KHÔNG PHẢI đợt cuối, đợt mới tính ra đúng con số mà một đợt sống khác đang giữ -
     * INSERT va UNIQUE (combo_id, installment_number), và usecase dịch nhầm thành "đã đủ 3 đợt" dù
     * mới có 1 đợt sống và còn thừa ngân sách. Phải tìm số NHỎ NHẤT còn trống trong [1, MAX], không
     * phải count+1.
     */
    @Test
    void reusesTheFreedInstallmentNumberAfterCancellingANonLastInstallment() {
        stubbedCombo();
        // Đợt 1 đã huỷ (không còn trong danh sách "sống"), đợt 2 vẫn sống - đúng mô phỏng
        // countByComboIdAndStatusNot/findAllByComboIdAndStatusNot đã loại CANCELLED.
        stubExistingInvoices(List.of(existingComboInvoice(2, "5000000")));
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request("8900000"));

        assertThat(response.installmentNumber()).isEqualTo(1);
    }

    @Test
    void numbersTheSecondInstallmentTwo() {
        stubbedCombo();
        stubExistingInvoices(List.of(existingComboInvoice(1, "10000000")));
        stubSaveEchoesBack();

        assertThat(useCase.execute(actorAccountId, actorBranchId, request("5000000")).installmentNumber())
                .isEqualTo(2);
    }

    /**
     * Review Focus #5: mốc so sánh là {@code totalDiscountedAmount} (17.850.000), KHÔNG phải tổng
     * gốc 21.000.000 - nếu so nhầm với tổng gốc thì học viên bị thu lại đúng phần vừa được giảm.
     * Và lỗi phải là INVOICE_AMOUNT_EXCEEDS_TUITION, không phải INSTALLMENT_LIMIT_EXCEEDED: mới có
     * 2 đợt, hạn mức 3 chưa chạm.
     */
    @Test
    void rejectsAnInstallmentThatPushesTheTotalPastTheDiscountedAmount() {
        stubbedCombo();
        stubExistingInvoices(List.of(existingComboInvoice(1, "10000000"),
                existingComboInvoice(2, "7850000")));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("1")))
                .isInstanceOf(InvoiceAmountExceedsTuitionException.class)
                .hasMessageContaining("17850000");
        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("3000000")))
                .isInstanceOf(InvoiceAmountExceedsTuitionException.class);
    }

    /** Ranh giới: tổng đúng BẰNG tổng sau giảm vẫn phát hành được, chỉ vượt mới bị chặn. */
    @Test
    void acceptsAnInstallmentThatExactlyCompletesTheDiscountedTotal() {
        stubbedCombo();
        stubExistingInvoices(List.of(existingComboInvoice(1, "10000000")));
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request("7850000"));

        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("7850000"));
        assertThat(response.installmentNumber()).isEqualTo(2);
    }

    /** Hạn mức 3 đợt là lỗi RIÊNG, chỉ gặp khi tiền còn chỗ mà số đợt đã hết - 3 đợt nhỏ chưa dùng
     * hết tổng combo. Lẫn hai lỗi này là báo sai nguyên nhân cho kế toán (Review Focus #5). */
    @Test
    void rejectsAFourthInstallmentEvenWhenTheComboBudgetRemains() {
        stubbedCombo();
        stubExistingInvoices(List.of(existingComboInvoice(1, "1000000"), existingComboInvoice(2, "1000000"),
                existingComboInvoice(3, "1000000")));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("1000000")))
                .isInstanceOf(InstallmentLimitExceededException.class)
                .hasMessageContaining("Combo")
                .hasMessageContaining("3");
    }

    /** Đợt đã huỷ không chiếm chỗ trong 3 đợt và không tính vào tổng - mirror CreateInvoice. */
    @Test
    void ignoresCancelledComboInvoicesInBothTheCountAndTheSum() {
        stubbedCombo();
        // Repository đã loại CANCELLED, nên usecase chỉ thấy 1 hoá đơn còn sống.
        stubExistingInvoices(List.of(existingComboInvoice(1, "10000000")));
        stubSaveEchoesBack();

        assertThat(useCase.execute(actorAccountId, actorBranchId, request("7850000")).installmentNumber())
                .isEqualTo(2);
    }

    /** Mirror CreateInvoice: race trên (combo_id, installment_number) chặn bởi uq_invoices_combo_
     * installment (V22), phải dịch sang lỗi nghiệp vụ thay vì rơi thành 500. */
    @Test
    void translatesADatabaseRaceOnTheInstallmentNumberIntoAnInstallmentLimitExceededException() {
        stubbedCombo();
        stubExistingInvoices(List.of());
        when(invoices.saveAndFlush(any(Invoice.class)))
                .thenThrow(new DataIntegrityViolationException("uq_invoices_combo_installment"));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("10000000")))
                .isInstanceOf(InstallmentLimitExceededException.class);

        verify(events, never()).publishEvent(any());
    }
}
