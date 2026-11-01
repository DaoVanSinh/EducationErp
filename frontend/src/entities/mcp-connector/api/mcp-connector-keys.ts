export const mcpConnectorKeys = {
  all: ["mcp-connector"] as const,
  connector: () => [...mcpConnectorKeys.all, "connector"] as const,
  mcps: () => [...mcpConnectorKeys.all, "mcps"] as const,
  connections: () => [...mcpConnectorKeys.all, "connections"] as const,
} as const;
