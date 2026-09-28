export const HTTP_METHOD = {
  get: "GET",
  post: "POST",
  patch: "PATCH",
  put: "PUT",
  delete: "DELETE",
} as const;

export type HttpMethod = (typeof HTTP_METHOD)[keyof typeof HTTP_METHOD];

/** Các method làm thay đổi dữ liệu, tức là các method backend đòi CSRF token. */
export const MUTATING_METHODS: readonly HttpMethod[] = [
  HTTP_METHOD.post,
  HTTP_METHOD.patch,
  HTTP_METHOD.put,
  HTTP_METHOD.delete,
];

export const HTTP_STATUS = {
  ok: 200,
  noContent: 204,
  badRequest: 400,
  unauthorized: 401,
  forbidden: 403,
  notFound: 404,
  conflict: 409,
  serverError: 500,
} as const;

export const HTTP_HEADER = {
  accept: "Accept",
  contentType: "Content-Type",
  /** Tên header mà Spring Security đọc khi CookieCsrfTokenRepository được bật. */
  csrfToken: "X-XSRF-TOKEN",
} as const;

export const MEDIA_TYPE = {
  json: "application/json",
} as const;

/** Cookie CSRF do Spring ghi, không HttpOnly nên JavaScript đọc lại được để gửi kèm header. */
export const CSRF_COOKIE = "XSRF-TOKEN";
