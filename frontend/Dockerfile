# ── Build stage ───────────────────────────────────────────────────────────
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

# ── Runtime stage (Dependency-free Node server, behind deploy/nginx.conf) ──
FROM node:22-alpine AS runner
ENV NODE_ENV=production \
    PORT=8080 \
    HOST=0.0.0.0
WORKDIR /app

COPY --chown=root:root server.mjs ./
COPY --from=builder --chown=root:root /app/dist ./dist

USER node
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD ["node", "-e", "fetch('http://127.0.0.1:8080/frontend-healthz').then(r=>process.exit(r.ok?0:1),()=>process.exit(1))"]

CMD ["node", "server.mjs"]
