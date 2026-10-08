import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

/**
 * Dev server proxy hết /api sang Spring: nhờ vậy trình duyệt coi frontend và API là cùng origin,
 * nên cookie phiên (HttpOnly, SameSite=Strict) và cookie CSRF đi qua được mà không cần cấu hình CORS
 * hay nới SameSite - hai thứ chỉ cần cho lúc dev rồi lại phải nhớ tắt khi lên thật.
 */
const DEV_SERVER = {
  port: 5173,
  apiTarget: "http://localhost:8080",
  apiPrefix: "/api",
} as const;

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: DEV_SERVER.port,
    proxy: {
      [DEV_SERVER.apiPrefix]: {
        target: DEV_SERVER.apiTarget,
        changeOrigin: false,
      },
    },
  },
});
