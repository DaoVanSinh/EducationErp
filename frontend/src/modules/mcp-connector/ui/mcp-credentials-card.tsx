import type { McpConnector } from "@/entities/mcp-connector";
import {
  MCP_COPY_FIELD,
  type McpCopyField,
} from "@/modules/mcp-connector/constants/mcp-connector-constants";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { AlertTriangle, Check, Copy, KeyRound, RefreshCw } from "lucide-react";

export interface McpCredentialsCardProps {
  readonly connector: McpConnector;
  readonly revealedSecret: string | null;
  readonly revealedBearerToken: string | null;
  readonly secretPreview: string;
  readonly bearerTokenPreview: string;
  readonly copiedField: McpCopyField | null;
  readonly isRotatingSecret: boolean;
  readonly onCopyText: (field: McpCopyField, value: string) => void;
  readonly onRotateSecret: () => void;
  readonly onClearRevealedSecret: () => void;
}

function SecretRotatedNotice({
  revealedSecret,
  revealedBearerToken,
  copiedField,
  onCopyText,
  onClearRevealedSecret,
}: {
  readonly revealedSecret: string;
  readonly revealedBearerToken: string | null;
  readonly copiedField: McpCopyField | null;
  readonly onCopyText: (field: McpCopyField, value: string) => void;
  readonly onClearRevealedSecret: () => void;
}) {
  const isSecretCopied = copiedField === MCP_COPY_FIELD.CLIENT_SECRET;
  const isBearerCopied = copiedField === MCP_COPY_FIELD.BEARER_TOKEN;

  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-amber-300/90 bg-amber-50/90 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 text-amber-800">
          <AlertTriangle size={18} aria-hidden />
          <span className="text-sm font-semibold">
            Hãy sao chép Client Secret ngay — khóa này chỉ hiển thị một lần duy nhất!
          </span>
        </div>
        <GlassButton variant="ghost" size="sm" onClick={onClearRevealedSecret}>
          Ẩn khóa
        </GlassButton>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-amber-200 bg-white px-3 py-2">
        <code className="font-mono text-xs break-all text-slate-900">{revealedSecret}</code>
        <GlassButton
          variant="secondary"
          size="sm"
          icon={isSecretCopied ? <Check size={14} aria-hidden /> : <Copy size={14} aria-hidden />}
          onClick={() => onCopyText(MCP_COPY_FIELD.CLIENT_SECRET, revealedSecret)}
        >
          {isSecretCopied ? "Đã chép Secret" : "Chép Secret"}
        </GlassButton>
      </div>

      {revealedBearerToken ? (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-amber-200 bg-white px-3 py-2">
          <code className="font-mono text-xs break-all text-slate-900">{revealedBearerToken}</code>
          <GlassButton
            variant="secondary"
            size="sm"
            icon={isBearerCopied ? <Check size={14} aria-hidden /> : <Copy size={14} aria-hidden />}
            onClick={() => onCopyText(MCP_COPY_FIELD.BEARER_TOKEN, revealedBearerToken)}
          >
            {isBearerCopied ? "Đã chép Bearer Token" : "Chép Bearer Token"}
          </GlassButton>
        </div>
      ) : null}
    </div>
  );
}

export function McpCredentialsCard({
  connector,
  revealedSecret,
  revealedBearerToken,
  secretPreview,
  bearerTokenPreview,
  copiedField,
  isRotatingSecret,
  onCopyText,
  onRotateSecret,
  onClearRevealedSecret,
}: McpCredentialsCardProps) {
  const isClientIdCopied = copiedField === MCP_COPY_FIELD.CLIENT_ID;
  const secretTone = connector.hasSecret ? "positive" : "warning";
  const secretStatusLabel = connector.hasSecret ? "Đã thiết lập Secret" : "Chưa có Secret";
  const rotateButtonLabel = connector.hasSecret ? "Đổi Client Secret mới" : "Khởi tạo Client Secret";

  return (
    <GlassPanel className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-orange-50 text-orange-600">
            <KeyRound size={20} aria-hidden />
          </span>
          <div>
            <h2 className="text-base font-bold text-slate-900">
              Thông tin xác thực Connector cá nhân
            </h2>
            <p className="text-sm text-slate-500">
              Mỗi tài khoản DS Academy sở hữu một cặp định danh riêng biệt, gắn trực tiếp với quyền RBAC của bạn.
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Badge tone={secretTone}>{secretStatusLabel}</Badge>
          <GlassButton
            variant="primary"
            size="sm"
            loading={isRotatingSecret}
            icon={<RefreshCw size={15} aria-hidden />}
            onClick={onRotateSecret}
          >
            {rotateButtonLabel}
          </GlassButton>
        </div>
      </div>

      {revealedSecret ? (
        <SecretRotatedNotice
          revealedSecret={revealedSecret}
          revealedBearerToken={revealedBearerToken}
          copiedField={copiedField}
          onCopyText={onCopyText}
          onClearRevealedSecret={onClearRevealedSecret}
        />
      ) : null}

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <div className="flex flex-col justify-between gap-2 rounded-2xl border border-slate-200/80 bg-white/60 p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-semibold tracking-wide text-slate-600 uppercase">
              OAuth Client ID
            </span>
            <GlassButton
              variant="secondary"
              size="sm"
              icon={
                isClientIdCopied ? <Check size={14} aria-hidden /> : <Copy size={14} aria-hidden />
              }
              onClick={() => onCopyText(MCP_COPY_FIELD.CLIENT_ID, connector.clientId)}
            >
              {isClientIdCopied ? "Đã chép" : "Sao chép"}
            </GlassButton>
          </div>
          <code className="font-mono text-sm font-semibold break-all text-slate-900">
            {connector.clientId}
          </code>
          <span className="text-xs text-slate-500">
            Nhập vào trường OAuth Client ID trên Claude.ai hoặc ChatGPT.
          </span>
        </div>

        <div className="flex flex-col justify-between gap-2 rounded-2xl border border-slate-200/80 bg-white/60 p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-semibold tracking-wide text-slate-600 uppercase">
              OAuth Client Secret / Bearer Token
            </span>
            { connector.secretRotatedAt ? (
              <span className="text-xs text-slate-400">
                Đổi lần cuối: {formatter.dateTime(connector.secretRotatedAt)}
              </span>
            ) : null }
          </div>
          <code className="font-mono text-sm break-all text-slate-800">{secretPreview}</code>
          <span className="text-xs text-slate-500">
            Định dạng IDE Bearer Token: <code className="font-mono">{bearerTokenPreview}</code>
          </span>
        </div>
      </div>
    </GlassPanel>
  );
}
