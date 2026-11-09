package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.internal.TuitionInvoiceIssuer;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Luật giải thể combo nằm ở {@code TuitionInvoiceIssuer} và có test riêng. */
class CancelComboTest {

    private final ComboRepository combos = mock(ComboRepository.class);
    private final TuitionInvoiceIssuer issuer = mock(TuitionInvoiceIssuer.class);
    private final CancelCombo useCase = new CancelCombo(combos, mock(BillingBranchGuard.class), issuer);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();

    @Test
    void rejectsAnUnknownCombo() {
        when(combos.findById(comboId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(comboId, actorAccountId, actorBranchId))
                .isInstanceOf(ComboNotFoundException.class);
    }

    @Test
    void dissolvesTheCombo() {
        var combo = new Combo(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("21000000"), new BigDecimal("15"),
                LocalDate.of(2027, 1, 31), actorAccountId);
        when(combos.findById(comboId)).thenReturn(Optional.of(combo));

        useCase.execute(comboId, actorAccountId, actorBranchId);

        verify(issuer).dissolveCombo(combo, actorAccountId, actorBranchId);
    }
}
