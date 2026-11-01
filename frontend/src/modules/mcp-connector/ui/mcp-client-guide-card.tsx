import { GlassPanel } from "@/shared/ui/glass-panel";
import { BookOpen, Bot, Sparkles, Terminal } from "lucide-react";

export function McpClientGuideCard() {
  return (
    <GlassPanel className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-orange-50 text-orange-600">
          <BookOpen size={20} aria-hidden />
        </span>
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Hướng dẫn kết nối Claude, ChatGPT &amp; AI IDE
          </h2>
          <p className="text-sm text-slate-500">
            Mọi công cụ gọi qua MCP đều tự động kiểm tra quyền hạn RBAC hiện hành của tài khoản bạn.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="flex flex-col gap-2 rounded-2xl border border-slate-200/80 bg-white/60 p-4">
          <div className="flex items-center gap-2 font-semibold text-slate-900">
            <Sparkles size={16} className="text-orange-500" aria-hidden />
            <span>1. Claude (claude.ai)</span>
          </div>
          <ol className="list-decimal space-y-1.5 pl-4 text-xs leading-relaxed text-slate-600">
            <li>Vào <strong>Settings &rarr; Integrations &rarr; Add Custom Connector</strong>.</li>
            <li>Dán <strong>MCP Server URL</strong> ở trên vào ô URL.</li>
            <li>Mở phần <strong>Advanced / OAuth</strong>, điền <strong>OAuth Client ID</strong> (và Client Secret nếu được hỏi).</li>
            <li>Bấm <strong>Connect</strong> và xác nhận ủy quyền trên màn hình DS Academy.</li>
          </ol>
        </div>

        <div className="flex flex-col gap-2 rounded-2xl border border-slate-200/80 bg-white/60 p-4">
          <div className="flex items-center gap-2 font-semibold text-slate-900">
            <Bot size={16} className="text-orange-500" aria-hidden />
            <span>2. ChatGPT (chatgpt.com)</span>
          </div>
          <ol className="list-decimal space-y-1.5 pl-4 text-xs leading-relaxed text-slate-600">
            <li>Vào <strong>Settings &rarr; Connectors &rarr; Create Custom MCP Connector</strong>.</li>
            <li>Dán <strong>MCP Server URL</strong> và chọn phương thức xác thực <strong>OAuth 2.1</strong>.</li>
            <li>Điền <strong>OAuth Client ID</strong> và <strong>Client Secret</strong> của bạn.</li>
            <li>Hoàn tất bước đăng nhập &amp; bấm <strong>Cho phép kết nối</strong>.</li>
          </ol>
        </div>

        <div className="flex flex-col gap-2 rounded-2xl border border-slate-200/80 bg-white/60 p-4">
          <div className="flex items-center gap-2 font-semibold text-slate-900">
            <Terminal size={16} className="text-orange-500" aria-hidden />
            <span>3. Antigravity / Cursor / VS Code</span>
          </div>
          <ol className="list-decimal space-y-1.5 pl-4 text-xs leading-relaxed text-slate-600">
            <li>Bấm <strong>Khởi tạo / Đổi Client Secret</strong> ở trên để nhận khóa mới.</li>
            <li>Chọn tab tương ứng ở mục <strong>Cấu hình nhanh cho AI IDE</strong>.</li>
            <li>Sao chép đoạn JSON đã điền sẵn Bearer Token <code className="font-mono">&lt;client_id&gt;.&lt;client_secret&gt;</code> vào tệp cấu hình IDE.</li>
          </ol>
        </div>
      </div>
    </GlassPanel>
  );
}
