package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Review Focus #3: chặn hai hợp đồng ACTIVE cùng lúc cho một account. */
public final class ContractAlreadyActiveException extends PayrollException {
    public ContractAlreadyActiveException(UUID accountId) {
        super("PAYROLL_CONTRACT_ALREADY_ACTIVE", HttpStatus.CONFLICT,
                "Tài khoản " + accountId + " đã có hợp đồng đang hiệu lực");
    }
}
