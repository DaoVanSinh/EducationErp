package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.ContractAlreadyActiveException;
import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CreateContractTest {

    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final IdentityManagement identity = mock(IdentityManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateContract useCase = new CreateContract(contracts, identity, events);

    private void stubAccountExists(UUID accountId) {
        when(identity.summariesOf(List.of(accountId)))
                .thenReturn(Map.of(accountId, new IdentityManagement.AccountBasicInfo(accountId, "a@eduerp.local", "A")));
    }

    @Test
    void rejectsAnAccountIdThatDoesNotExist() {
        var accountId = UUID.randomUUID();
        when(identity.summariesOf(List.of(accountId))).thenReturn(Map.of());
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(AppValidationException.class);
    }

    /** Review Focus #3. */
    @Test
    void rejectsASecondActiveContractForTheSameAccount() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        when(contracts.existsByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.ACTIVE)).thenReturn(true);
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(ContractAlreadyActiveException.class);
    }

    @Test
    void rejectsOfficialContractWithoutBaseSalary() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL, null, null, null,
                null, LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    @Test
    void rejectsCollaboratorContractWithoutHourlyRate() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.COLLABORATOR, null, null,
                null, null, LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    /** Review Focus #2. */
    @Test
    void rejectsCollaboratorContractWithProbationDates() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), LocalDate.now(), LocalDate.now().plusMonths(2), LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    @Test
    void createsAnOfficialContractAndPublishesContractCreated() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        when(contracts.save(any(EmploymentContract.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, LocalDate.now(), LocalDate.now().plusMonths(2), LocalDate.now(),
                List.of());

        var response = useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request);

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.accountFullName()).isEqualTo("A");
        assertThat(response.contractType()).isEqualTo(PayrollConstants.ContractType.OFFICIAL);
        verify(events).publishEvent(any(PayrollEvents.ContractCreated.class));
    }
}
