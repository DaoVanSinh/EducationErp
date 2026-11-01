"""Template loader and context builder for the DS Academy OAuth consent and error pages."""

from __future__ import annotations

import re
from html import escape
from pathlib import Path

from app.platform.oauth.constants import OAuthConstants
from app.shared.base.markers import helper

_IF_ERROR_PATTERN = re.compile(
    r"\{%\s*if\s+is_error\s*%\}(.*?)\{%\s*else\s*%\}(.*?)\{%\s*endif\s*%\}",
    re.DOTALL,
)
_FOR_FIELDS_PATTERN = re.compile(
    r"\{%\s*for\s+field\s+in\s+hidden_fields\s*%\}(.*?)\{%\s*endfor\s*%\}",
    re.DOTALL,
)
_VAR_PATTERN = re.compile(r"\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}")


class OAuthConsentRenderer:
    """Loads and renders ``templates/consent.html`` and ``templates/logo.png``."""

    _DIRECTORY = Path(__file__).resolve().parent / OAuthConstants.Templates.DIRECTORY
    _TEMPLATE_TEXT: str | None = None
    _LOGO_BYTES: bytes | None = None

    @classmethod
    @helper
    def _load_template(cls) -> str:
        if cls._TEMPLATE_TEXT is None:
            cls._TEMPLATE_TEXT = (
                cls._DIRECTORY / OAuthConstants.Templates.CONSENT
            ).read_text(encoding="utf-8")
        return cls._TEMPLATE_TEXT

    @classmethod
    @helper
    def logo_bytes(cls) -> bytes:
        """Read the bundled DS Academy PNG logo once."""
        if cls._LOGO_BYTES is None:
            cls._LOGO_BYTES = (
                cls._DIRECTORY / OAuthConstants.Templates.LOGO_FILE
            ).read_bytes()
        return cls._LOGO_BYTES

    @staticmethod
    @helper
    def _base_context(*, title: str, heading: str, is_error: bool) -> dict[str, object]:
        copy = OAuthConstants.ConsentCopy
        return {
            "page_title": title,
            "heading": heading,
            "is_error": is_error,
            "brand_name": copy.BRAND_NAME,
            "badge_protocol": copy.BADGE_PROTOCOL,
            "footer_left": copy.FOOTER_LEFT,
            "footer_right": copy.FOOTER_RIGHT,
        }

    @classmethod
    @helper
    def render_consent(
        cls,
        *,
        client_id: str,
        account: str,
        mcp_title: str,
        hidden_fields: dict[str, str],
    ) -> str:
        """Render the OAuth 2.1 consent dialog for an authenticated DS Academy account."""
        copy = OAuthConstants.ConsentCopy
        fields_list = [
            {"name": key, "value": val} for key, val in hidden_fields.items()
        ]
        context: dict[str, object] = {
            **cls._base_context(
                title=copy.TITLE,
                heading=copy.HEADING,
                is_error=False,
            ),
            "intro": copy.INTRO,
            "request_verb": copy.REQUEST,
            "account_verb": copy.ACCOUNT,
            "note": copy.NOTE,
            "approve_label": copy.APPROVE,
            "deny_label": copy.DENY,
            "label_mcp": copy.LABEL_MCP,
            "label_account": copy.LABEL_ACCOUNT,
            "client_id": client_id,
            "mcp_title": mcp_title,
            "account": account,
            "authorize_action": OAuthConstants.Routes.AUTHORIZE,
            "decision_field": OAuthConstants.Decisions.FIELD,
            "approve_value": OAuthConstants.Decisions.APPROVE,
            "deny_value": OAuthConstants.Decisions.DENY,
            "hidden_fields": fields_list,
            "description": "",
        }
        return cls._render_context(context)

    @classmethod
    @helper
    def render_error(cls, description: str) -> str:
        """Render the OAuth 2.1 error dialog using the same liquid-glass template."""
        copy = OAuthConstants.ConsentCopy
        context: dict[str, object] = {
            **cls._base_context(
                title=copy.ERROR_TITLE,
                heading=copy.ERROR_HEADING,
                is_error=True,
            ),
            "description": description,
            "hidden_fields": [],
        }
        return cls._render_context(context)

    @classmethod
    @helper
    def _render_context(cls, context: dict[str, object]) -> str:
        raw = cls._load_template()
        is_error = bool(context.get("is_error"))
        rendered = _IF_ERROR_PATTERN.sub(
            lambda m: m.group(1) if is_error else m.group(2),
            raw,
        )
        hidden_fields = context.get("hidden_fields")
        fields_list = hidden_fields if isinstance(hidden_fields, list) else []

        def _expand_fields(match: re.Match[str]) -> str:
            block = match.group(1)
            parts: list[str] = []
            for item in fields_list:
                if isinstance(item, dict):
                    line = block.replace(
                        "{{ field.name }}", escape(str(item.get("name", "")), quote=True)
                    ).replace(
                        "{{ field.value }}", escape(str(item.get("value", "")), quote=True)
                    )
                    parts.append(line.strip())
            return "\n        ".join(parts)

        rendered = _FOR_FIELDS_PATTERN.sub(_expand_fields, rendered)
        return _VAR_PATTERN.sub(
            lambda m: escape(str(context.get(m.group(1), "")), quote=True),
            rendered,
        )
