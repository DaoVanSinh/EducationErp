import { chatApi, CHAT_TURN_ROLE, type AskChatbotPayload, type ChatTurn } from "@/entities/chat";
import { useMutation } from "@tanstack/react-query";
import { useCallback, useState } from "react";

/**
 * Toàn bộ state/mutation của trang Hỏi đáp khóa học - `ui/*` chỉ render (Mandate #2).
 *
 * Lịch sử hội thoại sống TRONG React state và chỉ ở đó: không localStorage, không TanStack Query
 * cache, không gửi lên server để lưu (spec mục 2 - "tải lại trang là mất lịch sử"). Đó là một quyết
 * định về dữ liệu nhạy cảm của học viên, không phải một thiếu sót cần "cải thiện" sau này bằng một
 * lần persist nhanh.
 *
 * Không `useQuery`/queryKey nào: mỗi câu hỏi là một mutation, và cache lại câu trả lời theo câu hỏi
 * sẽ khiến cùng một câu hỏi trả về câu trả lời cũ trong khi danh mục khóa học đã đổi.
 */
export function useChatController() {
  const [turns, setTurns] = useState<readonly ChatTurn[]>([]);
  const ask = useMutation({
    mutationFn: (payload: AskChatbotPayload) => chatApi.ask(payload),
  });

  const onSend = useCallback(
    (message: string) => {
      const asked = message.trim();
      if (asked.length === 0) {
        return;
      }
      // `history` được chốt TRƯỚC khi thêm lượt mới: backend nối câu hỏi mới vào cuối lịch sử, nên
      // gửi kèm cả lượt vừa thêm sẽ làm câu hỏi xuất hiện hai lần trong prompt.
      const history = turns;
      setTurns([...history, { role: CHAT_TURN_ROLE.user, content: asked }]);
      ask.mutate(
        { message: asked, history },
        {
          onSuccess: (response) => {
            setTurns((previous) => [
              ...previous,
              { role: CHAT_TURN_ROLE.assistant, content: response.reply },
            ]);
          },
        },
      );
    },
    [turns, ask],
  );

  // Khi lỗi, câu hỏi của người dùng được GIỮ LẠI trong danh sách và lỗi hiện ra bên dưới: xoá câu
  // vừa gõ đi là bắt họ gõ lại, và với 429/503 thì việc cần làm chỉ là thử lại sau một lát.
  return { turns, onSend, isPending: ask.isPending, error: ask.error };
}
