import {
  mcpCatalogListResponseSchema,
  mcpConnectionItemSchema,
  mcpConnectionListResponseSchema,
  mcpConnectorSchema,
  rotateMcpSecretResponseSchema,
  type McpCatalogListResponse,
  type McpConnectionItem,
  type McpConnectionListResponse,
  type McpConnector,
  type RotateMcpSecretResponse,
} from "@/entities/mcp-connector/model/mcp-connector-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export const mcpConnectorApi = {
  async getConnector(): Promise<McpConnector> {
    return mcpConnectorSchema.parse(await apiClient.get<unknown>(API_ROUTE.mcp.connector));
  },

  async rotateSecret(): Promise<RotateMcpSecretResponse> {
    return rotateMcpSecretResponseSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.mcp.rotateSecret),
    );
  },

  async listMcps(): Promise<McpCatalogListResponse> {
    return mcpCatalogListResponseSchema.parse(await apiClient.get<unknown>(API_ROUTE.mcp.mcps));
  },

  async listConnections(): Promise<McpConnectionListResponse> {
    return mcpConnectionListResponseSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.mcp.connections),
    );
  },

  async revokeConnection(connectionId: number): Promise<McpConnectionItem> {
    return mcpConnectionItemSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.mcp.revokeConnection(connectionId)),
    );
  },
} as const;
