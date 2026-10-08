import { CHAT_TURN_ROLE, CHAT_TURN_ROLE_LABEL, type ChatTurn } from "@/entities/chat";
import { cx } from "@/shared/lib/class-names";
import { Spinner } from "@/shared/ui/spinner";

export interface ChatMessageListProps {
  readonly turns: readonly ChatTurn[];
  readonly isPending: boolean;
}

/** Render thuần một lượt. Tách ra để component danh sách không rẽ nhánh trong JSX (Mandate #3). */
function ChatBubble({ turn }: { readonly turn: ChatTurn }) {
  const isUser = turn.role === CHAT_TURN_ROLE.user;

  return (
    <li className={cx("flex flex-col gap-1", isUser ? "items-end" : "items-start")}>
      <span className="px-1 text-[10px] font-bold tracking-widest text-slate-400 uppercase">
        {CHAT_TURN_ROLE_LABEL[turn.role]}
      </span>
      <div
        className={cx(
          "max-w-[85%] rounded-2xl border px-4 py-2.5 text-sm whitespace-pre-wrap break-words",
          isUser
            ? "border-orange-200/90 bg-orange-50/90 text-slate-800"
            : "glass border-white/80 text-slate-700",
        )}
      >
        {turn.content}
      </div>
    </li>
  );
}

/** Ruling R4: không gõ-từng-chữ. Trạng thái "đang trả lời" là một bong bóng chờ, không phải một hiệu
 * ứng streaming. */
function PendingBubble() {
  return (
    <li className="flex flex-col items-start gap-1">
      <span className="px-1 text-[10px] font-bold tracking-widest text-slate-400 uppercase">
        {CHAT_TURN_ROLE_LABEL[CHAT_TURN_ROLE.assistant]}
      </span>
      <div className="glass flex items-center gap-2 rounded-2xl border border-white/80 px-4 py-2.5">
        <Spinner />
        <span className="text-sm text-slate-500">Đang tra danh mục khóa học…</span>
      </div>
    </li>
  );
}

export function ChatMessageList({ turns, isPending }: ChatMessageListProps) {
  return (
    <ul
      aria-live="polite"
      aria-label="Nội dung hội thoại"
      className="flex max-h-[55vh] flex-col gap-4 overflow-y-auto px-1"
    >
      {turns.map((turn, index) => (
        // Chỉ số làm key là hợp lệ ở đây: danh sách chỉ được THÊM VÀO CUỐI, không bao giờ chèn giữa,
        // xoá hay sắp lại - và một lượt không có id vì backend không lưu gì (spec mục 2).
        <ChatBubble key={`${turn.role}-${index}`} turn={turn} />
      ))}
      {isPending ? <PendingBubble /> : null}
    </ul>
  );
}
