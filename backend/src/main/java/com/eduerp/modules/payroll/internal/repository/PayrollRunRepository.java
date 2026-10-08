package com.eduerp.modules.payroll.internal.repository;

import com.eduerp.modules.payroll.internal.model.PayrollRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {
    boolean existsByYearAndMonth(int year, int month);

    Optional<PayrollRun> findByYearAndMonth(int year, int month);

    Page<PayrollRun> findAll(Pageable pageable);
}
