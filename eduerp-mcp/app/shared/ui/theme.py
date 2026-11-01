"""Page shell and styles shared by the OAuth 2.1 consent page."""

from __future__ import annotations

from html import escape

STYLE = """
*{box-sizing:border-box}
body{margin:0;background:#f4f7fb;color:#0f172a;font:15px/1.55 "Inter",-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
main{max-width:460px;margin:48px auto;padding:0 16px}
.card{background:#fff;border-radius:16px;padding:32px;border:1px solid #e2e8f0;box-shadow:0 4px 24px rgba(15,23,42,.06)}
.brand{display:flex;align-items:center;gap:10px;font-weight:700;color:#0f172a;margin-bottom:24px}
.logo{width:34px;height:34px;border-radius:10px;background:linear-gradient(135deg,#2563eb,#06b6d4);color:#fff;display:grid;place-items:center;font-weight:700}
h1{font-size:21px;line-height:1.3;margin:0 0 6px}
.sub{margin:0 0 20px;color:#475569;font-size:14px}
.meta{background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:12px 14px;font-size:13px;color:#334155;margin-bottom:16px}
.meta strong{color:#0f172a}
button{width:100%;margin-top:20px;padding:13px;border:0;border-radius:10px;background:#2563eb;color:#fff;font:600 14px inherit;cursor:pointer}
button:hover{background:#1d4ed8}
.err{background:#fef2f2;color:#dc2626;padding:10px 12px;border-radius:8px;font-size:14px;margin:0 0 8px}
.foot{margin:20px 0 0;font-size:12px;color:#64748b;text-align:center}
"""


def page(title: str, inner: str, *, wide: bool = False) -> str:
    width = "960px" if wide else "460px"
    return (
        '<!doctype html><html lang="vi"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        f"<title>{escape(title)}</title><style>{STYLE}</style></head>"
        f'<body><main style="max-width:{width}">{inner}</main></body></html>'
    )
