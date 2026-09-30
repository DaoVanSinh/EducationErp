import { ApiError } from "@/shared/api/api-error";
import { ENV } from "@/shared/config/env";
import { API_ROUTE, ENDPOINTS_WITHOUT_SESSION_RETRY } from "@/shared/constants/api-routes";
import {
  CSRF_COOKIE,
  HTTP_HEADER,
  HTTP_METHOD,
  HTTP_STATUS,
  MEDIA_TYPE,
  MUTATING_METHODS,
  type HttpMethod,
} from "@/shared/constants/http";
import { cookies } from "@/shared/lib/cookies";

export type QueryParams = Readonly<Record<string, string | number | boolean | undefined>>;

export interface ApiRequest {
  readonly method?: HttpMethod;
  readonly body?: unknown;
  readonly query?: QueryParams;
}

type SessionExpiredListener = () => void;

/**
 * Cửa duy nhất đi ra API.
 *
 * Ba việc nó gánh thay cho mọi nơi gọi: gửi cookie phiên (credentials: "include"), kèm CSRF token
 * đọc từ cookie cho mọi request làm thay đổi dữ liệu, và khi access token hết hạn thì tự gọi refresh
 * một lần rồi thử lại - người dùng không bị đá ra màn hình đăng nhập sau mỗi 15 phút.
 */
class ApiClient {
  private readonly baseUrl: string;
  private readonly sessionExpiredListeners = new Set<SessionExpiredListener>();
  /** Nhiều request 401 cùng lúc chỉ được tạo một lần refresh, nếu không token sẽ bị quay vòng chồng nhau. */
  private refreshInFlight: Promise<boolean> | null = null;
  /**
   * Hai lần gọi trùng URL cùng lúc (StrictMode double-mount, hai component cùng cần một dữ liệu...)
   * chỉ được gửi một request thật. Không gộp thì hai lần 401 độc lập cùng bắn
   * {@link onSessionExpired}, mỗi lần đều dọn cache của chính request kia đang chạy dở — loading treo
   * vĩnh viễn vì observer thấy query của mình liên tục bị xoá rồi tạo lại.
   */
  private readonly inFlightGets = new Map<string, Promise<unknown>>();

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  /** Cho tầng app biết phiên đã mất hẳn, để xoá cache và đưa về trang đăng nhập. */
  onSessionExpired(listener: SessionExpiredListener): () => void {
    this.sessionExpiredListeners.add(listener);
    return () => {
      this.sessionExpiredListeners.delete(listener);
    };
  }

  async get<T>(path: string, query?: QueryParams): Promise<T> {
    const key = this.url(path, query);
    const existing = this.inFlightGets.get(key);
    if (existing) {
      return existing as Promise<T>;
    }
    const inFlight = this.request<T>(path, { method: HTTP_METHOD.get, query }).finally(() => {
      this.inFlightGets.delete(key);
    });
    this.inFlightGets.set(key, inFlight);
    return inFlight;
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, { method: HTTP_METHOD.post, body });
  }

  async patch<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>(path, { method: HTTP_METHOD.patch, body });
  }

  async request<T>(path: string, request: ApiRequest = {}): Promise<T> {
    let response = await this.send(path, request);

    if (response.status === HTTP_STATUS.unauthorized && !ENDPOINTS_WITHOUT_SESSION_RETRY.includes(path)) {
      if (await this.refreshSession()) {
        response = await this.send(path, request);
      } else {
        this.sessionExpiredListeners.forEach((listener) => listener());
        throw ApiError.sessionExpired();
      }
    }

    return this.readBody<T>(response);
  }

  private async send(path: string, request: ApiRequest): Promise<Response> {
    const method = request.method ?? HTTP_METHOD.get;
    const headers = new Headers({ [HTTP_HEADER.accept]: MEDIA_TYPE.json });

    if (request.body !== undefined) {
      headers.set(HTTP_HEADER.contentType, MEDIA_TYPE.json);
    }
    if (MUTATING_METHODS.includes(method)) {
      const csrfToken = cookies.read(CSRF_COOKIE);
      if (csrfToken) {
        headers.set(HTTP_HEADER.csrfToken, csrfToken);
      }
    }

    try {
      return await fetch(this.url(path, request.query), {
        method,
        headers,
        credentials: "include",
        body: request.body === undefined ? undefined : JSON.stringify(request.body),
      });
    } catch {
      throw ApiError.networkUnreachable();
    }
  }

  private async refreshSession(): Promise<boolean> {
    this.refreshInFlight ??= this.performRefresh();
    try {
      return await this.refreshInFlight;
    } finally {
      this.refreshInFlight = null;
    }
  }

  private async performRefresh(): Promise<boolean> {
    try {
      const response = await this.send(API_ROUTE.auth.refresh, { method: HTTP_METHOD.post });
      return response.ok;
    } catch {
      return false;
    }
  }

  private async readBody<T>(response: Response): Promise<T> {
    if (!response.ok) {
      throw await ApiError.fromResponse(response);
    }
    if (response.status === HTTP_STATUS.noContent) {
      return undefined as T;
    }
    // Nhiều endpoint trả 200 không có body (đăng nhập, đổi mật khẩu): JSON.parse("") sẽ ném lỗi.
    const text = await response.text();
    return (text.length === 0 ? undefined : JSON.parse(text)) as T;
  }

  private url(path: string, query?: QueryParams): string {
    if (!query) {
      return `${this.baseUrl}${path}`;
    }
    const search = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined) {
        search.set(key, String(value));
      }
    });
    const suffix = search.toString();
    return suffix.length === 0 ? `${this.baseUrl}${path}` : `${this.baseUrl}${path}?${suffix}`;
  }
}

export const apiClient = new ApiClient(ENV.apiBaseUrl);
