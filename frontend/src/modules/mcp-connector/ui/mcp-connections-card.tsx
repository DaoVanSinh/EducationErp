import type { McpConnectionItem } from "@/entities/mcp-connector";
import { MCP_CONNECTION_STATUS } from "@/modules/mcp-connector/constants/mcp-connector-constants";
import { resolveConnectionStatus } from "@/modules/mcp-connector/model/mcp-config-snippets";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Link2, ShieldOff } from "lucide-react";

export interface McpConnectionsCardProps {
  readonly connections: readonly McpConnectionItem[];
  readonly revokingConnectionId: number | null;
  readonly onRevokeConnection: (connectionId: number) => void;
}

function ConnectionRow({
  item,
  isRevoking,
  onRevokeConnection,
}: {
  readonly item: McpConnectionItem;
  readonly isRevoking: boolean;
  readonly onRevokeConnection: (connectionId: number) => void;
}) {
  const status = resolveConnectionStatus(item);
  const isActive = status === MCP_CONNECTION_STATUS.ACTIVE;
  const badgeTone = isActive ? "positive" : "neutral";
  const badgeText = isActive ? "Đang hoạt động" : "Đã thu hồi";

  return (
    <div className="flex flex-col justify-between gap-3 rounded-2xl border border-slate-200/80 bg-white/60 p-4 sm:flex-row sm:items-center">
      <div className="flex flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-semibold text-slate-900">{item.appName}</span>
          <Badge tone={badgeTone}>{badgeText}</Badge>
          {item.appOrigin ? (
            <span className="text-xs text-slate-500">({item.appOrigin})</span>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
          <span>Cấp quyền: {formatter.dateTime(item.createdAt)}</span>
          {item.lastUsedAt ? (
            <span>Sử dụng gần nhất: {formatter.dateTime(item.lastUsedAt)}</span>
          ) : null}
        </div>
      </div>

      {isActive ? (
        <GlassButton
          variant="danger"
          size="sm"
          loading={isRevoking}
          icon={<ShieldOff size={14} aria-hidden />}
          onClick={() => onRevokeConnection(item.id)}
        >
          Thu hồi quyền
        </GlassButton>
      ) : null}
    </div>
  );
}

export function McpConnectionsCard({
  connections,
  revokingConnectionId,
  onRevokeConnection,
}: McpConnectionsCardProps) {
  return (
    <GlassPanel className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-orange-50 text-orange-600">
          <Link2 size={20} aria-hidden />
        </span>
        <div>
          <h2 className="text-base font-bold text-slate-900">
            Ứng dụng AI đã kết nối ({formatter.count(connections.length)})
          </h2>
          <p className="text-sm text-slate-500">
            Danh sách các phiên OAuth 2.1 (Claude, ChatGPT) và IDE sử dụng Connector của bạn.
          </p>
        </div>
      </div>

      {connections.length === 0 ? (
        <EmptyState
          icon={<Link2 size={28} aria-hidden />}
          title="Chưa có ứng dụng AI nào kết nối"
          description="Khi bạn kết nối Claude, ChatGPT hoặc AI IDE, ứng dụng sẽ xuất hiện tại đây để bạn theo dõi và thu hồi quyền bất cứ lúc nào."
        />
      ) : (
        <div className="flex flex-col gap-3">
          {connections.map((item) => (
            <ConnectionRow
              key={item.id}
              item={item}
              isRevoking={revokingConnectionId === item.id}
              onRevokeConnection={onRevokeConnection}
            />
          ))}
        </div>
      )}
    </GlassPanel>
  );
}
