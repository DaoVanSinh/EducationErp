/** Biến môi trường của Vite, gom một chỗ để không rải import.meta.env khắp code. */
export const ENV = {
  /**
   * Rỗng nghĩa là gọi cùng origin: dev đi qua proxy của Vite, prod đi qua reverse proxy đặt SPA và
   * API sau cùng một tên miền. Cookie phiên là SameSite=Strict nên đây là cấu hình duy nhất hoạt
   * động mà không phải nới lỏng cookie.
   */
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? "",
} as const;
