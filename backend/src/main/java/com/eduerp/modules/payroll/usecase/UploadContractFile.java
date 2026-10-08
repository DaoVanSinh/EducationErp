package com.eduerp.modules.payroll.usecase;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadContractFile {

    private final EmploymentContractRepository contracts;
    private final StorageClient storage;
    private final IdentityManagement identity;

    UploadContractFile(EmploymentContractRepository contracts, StorageClient storage, IdentityManagement identity) {
        this.contracts = contracts;
        this.storage = storage;
        this.identity = identity;
    }

    @Transactional
    public ContractResponse execute(UUID contractId, String fileName, String contentType, byte[] content) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        var key = storage.upload("contracts/" + contractId, fileName, content, contentType);
        contract.setContractFileKey(key);
        var accountInfo = identity.summariesOf(List.of(contract.getAccountId()));
        return CreateContract.toResponse(contract, accountInfo);
    }
}
