package com.eduerp.modules.payroll.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.dto.UpdateContractRequest;
import com.eduerp.modules.payroll.usecase.CreateContract;
import com.eduerp.modules.payroll.usecase.DownloadContractFile;
import com.eduerp.modules.payroll.usecase.ListContracts;
import com.eduerp.modules.payroll.usecase.TerminateContract;
import com.eduerp.modules.payroll.usecase.UpdateContract;
import com.eduerp.modules.payroll.usecase.UploadContractFile;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Quản trị hợp đồng lao động - mirror CourseAdminController 1:1 về cấu trúc. */
@RestController
@RequestMapping("/api/payroll/contracts")
class ContractAdminController {

    private final ListContracts listContracts;
    private final CreateContract createContract;
    private final UpdateContract updateContract;
    private final TerminateContract terminateContract;
    private final UploadContractFile uploadContractFile;
    private final DownloadContractFile downloadContractFile;

    ContractAdminController(ListContracts listContracts, CreateContract createContract,
            UpdateContract updateContract, TerminateContract terminateContract,
            UploadContractFile uploadContractFile, DownloadContractFile downloadContractFile) {
        this.listContracts = listContracts;
        this.createContract = createContract;
        this.updateContract = updateContract;
        this.terminateContract = terminateContract;
        this.uploadContractFile = uploadContractFile;
        this.downloadContractFile = downloadContractFile;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    PageResponse<ContractResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID accountId) {
        return listContracts.execute(pageable, accountId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AccessConstants.AccessRules.CREATE_PAYROLL)
    ContractResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestPart("request") CreateContractRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        var created = createContract.execute(principal.accountId(), principal.homeBranchId(), request);
        if (file != null && !file.isEmpty()) {
            return uploadContractFile.execute(created.id(), file.getOriginalFilename(), file.getContentType(),
                    file.getBytes());
        }
        return created;
    }

    @PatchMapping(value = "/{contractId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    ContractResponse update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID contractId,
            @Valid @RequestPart("request") UpdateContractRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        var updated = updateContract.execute(contractId, principal.accountId(), principal.homeBranchId(), request);
        if (file != null && !file.isEmpty()) {
            return uploadContractFile.execute(contractId, file.getOriginalFilename(), file.getContentType(),
                    file.getBytes());
        }
        return updated;
    }

    @PostMapping("/{contractId}/terminate")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    void terminate(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID contractId) {
        terminateContract.execute(contractId, principal.accountId(), principal.homeBranchId());
    }

    @GetMapping("/{contractId}/file")
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    ResponseEntity<byte[]> downloadFile(@PathVariable UUID contractId) {
        var content = downloadContractFile.execute(contractId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(content);
    }
}
