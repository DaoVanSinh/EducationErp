import { RequirePermission } from "@/entities/permission";
import { useChatController } from "@/modules/chat/hooks/use-chat-controller";
import { ChatInput } from "@/modules/chat/ui/chat-input";
import { ChatMessageList } from "@/modules/chat/ui/chat-message-list";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { MessagesSquare } from "lucide-react";

export function ChatPage() {
  const controller = useChatController();

  return (
    <RequirePermission {...ACCESS_RULE.createChat}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Hỏi đáp khóa học"
          description="Đặt câu hỏi về các khóa học đang mở của trung tâm. Trợ lý chỉ trả lời dựa trên danh mục khóa học thật, và hội thoại không được lưu lại — tải lại trang là bắt đầu lại."
        />

        {/* Nguồn sự thật về lỗi là chính ProblemDetail của backend: 429 "chờ một lát", 503 "tạm thời
            không trả lời được", 400 "câu hỏi quá dài" - giao diện không tự diễn giải lại. */}
        {controller.error ? <ErrorNotice error={controller.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <ChatBody controller={controller} />
          <ChatInput onSend={controller.onSend} disabled={controller.isPending} />
        </GlassPanel>
      </div>
    </RequirePermission>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function ChatBody({ controller }: { readonly controller: ReturnType<typeof useChatController> }) {
  if (controller.turns.length === 0 && !controller.isPending) {
    return (
      <EmptyState
        icon={<MessagesSquare size={28} aria-hidden />}
        title="Bắt đầu bằng một câu hỏi"
        description="Thử hỏi về học phí, lịch học buổi tối, hoặc khóa phù hợp với lớp của con bạn."
      />
    );
  }
  return <ChatMessageList turns={controller.turns} isPending={controller.isPending} />;
}
