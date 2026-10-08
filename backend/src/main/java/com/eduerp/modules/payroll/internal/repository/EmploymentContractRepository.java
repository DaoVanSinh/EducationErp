package com.eduerp.modules.payroll.internal.repository;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, UUID> {
    boolean existsByAccountIdAndStatus(UUID accountId, PayrollConstants.ContractStatus status);

    Optional<EmploymentContract> findByAccountIdAndStatus(UUID accountId, PayrollConstants.ContractStatus status);

    List<EmploymentContract> findAllByStatus(PayrollConstants.ContractStatus status);

    Page<EmploymentContract> findAllByAccountId(UUID accountId, Pageable pageable);

    Page<EmploymentContract> findAll(Pageable pageable);
}
