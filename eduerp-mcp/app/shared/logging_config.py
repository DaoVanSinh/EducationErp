"""Structured logging configuration for EduERP MCP."""

from __future__ import annotations

import logging
import sys

import structlog


class LogColors:
    RED = "\x1b[31m"
    YELLOW = "\x1b[33m"
    GREEN = "\x1b[32m"


class LogConfigDefaults:
    TIMESTAMP_FORMAT = "%Y-%m-%d %H:%M:%S"
    ISO_TIMESTAMP = "iso"
    UVICORN_ACCESS_LOGGER = "uvicorn.access"


class LogLevelStyles:
    VALUES: dict[str, str] = {
        "critical": LogColors.RED,
        "exception": LogColors.RED,
        "error": LogColors.RED,
        "warn": LogColors.YELLOW,
        "warning": LogColors.YELLOW,
        "info": LogColors.GREEN,
        "debug": LogColors.GREEN,
    }


def configure_logging(level: str = "info", *, console: bool = True) -> None:
    """Colored console lines with local timestamps on stderr; JSON (UTC) is an opt-in."""
    for noisy in ("httpx", "httpcore"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
    logging.getLogger(LogConfigDefaults.UVICORN_ACCESS_LOGGER).disabled = True
    timestamper = (
        structlog.processors.TimeStamper(fmt=LogConfigDefaults.TIMESTAMP_FORMAT, utc=False)
        if console
        else structlog.processors.TimeStamper(fmt=LogConfigDefaults.ISO_TIMESTAMP, utc=True)
    )
    renderer = (
        structlog.dev.ConsoleRenderer(
            colors=True,
            level_styles=LogLevelStyles.VALUES,
            exception_formatter=structlog.dev.plain_traceback,
        )
        if console
        else structlog.processors.JSONRenderer()
    )
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.StackInfoRenderer(),
            structlog.processors.format_exc_info,
            timestamper,
            renderer,
        ],
        wrapper_class=structlog.make_filtering_bound_logger(
            logging.getLevelNamesMapping()[level.upper()]
        ),
        logger_factory=structlog.PrintLoggerFactory(file=sys.stderr),
        cache_logger_on_first_use=False,
    )


def get_logger(name: str):
    return structlog.get_logger(name)
