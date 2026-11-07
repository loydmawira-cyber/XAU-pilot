FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Build context is the repository root (see backend/docker-compose.yml) so the
# package path matches the `backend.app...` imports used by the code.
COPY backend/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY backend /app/backend

RUN useradd --create-home appuser && mkdir -p /var/ea_packages && chown appuser /var/ea_packages
USER appuser

ENV EA_STORAGE_DIR=/var/ea_packages
EXPOSE 8000

CMD ["uvicorn", "backend.app.main:app", "--host", "0.0.0.0", "--port", "8000"]
