import type { McpCatalogItem, McpConnector } from "@/entities/mcp-connector";
import {
  MCP_COPY_FIELD,
  type McpCopyField,
} from "@/modules/mcp-connector/constants/mcp-connector-constants";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Check, Copy, Globe, ShieldCheck } from "lucide-react";

export interface McpUrlsCardProps {
  readonly connector: McpConnector;
  readonly mcps: readonly McpCatalogItem[];
  readonly copiedField: McpCopyField | null;
  readonly onCopyText: (field: McpCopyField, value: string) => void;
}

function CopyableUrlRow({
  label,
  hint,
  value,
  field,
  copiedField,
  onCopyText,
}: {
  readonly label: string;
  readonly hint: string;
  readonly value: string;
  readonly field: McpCopyField;
  readonly copiedField: McpCopyField | null;
  readonly onCopyText: (field: McpCopyField, value: string) => void;
}) {
  const isCopied = copiedField === field;
  const copyIcon = isCopied ? <Check size={15} aria-hidden /> : <Copy size={15} aria-hidden />;
  const copyLabel = isCopied ? "Đã chép" : "Sao chép";

  return (
    <div className="flex flex-col gap-2 rounded-2xl border border-slate-200/80 bg-white/60 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold tracking-wide text-slate-600 uppercase">
            {label}
          </span>
          <span className="text-xs text-slate-400">• {hint}</span>
        </div>
        <code className="mt-1 block truncate font-mono text-sm font-medium text-slate-900">
          {value}
        </code>
      </div>
      <GlassButton
        variant="secondary"
        size="sm"
        icon={copyIcon}
        onClick={() => onCopyText(field, value)}
      >
        {copyLabel}
      </GlassButton>
    </div>
  );
}

export function McpUrlsCard({
  connector,
  mcps,
  copiedField,
  onCopyText,
}: McpUrlsCardProps) {
  const primaryMcp = mcps[0] ?? null;
  const mcpUrl = primaryMcp ? primaryMcp.mcpUrl : connector.mcpBaseUrl;

  return (
    <GlassPanel className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-orange-50 text-orange-600">
            <Globe size={20} aria-hidden />
          </span>
          <div>
            <h2 className="text-base font-bold text-slate-900">
              Điểm cuối kết nối MCP &amp; OAuth 2.1
            </h2>
            <p className="text-sm text-slate-500">
              Dùng chung cho Claude.ai, ChatGPT và các trình soạn thảo AI (Antigravity, Cursor, Claude Code, VS Code).
            </p>
          </div>
        </div>
        <Badge tone="positive">
          <ShieldCheck size={13} aria-hidden />
          RBAC Trực tiếp (Redis + DB)
        </Badge>
      </div>

      <div className="flex flex-col gap-3">
        <CopyableUrlRow
          label="MCP Server URL"
          hint="Streamable HTTP endpoint"
          value={mcpUrl}
          field={MCP_COPY_FIELD.MCP_URL}
          copiedField={copiedField}
          onCopyText={onCopyText}
        />
        <CopyableUrlRow
          label="OAuth Authorization URL"
          hint="Tự động khám phá qua /.well-known"
          value={connector.oauthAuthorizeUrl}
          field={MCP_COPY_FIELD.OAUTH_AUTHORIZE_URL}
          copiedField={copiedField}
          onCopyText={onCopyText}
        />
        <CopyableUrlRow
          label="OAuth Token URL"
          hint="Hỗ trợ PKCE S256 & Refresh Rotation"
          value={connector.oauthTokenUrl}
          field={MCP_COPY_FIELD.OAUTH_TOKEN_URL}
          copiedField={copiedField}
          onCopyText={onCopyText}
        />
      </div>
    </GlassPanel>
  );
}
