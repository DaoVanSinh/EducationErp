import { CHAT_MESSAGE_MAX_LENGTH } from "@/entities/chat";
import { GlassButton } from "@/shared/ui/glass-button";
import { SendHorizontal } from "lucide-react";
import { useCallback, useState, type FormEvent, type KeyboardEvent } from "react";

export interface ChatInputProps {
  readonly onSend: (message: string) => void;
  readonly disabled: boolean;
}

/**
 * Ô nhập câu hỏi. State của chính ô nhập (chuỗi đang gõ, chưa gửi) nằm ở đây chứ không ở
 * `useChatController`: nó không phải state của hội thoại, và để nó lên trên sẽ khiến mọi ký tự gõ vào
 * render lại cả danh sách tin nhắn.
 */
export function ChatInput({ onSend, disabled }: ChatInputProps) {
  const [draft, setDraft] = useState("");

  const submit = useCallback(() => {
    if (draft.trim().length === 0) {
      return;
    }
    onSend(draft);
    setDraft("");
  }, [draft, onSend]);

  const onSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      submit();
    },
    [submit],
  );

  // Enter gửi, Shift+Enter xuống dòng - đúng quy ước mọi người đã quen ở một ô chat.
  const onKeyDown = useCallback(
    (event: KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key !== "Enter" || event.shiftKey) {
        return;
      }
      event.preventDefault();
      submit();
    },
    [submit],
  );

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-2" noValidate>
      <label htmlFor="chat-message" className="sr-only">
        Câu hỏi về khóa học
      </label>
      <textarea
        id="chat-message"
        rows={3}
        maxLength={CHAT_MESSAGE_MAX_LENGTH}
        value={draft}
        disabled={disabled}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={onKeyDown}
        placeholder="Ví dụ: có khóa tiếng Anh giao tiếp buổi tối không?"
        className="w-full resize-none rounded-2xl border border-slate-200/90 bg-white/75 px-4 py-3 text-sm text-slate-800 placeholder:text-slate-400 backdrop-blur-md transition-all duration-200 hover:border-orange-300/60 focus:border-orange-400 focus:bg-white focus:ring-2 focus:ring-orange-300/30 focus:outline-none disabled:opacity-60"
      />
      <div className="flex items-center justify-between gap-3">
        <span className="text-xs text-slate-400">
          {draft.length}/{CHAT_MESSAGE_MAX_LENGTH} ký tự · Enter để gửi, Shift+Enter để xuống dòng
        </span>
        <GlassButton
          type="submit"
          size="sm"
          loading={disabled}
          disabled={disabled || draft.trim().length === 0}
          icon={<SendHorizontal size={14} aria-hidden />}
        >
          Gửi câu hỏi
        </GlassButton>
      </div>
    </form>
  );
}
