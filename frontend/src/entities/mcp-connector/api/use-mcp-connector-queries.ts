import { mcpConnectorApi } from "@/entities/mcp-connector/api/mcp-connector-api";
import { mcpConnectorKeys } from "@/entities/mcp-connector/api/mcp-connector-keys";
import { useQuery } from "@tanstack/react-query";

export function useMyMcpConnector() {
  return useQuery({
    queryKey: mcpConnectorKeys.connector(),
    queryFn: () => mcpConnectorApi.getConnector(),
  });
}

export function useMyMcps() {
  return useQuery({
    queryKey: mcpConnectorKeys.mcps(),
    queryFn: () => mcpConnectorApi.listMcps(),
  });
}

export function useMyMcpConnections() {
  return useQuery({
    queryKey: mcpConnectorKeys.connections(),
    queryFn: () => mcpConnectorApi.listConnections(),
  });
}
