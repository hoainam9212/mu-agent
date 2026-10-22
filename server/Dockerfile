FROM python:3.12-slim

WORKDIR /app
COPY pyproject.toml ./
COPY vaohang ./vaohang
RUN pip install --no-cache-dir ".[fcm,postgres]"

# Cloud Run truyền cổng qua $PORT.
ENV PORT=8080
CMD ["sh", "-c", "uvicorn --factory vaohang.main:create_app --host 0.0.0.0 --port ${PORT}"]
