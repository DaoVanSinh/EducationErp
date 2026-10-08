import {
  askChatbotResponseSchema,
  type AskChatbotPayload,
  type AskChatbotResponse,
} from "@/entities/chat/model/chat-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export const chatApi = {
  /** Một lệnh gọi, một câu trả lời đầy đủ - không streaming (spec Ruling R4). */
  async ask(payload: AskChatbotPayload): Promise<AskChatbotResponse> {
    return askChatbotResponseSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.chat.messages, payload),
    );
  },
} as const;
