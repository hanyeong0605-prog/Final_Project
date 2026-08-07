FROM python:3.11-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# openai-whisper's source build still imports pkg_resources. Recent setuptools
# releases removed it, so keep a compatible build toolchain and disable the
# isolated build environment for this dependency installation.
RUN pip install --no-cache-dir "setuptools<81" wheel \
    && pip install --no-cache-dir --no-build-isolation -r requirements.txt
COPY app ./app

EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
