package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.ComboAlreadyInvoicedException;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.internal.TuitionInvoiceIssuer;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.response.InvoiceResponseAssembler;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CreateComboInvoiceTest {

    /** 12.000.000 + 9.000.000 = 21.000.000, giảm 15% → 17.850.000. */
    private static final BigDecimal DISCOUNTED_TOTAL = new BigDecimal("17850000");
    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);

    private final ComboRepository combos = mock(ComboRepository.class);
    private final TuitionInvoiceIssuer issuer = mock(TuitionInvoiceIssuer.class);
    private final CreateComboInvoice useCase = new CreateComboInvoice(combos, mock(BillingBranchGuard.class), issuer,
            new InvoiceResponseAssembler(combos, mock(CoursesManagement.class), mock(StudentsManagement.class)));

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID comboBranchId = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();
    private final CreateComboInvoiceRequest request = new CreateComboInvoiceRequest(comboId);

    private Combo stubCombo() {
        var combo = new Combo(studentProfileId, comboBranchId, new BigDecimal("21000000"), new BigDecimal("15"),
                DUE_DATE, actorAccountId);
        when(combos.findById(comboId)).thenReturn(Optional.of(combo));
        return combo;
    }

    @Test
    void rejectsAnUnknownCombo() {
        when(combos.findById(comboId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(ComboNotFoundException.class);
    }

    @Test
    void reportsAlreadyInvoicedWhenNothingIsLeftToIssue() {
        var combo = stubCombo();
        when(issuer.issueForCombo(combo, actorAccountId, actorBranchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(ComboAlreadyInvoicedException.class);
    }

    @Test
    void translatesAConcurrentIssueIntoAlreadyInvoiced() {
        var combo = stubCombo();
        when(issuer.issueForCombo(combo, actorAccountId, actorBranchId))
                .thenThrow(new DataIntegrityViolationException("uq_invoices_combo_installment"));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request))
                .isInstanceOf(ComboAlreadyInvoicedException.class);
    }

    @Test
    void returnsTheIssuedInvoice() {
        var combo = stubCombo();
        var invoice = Invoice.forCombo(comboId, studentProfileId, comboBranchId, 1, DISCOUNTED_TOTAL, DUE_DATE,
                actorAccountId);
        when(issuer.issueForCombo(combo, actorAccountId, actorBranchId)).thenReturn(Optional.of(invoice));

        var response = useCase.execute(actorAccountId, actorBranchId, request);

        assertThat(response.comboId()).isEqualTo(comboId);
        assertThat(response.amount()).isEqualByComparingTo(DISCOUNTED_TOTAL);
    }
}
