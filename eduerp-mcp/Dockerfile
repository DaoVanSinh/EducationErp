FROM python:3.12-slim

COPY --from=ghcr.io/astral-sh/uv:0.6 /uv /usr/local/bin/uv

WORKDIR /app
COPY pyproject.toml ./
RUN uv pip install --system --no-cache .

COPY main.py ./
COPY app ./app
COPY scripts ./scripts

RUN chmod +x ./scripts/start.sh \
    && useradd --system --uid 10001 --home /app --no-create-home app \
    && chown -R app:app /app

USER app
EXPOSE 8700
CMD ["./scripts/start.sh"]
