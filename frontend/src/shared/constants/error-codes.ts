/**
 * errorCode do backend trả trong ProblemDetail. Chỉ liệt kê những mã mà giao diện cần xử lý khác đi;
 * còn lại hiển thị nguyên câu "detail" - backend đã viết sẵn bằng tiếng Việt.
 */
export const ERROR_CODE = {
  validationFailed: "VALIDATION_FAILED",
  invalidCredentials: "IDENTITY_INVALID_CREDENTIALS",
  tokenInvalid: "IDENTITY_TOKEN_INVALID",
  accountNotFound: "IDENTITY_ACCOUNT_NOT_FOUND",
  /** Mã mà client tự sinh khi không nhận được phản hồi nào (mất mạng, server chưa chạy). */
  networkUnreachable: "CLIENT_NETWORK_UNREACHABLE",
  /** Phiên đã hết và refresh cũng không cứu được. */
  sessionExpired: "CLIENT_SESSION_EXPIRED",
} as const;

export const FALLBACK_ERROR_MESSAGE: Record<string, string> = {
  [ERROR_CODE.networkUnreachable]: "Không kết nối được tới máy chủ. Kiểm tra lại mạng rồi thử lại.",
  [ERROR_CODE.sessionExpired]: "Phiên làm việc đã hết. Vui lòng đăng nhập lại.",
  [ERROR_CODE.validationFailed]: "Dữ liệu chưa hợp lệ. Xem lại các ô được đánh dấu.",
};

export const GENERIC_ERROR_MESSAGE = "Có lỗi xảy ra. Vui lòng thử lại.";
