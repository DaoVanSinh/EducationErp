import {
  useMyMcpConnections,
  useMyMcpConnector,
  useMyMcps,
  type McpCatalogItem,
  type McpConnectionItem,
  type McpConnector,
} from "@/entities/mcp-connector";
import {
  useRevokeMcpConnectionMutation,
  useRotateMcpSecretMutation,
} from "@/modules/mcp-connector/api/use-mcp-connector-mutations";
import {
  MCP_COPY_FIELD,
  MCP_COPY_RESET_DELAY_MS,
  MCP_IDE_TAB,
  type McpCopyField,
  type McpIdeTab,
} from "@/modules/mcp-connector/constants/mcp-connector-constants";
import {
  buildIdeConfigSnippet,
  resolveBearerTokenDisplay,
  resolveSecretPreview,
} from "@/modules/mcp-connector/model/mcp-config-snippets";
import { useCallback, useEffect, useRef, useState } from "react";

export interface McpConnectorController {
  readonly isLoading: boolean;
  readonly isError: boolean;
  readonly error: unknown;
  readonly connector: McpConnector | null;
  readonly mcps: readonly McpCatalogItem[];
  readonly connections: readonly McpConnectionItem[];
  readonly revealedSecret: string | null;
  readonly revealedBearerToken: string | null;
  readonly secretPreview: string;
  readonly bearerTokenPreview: string;
  readonly activeIdeTab: McpIdeTab;
  readonly ideSnippet: string;
  readonly copiedField: McpCopyField | null;
  readonly isRotatingSecret: boolean;
  readonly revokingConnectionId: number | null;
  readonly onSelectIdeTab: (tab: McpIdeTab) => void;
  readonly onCopyText: (field: McpCopyField, value: string) => void;
  readonly onRotateSecret: () => void;
  readonly onClearRevealedSecret: () => void;
  readonly onRevokeConnection: (connectionId: number) => void;
}

export function useMcpConnectorController(): McpConnectorController {
  const connectorQuery = useMyMcpConnector();
  const mcpsQuery = useMyMcps();
  const connectionsQuery = useMyMcpConnections();

  const rotateSecretMutation = useRotateMcpSecretMutation();
  const revokeConnectionMutation = useRevokeMcpConnectionMutation();

  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [revealedBearerToken, setRevealedBearerToken] = useState<string | null>(null);
  const [activeIdeTab, setActiveIdeTab] = useState<McpIdeTab>(MCP_IDE_TAB.ANTIGRAVITY_CURSOR);
  const [copiedField, setCopiedField] = useState<McpCopyField | null>(null);
  const [revokingConnectionId, setRevokingConnectionId] = useState<number | null>(null);

  const copyTimerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (copyTimerRef.current !== null) {
        window.clearTimeout(copyTimerRef.current);
      }
    };
  }, []);

  const onCopyText = useCallback((field: McpCopyField, value: string) => {
    if (!value) {
      return;
    }
    void navigator.clipboard.writeText(value);
    setCopiedField(field);
    if (copyTimerRef.current !== null) {
      window.clearTimeout(copyTimerRef.current);
    }
    copyTimerRef.current = window.setTimeout(() => {
      setCopiedField(null);
    }, MCP_COPY_RESET_DELAY_MS);
  }, []);

  const onRotateSecret = useCallback(() => {
    rotateSecretMutation.mutate(undefined, {
      onSuccess: (result) => {
        setRevealedSecret(result.clientSecret);
        setRevealedBearerToken(result.bearerToken);
      },
    });
  }, [rotateSecretMutation]);

  const onClearRevealedSecret = useCallback(() => {
    setRevealedSecret(null);
    setRevealedBearerToken(null);
  }, []);

  const onRevokeConnection = useCallback(
    (connectionId: number) => {
      setRevokingConnectionId(connectionId);
      revokeConnectionMutation.mutate(connectionId, {
        onSettled: () => {
          setRevokingConnectionId(null);
        },
      });
    },
    [revokeConnectionMutation],
  );

  const connector = connectorQuery.data ?? null;
  const mcps = mcpsQuery.data?.items ?? [];
  const connections = connectionsQuery.data?.items ?? [];

  const secretPreview = connector ? resolveSecretPreview(connector, revealedSecret) : "";
  const bearerTokenPreview = connector
    ? resolveBearerTokenDisplay(connector, revealedBearerToken)
    : "";
  const ideSnippet = connector
    ? buildIdeConfigSnippet(
        activeIdeTab,
        connector.mcpBaseUrl,
        connector.clientId,
        revealedSecret,
      )
    : "";

  return {
    isLoading: connectorQuery.isPending || mcpsQuery.isPending,
    isError: connectorQuery.isError,
    error: connectorQuery.error,
    connector,
    mcps,
    connections,
    revealedSecret,
    revealedBearerToken,
    secretPreview,
    bearerTokenPreview,
    activeIdeTab,
    ideSnippet,
    copiedField,
    isRotatingSecret: rotateSecretMutation.isPending,
    revokingConnectionId,
    onSelectIdeTab: setActiveIdeTab,
    onCopyText,
    onRotateSecret,
    onClearRevealedSecret,
    onRevokeConnection,
  };
}

export { MCP_COPY_FIELD };
