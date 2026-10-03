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

    private final EmploymentContractRepository contracts;
    private final StorageClient storage;

    DownloadContractFile(EmploymentContractRepository contracts, StorageClient storage) {
        this.contracts = contracts;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public byte[] execute(UUID contractId) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        if (contract.getContractFileKey() == null) {
            throw new ContractFileNotFoundException(contractId);
        }
        return storage.download(contract.getContractFileKey());
    }
}
