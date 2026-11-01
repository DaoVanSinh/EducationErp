export const MCP_IDE_TAB = {
  ANTIGRAVITY_CURSOR: "antigravity_cursor",
  CLAUDE_CODE: "claude_code",
  VSCODE: "vscode",
} as const;

export type McpIdeTab = (typeof MCP_IDE_TAB)[keyof typeof MCP_IDE_TAB];

export const MCP_IDE_TAB_OPTIONS: ReadonlyArray<{
  readonly id: McpIdeTab;
  readonly label: string;
  readonly fileHint: string;
}> = [
  {
    id: MCP_IDE_TAB.ANTIGRAVITY_CURSOR,
    label: "Antigravity / Cursor",
    fileHint: "~/.gemini/antigravity/mcp_config.json hoặc .cursor/mcp.json",
  },
  {
    id: MCP_IDE_TAB.CLAUDE_CODE,
    label: "Claude Code CLI",
    fileHint: "Chạy lệnh trong Terminal",
  },
  {
    id: MCP_IDE_TAB.VSCODE,
    label: "VS Code (Copilot MCP)",
    fileHint: ".vscode/mcp.json",
  },
];

export const MCP_COPY_FIELD = {
  MCP_URL: "mcp_url",
  OAUTH_AUTHORIZE_URL: "oauth_authorize_url",
  OAUTH_TOKEN_URL: "oauth_token_url",
  CLIENT_ID: "client_id",
  CLIENT_SECRET: "client_secret",
  BEARER_TOKEN: "bearer_token",
  IDE_SNIPPET: "ide_snippet",
} as const;

export type McpCopyField = (typeof MCP_COPY_FIELD)[keyof typeof MCP_COPY_FIELD];

export const MCP_CONNECTION_STATUS = {
  ACTIVE: "ACTIVE",
  REVOKED: "REVOKED",
} as const;

export type McpConnectionStatus =
  (typeof MCP_CONNECTION_STATUS)[keyof typeof MCP_CONNECTION_STATUS];

export const MCP_COPY_RESET_DELAY_MS = 2000;
