import type { McpConnectionItem, McpConnector } from "@/entities/mcp-connector";
import {
  MCP_CONNECTION_STATUS,
  MCP_IDE_TAB,
  type McpConnectionStatus,
  type McpIdeTab,
} from "@/modules/mcp-connector/constants/mcp-connector-constants";

const MASKED_SECRET_PLACEHOLDER = "<CLIENT_SECRET_CỦA_BẠN>";

export function resolveSecretPreview(
  connector: McpConnector,
  revealedSecret: string | null,
): string {
  if (revealedSecret) {
    return revealedSecret;
  }
  if (connector.hasSecret) {
    return `sec_••••••••••••••••••••••••••••${connector.secretLast4}`;
  }
  return "Chưa khởi tạo — bấm «Tạo / Đổi Client Secret» để sinh khóa";
}

export function resolveBearerTokenDisplay(
  connector: McpConnector,
  revealedBearerToken: string | null,
): string {
  if (revealedBearerToken) {
    return revealedBearerToken;
  }
  if (connector.hasSecret) {
    return `${connector.clientId}.sec_••••••••••••••••••••${connector.secretLast4}`;
  }
  return `${connector.clientId}.${MASKED_SECRET_PLACEHOLDER}`;
}

export function buildIdeConfigSnippet(
  tab: McpIdeTab,
  mcpUrl: string,
  clientId: string,
  revealedSecret: string | null,
): string {
  const tokenPart = revealedSecret
    ? `${clientId}.${revealedSecret}`
    : `${clientId}.${MASKED_SECRET_PLACEHOLDER}`;

  if (tab === MCP_IDE_TAB.CLAUDE_CODE) {
    return [
      `claude mcp add --transport http eduerp "${mcpUrl}" \\`,
      `  --header "Authorization: Bearer ${tokenPart}"`,
    ].join("\n");
  }

  if (tab === MCP_IDE_TAB.VSCODE) {
    return JSON.stringify(
      {
        servers: {
          eduerp: {
            type: "http",
            url: mcpUrl,
            headers: {
              Authorization: `Bearer ${tokenPart}`,
            },
          },
        },
      },
      null,
      2,
    );
  }

  return JSON.stringify(
    {
      mcpServers: {
        eduerp: {
          serverUrl: mcpUrl,
          headers: {
            Authorization: `Bearer ${tokenPart}`,
          },
        },
      },
    },
    null,
    2,
  );
}

export function resolveConnectionStatus(item: McpConnectionItem): McpConnectionStatus {
  if (item.revokedAt) {
    return MCP_CONNECTION_STATUS.REVOKED;
  }
  return MCP_CONNECTION_STATUS.ACTIVE;
}
