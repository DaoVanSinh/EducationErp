import { useMcpConnectorController } from "@/modules/mcp-connector/hooks/use-mcp-connector-controller";
import { McpClientGuideCard } from "@/modules/mcp-connector/ui/mcp-client-guide-card";
import { McpConnectionsCard } from "@/modules/mcp-connector/ui/mcp-connections-card";
import { McpCredentialsCard } from "@/modules/mcp-connector/ui/mcp-credentials-card";
import { McpIdeConfigCard } from "@/modules/mcp-connector/ui/mcp-ide-config-card";
import { McpUrlsCard } from "@/modules/mcp-connector/ui/mcp-urls-card";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";

export function McpConnectorPage() {
  const controller = useMcpConnectorController();

  if (controller.isLoading) {
    return (
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Kết nối AI (MCP)"
          description="Cấu hình cổng kết nối Model Context Protocol cho Claude, ChatGPT và AI IDE theo phân quyền DS Academy."
        />
        <Skeleton className="h-48" />
        <Skeleton className="h-56" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  if (controller.isError || !controller.connector) {
    return (
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Kết nối AI (MCP)"
          description="Cấu hình cổng kết nối Model Context Protocol cho Claude, ChatGPT và AI IDE theo phân quyền DS Academy."
        />
        <ErrorNotice
          error={
            controller.error ??
            new Error(
              "Không thể tải thông tin cổng kết nối MCP. Vui lòng kiểm tra dịch vụ MCP và thử lại.",
            )
          }
        />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Kết nối AI (MCP)"
        description="Kết nối tài khoản DS Academy của bạn với Claude, ChatGPT và AI IDE. Mọi thao tác công cụ đều tuân thủ phân quyền RBAC trực tiếp."
      />

      <McpUrlsCard
        connector={controller.connector}
        mcps={controller.mcps}
        copiedField={controller.copiedField}
        onCopyText={controller.onCopyText}
      />

      <McpCredentialsCard
        connector={controller.connector}
        revealedSecret={controller.revealedSecret}
        revealedBearerToken={controller.revealedBearerToken}
        secretPreview={controller.secretPreview}
        bearerTokenPreview={controller.bearerTokenPreview}
        copiedField={controller.copiedField}
        isRotatingSecret={controller.isRotatingSecret}
        onCopyText={controller.onCopyText}
        onRotateSecret={controller.onRotateSecret}
        onClearRevealedSecret={controller.onClearRevealedSecret}
      />

      <McpIdeConfigCard
        activeIdeTab={controller.activeIdeTab}
        ideSnippet={controller.ideSnippet}
        copiedField={controller.copiedField}
        onSelectIdeTab={controller.onSelectIdeTab}
        onCopyText={controller.onCopyText}
      />

      <McpConnectionsCard
        connections={controller.connections}
        revokingConnectionId={controller.revokingConnectionId}
        onRevokeConnection={controller.onRevokeConnection}
      />

      <McpClientGuideCard />
    </div>
  );
}
