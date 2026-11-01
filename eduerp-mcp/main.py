"""Entrypoint — configure logging, build the Starlette app, and run uvicorn."""

import uvicorn

from app.api.router import create_app
from app.core.config import Settings
from app.shared.logging_config import configure_logging


def main() -> None:
    cfg = Settings()
    configure_logging("info")
    # Single worker: FastMCP Streamable HTTP sessions live in process memory.
    uvicorn.run(
        create_app(cfg),
        host=cfg.host,
        port=cfg.port,
        proxy_headers=True,
        forwarded_allow_ips=cfg.trusted_proxy_ips,
        access_log=False,
    )


if __name__ == "__main__":
    main()
