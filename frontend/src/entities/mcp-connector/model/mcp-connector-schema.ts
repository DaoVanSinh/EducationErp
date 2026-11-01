import { z } from "zod";

export const mcpConnectorSchema = z.object({
  clientId: z.string(),
  hasSecret: z.boolean(),
  secretLast4: z.string(),
  enabled: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  lastUsedAt: z.string().nullable(),
  secretRotatedAt: z.string().nullable(),
  oauthAuthorizeUrl: z.string(),
  oauthTokenUrl: z.string(),
  mcpBaseUrl: z.string(),
});

export type McpConnector = z.infer<typeof mcpConnectorSchema>;

export const rotateMcpSecretResponseSchema = z.object({
  connector: mcpConnectorSchema,
  clientSecret: z.string(),
  bearerToken: z.string(),
});

export type RotateMcpSecretResponse = z.infer<typeof rotateMcpSecretResponseSchema>;

export const mcpCatalogItemSchema = z.object({
  id: z.number(),
  code: z.string(),
  name: z.string(),
  description: z.string(),
  routePrefix: z.string(),
  mcpUrl: z.string(),
  enabled: z.boolean(),
});

export type McpCatalogItem = z.infer<typeof mcpCatalogItemSchema>;

export const mcpCatalogListResponseSchema = z.object({
  items: z.array(mcpCatalogItemSchema),
});

export type McpCatalogListResponse = z.infer<typeof mcpCatalogListResponseSchema>;

export const mcpConnectionItemSchema = z.object({
  id: z.number(),
  clientId: z.string(),
  appKey: z.string(),
  appName: z.string(),
  appOrigin: z.string(),
  redirectUri: z.string(),
  scope: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  lastUsedAt: z.string().nullable(),
  revokedAt: z.string().nullable(),
});

export type McpConnectionItem = z.infer<typeof mcpConnectionItemSchema>;

export const mcpConnectionListResponseSchema = z.object({
  items: z.array(mcpConnectionItemSchema),
});

export type McpConnectionListResponse = z.infer<typeof mcpConnectionListResponseSchema>;
