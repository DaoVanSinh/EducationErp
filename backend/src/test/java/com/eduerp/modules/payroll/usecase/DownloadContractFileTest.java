package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.payroll.ContractFileNotFoundException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Review finding Important #4: tải file về thiếu Content-Disposition với tên file gốc, nên trình
 * duyệt lưu file không có phần mở rộng. StorageClient.upload nhúng tên file gốc vào key dạng
 * "{prefix}/{uuid}-{tên gốc}" - khôi phục lại tên gốc từ chính key đó, không cần thêm cột DB mới. */
class DownloadContractFileTest {

    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final StorageClient storage = mock(StorageClient.class);
    private final DownloadContractFile useCase = new DownloadContractFile(contracts, storage);

    @Test
    void recoversTheOriginalFileNameFromTheStorageKey() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        var key = "contracts/" + UUID.randomUUID() + "/" + UUID.randomUUID() + "-hop-dong.pdf";
        contract.setContractFileKey(key);
        var contractId = UUID.randomUUID();
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));
        when(storage.download(key)).thenReturn("nội dung".getBytes());

        var result = useCase.execute(contractId);

        assertThat(result.fileName()).isEqualTo("hop-dong.pdf");
        assertThat(result.content()).isEqualTo("nội dung".getBytes());
    }

    @Test
    void throwsWhenContractHasNoFileAttached() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        var contractId = UUID.randomUUID();
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> useCase.execute(contractId)).isInstanceOf(ContractFileNotFoundException.class);
    }
}
