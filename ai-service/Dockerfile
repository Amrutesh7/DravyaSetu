FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_NO_CACHE_DIR=1

WORKDIR /workspace/ai-service

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# CPU-only PyTorch
RUN pip install --no-cache-dir --default-timeout=180 --retries=10 \
    --index-url https://download.pytorch.org/whl/cpu \
    torch==2.5.1 torchvision==0.20.1

# Application dependencies, including headless OpenCV
COPY ai-service/requirements-docker.txt ./requirements-docker.txt

RUN pip install --no-cache-dir --default-timeout=180 --retries=10 \
    -r requirements-docker.txt

# Install Grad-CAM without allowing it to replace headless OpenCV
RUN pip install --no-cache-dir --default-timeout=180 --retries=10 \
    --no-deps grad-cam==1.5.5

COPY ai-service/app ./app
COPY ai-service/artifacts/models ./artifacts/models
COPY shared/constants/plant-classes.json /workspace/shared/constants/plant-classes.json

RUN mkdir -p ./artifacts/gradcam

EXPOSE 8000

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
