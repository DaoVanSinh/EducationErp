/**
 * Hai cổng thanh toán Việt Nam (MoMo, VNPay) sau một interface duy nhất. Cơ chế thuần: không biết
 * hoá đơn/ghi danh/học phí là gì - module nghiệp vụ ({@code modules.billing}) tự đặt
 * {@code orderId}, tự quyết số tiền, tự lưu bản ghi {@code Payment}. Mirror
 * {@code integrations.storage}: một interface + nhiều implementation, KHÔNG phụ thuộc bất kỳ
 * {@code modules.*} nào (spec mục 6).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payment Gateways")
package com.eduerp.integrations.payment;
