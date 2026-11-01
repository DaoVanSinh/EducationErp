import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig, loadEnv } from "vite";

/**
 * Dev server proxy hết /api, /mcp, /oauth, /.well-known sang Backend & MCP:
 * nhờ vậy trình duyệt coi frontend và API là cùng origin, nên cookie phiên (HttpOnly, SameSite=Strict)
 * và cookie CSRF đi qua được mà không bị chặn khi chạy qua tên miền riêng hoặc tunnel.
 */
const DEV_SERVER = {
  port: 5173,
  apiTarget: "http://localhost:8080",
  mcpTarget: "http://localhost:8700",
  apiPrefix: "/api",
  mcpApiPrefix: "/api/mcp",
  mcpPrefix: "/mcp",
  oauthPrefix: "/oauth",
  wellKnownPrefix: "/.well-known",
} as const;

function parseAllowedHosts(raw: string | undefined): true | string[] {
  const trimmed = (raw ?? "").trim();
  if (!trimmed || trimmed === "*" || trimmed === "true") {
    return true;
  }
  const hosts = trimmed
    .split(",")
    .map((host) => host.trim())
    .filter(Boolean);
  return hosts.length > 0 ? hosts : true;
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiTarget = env.BACKEND_ORIGIN || DEV_SERVER.apiTarget;
  const mcpTarget = env.MCP_ORIGIN || DEV_SERVER.mcpTarget;
  const allowedHosts = parseAllowedHosts(env.DEV_ALLOWED_HOSTS);

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      host: "0.0.0.0",
      port: env.PORT ? Number(env.PORT) : DEV_SERVER.port,
      allowedHosts,
      cors: true,
      proxy: {
        [DEV_SERVER.mcpApiPrefix]: {
          target: mcpTarget,
          changeOrigin: false,
        },
        [DEV_SERVER.mcpPrefix]: {
          target: mcpTarget,
          changeOrigin: false,
        },
        [DEV_SERVER.oauthPrefix]: {
          target: mcpTarget,
          changeOrigin: false,
        },
        [DEV_SERVER.wellKnownPrefix]: {
          target: mcpTarget,
          changeOrigin: false,
        },
        [DEV_SERVER.apiPrefix]: {
          target: apiTarget,
          changeOrigin: false,
        },
      },
    },
    preview: {
      host: "0.0.0.0",
      allowedHosts,
      cors: true,
    },
  };
});
