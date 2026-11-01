// DS Academy Frontend: production static server for the Vite bundle, behind the edge Nginx
// (deploy/nginx.conf). Every request that reaches this container is a frontend document
// or static asset; the edge sends API, OAuth, MCP, and OpenAPI paths to backend / eduerp-mcp.
// Dependency-free on purpose: the runtime image ships this file and dist/ only.
import { createServer } from "node:http";
import { open, realpath } from "node:fs/promises";
import { pipeline } from "node:stream/promises";
import { dirname, extname, resolve, sep } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

// --- Routes --------------------------------------------------------------------------
const DOCUMENT_FILE = "index.html";
const HEALTH_PATH = "/frontend-healthz";
const HEALTH_BODY = "ok\n";

// --- Methods -------------------------------------------------------------------------
const ALLOWED_METHODS = new Set(["GET", "HEAD"]);
const ALLOW_HEADER = "GET, HEAD";

// --- Caching: hashed build files forever, the document never, anything else revalidates.
const CACHE_DOCUMENT = "no-store";
const CACHE_IMMUTABLE = "public, max-age=31536000, immutable";
const CACHE_REVALIDATE = "no-cache";
const DOCUMENT_PATTERN = /\/index\.html$/;
const HASHED_PATTERN = /^\/assets\/|-[A-Za-z0-9_-]{8}\.[A-Za-z0-9]+$/;

// --- Security headers on every document, asset and error response.
const SECURITY_HEADERS = Object.freeze({
  "X-Frame-Options": "DENY",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "same-origin",
});

// --- Types for the files Vite emits; anything else is an opaque download.
const DEFAULT_TYPE = "application/octet-stream";
const TEXT_TYPE = "text/plain; charset=utf-8";
const MIME_TYPES = Object.freeze({
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".mjs": "application/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
  ".json": "application/json",
  ".txt": TEXT_TYPE,
  ".map": "application/json",
  ".webmanifest": "application/manifest+json",
});

const REASONS = Object.freeze({
  400: "Bad Request",
  404: "Not Found",
  405: "Method Not Allowed",
  500: "Internal Server Error",
});

const KEEP_ALIVE_TIMEOUT_MS = 65_000;
const HEADERS_TIMEOUT_MS = 15_000;
const SHUTDOWN_GRACE_MS = 10_000;

const DEFAULT_PORT = 8080;
const DEFAULT_HOST = "0.0.0.0";
const HERE = dirname(fileURLToPath(import.meta.url));
const DEFAULT_DIST_DIR = resolve(HERE, "dist");

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function cacheControlFor(path) {
  if (DOCUMENT_PATTERN.test(path)) return CACHE_DOCUMENT;
  if (HASHED_PATTERN.test(path)) return CACHE_IMMUTABLE;
  return CACHE_REVALIDATE;
}

export function requestPath(rawUrl) {
  let target = rawUrl ?? "/";
  if (!target.startsWith("/")) {
    const match = /^https?:\/\/[^/?#]*(.*)$/i.exec(target);
    if (!match) throw new HttpError(400, REASONS[400]);
    target = match[1] || "/";
  }
  target = target.split(/[?#]/, 1)[0];
  let decoded;
  try {
    decoded = decodeURIComponent(target);
  } catch {
    throw new HttpError(400, REASONS[400]);
  }
  if (decoded.includes("\0") || decoded.includes("\\"))
    throw new HttpError(400, REASONS[400]);
  const merged = decoded.replace(/\/{2,}/g, "/");
  const segments = merged.split("/");
  if (segments.some((segment) => segment === "." || segment === ".."))
    throw new HttpError(400, REASONS[400]);
  return merged;
}

function resolveStaticFile(distDir, path) {
  const relative = path.replace(/^\/+/, "");
  if (!relative || relative.endsWith("/")) return null;
  if (relative.split("/").some((segment) => segment.startsWith(".")))
    return null;
  const file = resolve(distDir, relative);
  return file.startsWith(distDir + sep) ? file : null;
}

function entityTag(stats) {
  return `"${Math.floor(stats.mtimeMs / 1000).toString(16)}-${stats.size.toString(16)}"`;
}

function notModified(request, etag, lastModified) {
  const match = request.headers["if-none-match"];
  if (match !== undefined)
    return match
      .split(",")
      .map((tag) => tag.trim().replace(/^W\//, ""))
      .some((tag) => tag === "*" || tag === etag);
  return request.headers["if-modified-since"] === lastModified;
}

async function openFile(distDir, file) {
  let handle;
  try {
    const real = await realpath(file);
    if (!real.startsWith(distDir + sep)) return null;
    handle = await open(real, "r");
    const stats = await handle.stat();
    if (stats.isFile()) return { handle, stats };
  } catch {
    // Missing or unreadable: not found.
  }
  await handle?.close();
  return null;
}

function send(response, status, headers, body = "") {
  const payload = Buffer.from(body);
  response.writeHead(status, {
    ...headers,
    "Content-Length": payload.length,
  });
  response.end(response.req.method === "HEAD" ? undefined : payload);
  return response.req.method === "HEAD" ? 0 : payload.length;
}

function sendError(response, status, extra = {}) {
  const reason = REASONS[status] ?? REASONS[500];
  return send(
    response,
    status,
    { ...SECURITY_HEADERS, "Content-Type": TEXT_TYPE, ...extra },
    `${status} ${reason}\n`,
  );
}

async function sendFile(request, response, opened, cacheControl) {
  const { handle, stats } = opened;
  const etag = entityTag(stats);
  const lastModified = stats.mtime.toUTCString();
  const headers = {
    ...SECURITY_HEADERS,
    "Cache-Control": cacheControl,
    ETag: etag,
    "Last-Modified": lastModified,
  };
  if (notModified(request, etag, lastModified)) {
    await handle.close();
    response.writeHead(304, headers);
    response.end();
    return 0;
  }
  const type = MIME_TYPES[extname(opened.path).toLowerCase()] ?? DEFAULT_TYPE;
  response.writeHead(200, {
    ...headers,
    "Content-Type": type,
    "Content-Length": stats.size,
  });
  if (request.method === "HEAD") {
    await handle.close();
    response.end();
    return 0;
  }
  try {
    await pipeline(handle.createReadStream(), response);
  } catch {
    // Client disconnected during stream.
  }
  return stats.size;
}

async function serveDocument(request, response, distDir) {
  const path = resolve(distDir, DOCUMENT_FILE);
  const opened = await openFile(distDir, path);
  if (!opened) return sendError(response, 404);
  return sendFile(request, response, { ...opened, path }, CACHE_DOCUMENT);
}

async function route(request, response, distDir) {
  if (!ALLOWED_METHODS.has(request.method)) {
    return sendError(response, 405, {
      Allow: ALLOW_HEADER,
      Connection: "close",
    });
  }
  const path = requestPath(request.url);
  if (path === HEALTH_PATH)
    return send(response, 200, { "Content-Type": TEXT_TYPE }, HEALTH_BODY);

  const candidate = resolveStaticFile(distDir, path);
  if (candidate) {
    const opened = await openFile(distDir, candidate);
    if (opened) {
      return sendFile(
        request,
        response,
        { ...opened, path: candidate },
        cacheControlFor(path),
      );
    }
    if (extname(path)) {
      return sendError(response, 404);
    }
  }
  return serveDocument(request, response, distDir);
}

function loggedPath(rawUrl) {
  return (rawUrl ?? "/").split(/[?#]/, 1)[0].replace(/[^\x21-\x7e]/g, "?");
}

function requestPathSafe(rawUrl) {
  try {
    return requestPath(rawUrl);
  } catch {
    return null;
  }
}

export function createConsoleServer({
  distDir = DEFAULT_DIST_DIR,
  log = console.log,
} = {}) {
  const root = resolve(distDir);
  let realRoot;
  const server = createServer(async (request, response) => {
    const started = process.hrtime.bigint();
    let bytes = 0;
    try {
      realRoot ??= await realpath(root);
      bytes = await route(request, response, realRoot);
    } catch (error) {
      if (!response.headersSent)
        bytes = sendError(
          response,
          error instanceof HttpError ? error.status : 500,
        );
      else response.destroy();
    }
    if (requestPathSafe(request.url) === HEALTH_PATH) return;
    const ms = Number(process.hrtime.bigint() - started) / 1e6;
    log(
      `${request.socket.remoteAddress ?? "-"} [${new Date().toISOString()}] ` +
        `"${request.method} ${loggedPath(request.url)} HTTP/${request.httpVersion}" ` +
        `${response.statusCode} ${bytes} ${ms.toFixed(1)}ms`,
    );
  });
  server.keepAliveTimeout = KEEP_ALIVE_TIMEOUT_MS;
  server.headersTimeout = HEADERS_TIMEOUT_MS;
  return server;
}

function main() {
  const port = Number.parseInt(process.env.PORT ?? "", 10) || DEFAULT_PORT;
  const host = process.env.HOST || DEFAULT_HOST;
  const distDir = process.env.DIST_DIR
    ? resolve(HERE, process.env.DIST_DIR)
    : DEFAULT_DIST_DIR;
  const server = createConsoleServer({ distDir });
  server.listen(port, host, () => {
    console.log(`DS Academy frontend serving ${distDir} on http://${host}:${port}`);
  });
  const shutdown = (signal) => {
    console.log(`${signal} received, closing the frontend server`);
    server.close(() => process.exit(0));
    server.closeIdleConnections();
    setTimeout(() => {
      server.closeAllConnections();
      process.exit(0);
    }, SHUTDOWN_GRACE_MS).unref();
  };
  process.once("SIGTERM", shutdown);
  process.once("SIGINT", shutdown);
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
)
  main();
