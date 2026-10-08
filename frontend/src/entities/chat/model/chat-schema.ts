import { z } from "zod";

/** Bản sao của `ChatConstants.TurnRole` ở backend. Hai giá trị này đi vào JSON nên chúng là hợp đồng,
 * không phải một chi tiết hiển thị. */
export const CHAT_TURN_ROLE = {
  user: "USER",
  assistant: "ASSISTANT",
} as const;

export type ChatTurnRole = (typeof CHAT_TURN_ROLE)[keyof typeof CHAT_TURN_ROLE];

export const CHAT_TURN_ROLE_LABEL: Record<ChatTurnRole, string> = {
  [CHAT_TURN_ROLE.user]: "Bạn",
  [CHAT_TURN_ROLE.assistant]: "Trợ lý tư vấn",
};

/** Khớp `ChatConstants.Limits.MAX_MESSAGE_LENGTH`. Backend kiểm lại bằng CHAT_MESSAGE_TOO_LONG -
 * con số ở đây chỉ để người gõ thấy trần trước khi gửi, không phải lớp bảo vệ. */
export const CHAT_MESSAGE_MAX_LENGTH = 2000;

/**
 * Một lượt hội thoại. Khớp `chat.dto.ChatTurnPayload`.
 *
 * KHÔNG có `id`, không có `createdAt`: backend không lưu hội thoại (spec mục 2), nên không có gì cấp
 * cho một lượt một danh tính bền. React dùng chỉ số trong mảng làm key - hợp lệ ở đây vì danh sách
 * chỉ-thêm-vào-cuối, không bao giờ chèn giữa hay sắp lại.
 */
export const chatTurnSchema = z.object({
  role: z.enum([CHAT_TURN_ROLE.user, CHAT_TURN_ROLE.assistant]),
  content: z.string(),
});

export type ChatTurn = z.infer<typeof chatTurnSchema>;

/** Khớp `chat.dto.AskChatbotResponse` - đúng một field, và đó là toàn bộ hợp đồng ra. */
export const askChatbotResponseSchema = z.object({
  reply: z.string(),
});

export type AskChatbotResponse = z.infer<typeof askChatbotResponseSchema>;

/** Khớp `chat.dto.AskChatbotRequest`. `history` là TOÀN BỘ lịch sử của phiên trình duyệt: backend
 * không lưu gì nên mỗi request phải mang theo ngữ cảnh của chính nó (spec mục 4). */
export interface AskChatbotPayload {
  readonly message: string;
  readonly history: readonly ChatTurn[];
}
