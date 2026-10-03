package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import jakarta.validation.constraints.NotNull;

public record InitiateOnlinePaymentRequest(@NotNull BillingConstants.PaymentMethod gateway) {
}
