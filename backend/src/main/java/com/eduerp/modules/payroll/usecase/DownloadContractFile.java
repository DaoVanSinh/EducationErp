package com.eduerp.modules.payroll.usecase;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.payroll.ContractFileNotFoundException;
import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DownloadContractFile {

    /** UUID.randomUUID().toString() luôn dài 36 ký tự - đúng độ dài StorageClient.upload dùng khi
     * ghép key dạng "{prefix}/{uuid}-{tên file gốc}". */
    private static final int UUID_PREFIX_LENGTH = 36 + 1;

    private final EmploymentContractRepository contracts;
    private final StorageClient storage;

    DownloadContractFile(EmploymentContractRepository contracts, StorageClient storage) {
        this.contracts = contracts;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public ContractFileResult execute(UUID contractId) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        var key = contract.getContractFileKey();
        if (key == null) {
            throw new ContractFileNotFoundException(contractId);
        }
        return new ContractFileResult(storage.download(key), recoverFileName(key));
    }

    /** StorageClient.upload không lưu tên file gốc ở đâu khác ngoài chính object key
     * ("{prefix}/{uuid}-{tên gốc}") - khôi phục lại từ đó thay vì thêm cột DB mới cho một giá trị đã
     * có sẵn trong dữ liệu đang lưu (review finding Important #4). */
    private static String recoverFileName(String key) {
        var lastSegment = key.substring(key.lastIndexOf('/') + 1);
        return lastSegment.length() > UUID_PREFIX_LENGTH ? lastSegment.substring(UUID_PREFIX_LENGTH) : lastSegment;
    }

    public record ContractFileResult(byte[] content, String fileName) {
    }
}
