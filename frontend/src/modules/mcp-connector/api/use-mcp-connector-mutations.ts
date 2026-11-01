import { mcpConnectorApi, mcpConnectorKeys } from "@/entities/mcp-connector";
import { useMutation, useQueryClient } from "@tanstack/react-query";

export function useRotateMcpSecretMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => mcpConnectorApi.rotateSecret(),
    onSuccess: (data) => {
      queryClient.setQueryData(mcpConnectorKeys.connector(), data.connector);
    },
  });
}

export function useRevokeMcpConnectionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (connectionId: number) => mcpConnectorApi.revokeConnection(connectionId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: mcpConnectorKeys.connections() });
    },
  });
}
