package com.eduerp.integrations.ai;

/**
 * Nhà cung cấp không trả lời được: timeout, 401 vì chưa cấu hình khoá, 429 của chính Anthropic, hay
 * một câu trả lời rỗng. Đây là MỘT kiểu lỗi duy nhất mà nơi gọi phải bắt, thay vì phải biết tới
 * {@code NonTransientAiException}/{@code TransientAiException}/{@code RestClientException} của ba
 * thư viện khác nhau - cùng vai với {@code PaymentSignatureException} của
 * {@code integrations.payment}.
 *
 * <p>KHÔNG kế thừa {@code AppException}: {@code integrations.*} không được quyết định HTTP status của
 * một nghiệp vụ. {@code modules.chat} dịch nó thành {@code ChatbotUnavailableException} (503) ở biên
 * của mình.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
