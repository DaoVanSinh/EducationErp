import {
  ERROR_CODE,
  FALLBACK_ERROR_MESSAGE,
  GENERIC_ERROR_MESSAGE,
} from "@/shared/constants/error-codes";
import { HTTP_STATUS } from "@/shared/constants/http";

export interface FieldIssue {
  readonly field: string;
  readonly message: string;
}

/** Hình dạng ProblemDetail mà GlobalExceptionHandler của backend trả về. */
interface ProblemDetail {
  readonly title?: string;
  readonly detail?: string;
  readonly status?: number;
  readonly errorCode?: string;
  readonly errors?: readonly FieldIssue[];
}

/**
 * Lỗi API đã được dịch sang thứ giao diện dùng được: một câu để hiển thị, một mã để rẽ nhánh, và
 * danh sách lỗi theo từng ô nhập khi backend trả VALIDATION_FAILED.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly errorCode: string;
  readonly fieldIssues: readonly FieldIssue[];

  constructor(status: number, errorCode: string, message: string, fieldIssues: readonly FieldIssue[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errorCode = errorCode;
    this.fieldIssues = fieldIssues;
  }

  get isUnauthorized(): boolean {
    return this.status === HTTP_STATUS.unauthorized;
  }

  get isForbidden(): boolean {
    return this.status === HTTP_STATUS.forbidden;
  }

  /** Lỗi của từng ô nhập, dạng map để form tra theo tên field. */
  fieldIssueMap(): Record<string, string> {
    return this.fieldIssues.reduce<Record<string, string>>((carry, issue) => {
      carry[issue.field] = issue.message;
      return carry;
    }, {});
  }

  static async fromResponse(response: Response): Promise<ApiError> {
    const problem = await ApiError.readProblemDetail(response);
    const errorCode = problem?.errorCode ?? problem?.title ?? String(response.status);
    const message = problem?.detail ?? FALLBACK_ERROR_MESSAGE[errorCode] ?? GENERIC_ERROR_MESSAGE;
    return new ApiError(response.status, errorCode, message, problem?.errors ?? []);
  }

  static networkUnreachable(): ApiError {
    return new ApiError(
      0,
      ERROR_CODE.networkUnreachable,
      FALLBACK_ERROR_MESSAGE[ERROR_CODE.networkUnreachable] ?? GENERIC_ERROR_MESSAGE,
    );
  }

  static sessionExpired(): ApiError {
    return new ApiError(
      HTTP_STATUS.unauthorized,
      ERROR_CODE.sessionExpired,
      FALLBACK_ERROR_MESSAGE[ERROR_CODE.sessionExpired] ?? GENERIC_ERROR_MESSAGE,
    );
  }

  /** Câu để hiển thị cho bất kỳ lỗi nào, kể cả lỗi không phải từ API. */
  static messageOf(error: unknown): string {
    if (error instanceof ApiError) {
      return error.message;
    }
    return error instanceof Error && error.message.length > 0 ? error.message : GENERIC_ERROR_MESSAGE;
  }

  private static async readProblemDetail(response: Response): Promise<ProblemDetail | null> {
    try {
      const text = await response.text();
      return text.length === 0 ? null : (JSON.parse(text) as ProblemDetail);
    } catch {
      // Phản hồi lỗi không phải JSON (ví dụ trang lỗi của reverse proxy): coi như không có chi tiết.
      return null;
    }
  }
}
