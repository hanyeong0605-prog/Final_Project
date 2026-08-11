FROM python:3.11-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# 2026-08-07: openai-whisper를 requirements.txt에서 뺐다(audio_analysis.py 참고 - STT를
# Google Cloud Speech-to-Text REST API로 교체). 이 setuptools 고정 + --no-build-isolation은
# 원래 whisper의 소스 빌드가 pkg_resources를 요구해서 필요했던 워크어라운드였는데, 지금은
# 필요 없어졌을 가능성이 높다. 다만 실제 도커 빌드(특히 torch/transformers/tokenizers 조합)를
# 이 자리에서 다시 검증할 방법이 없어서(용량이 커서 여기서 못 돌려봄) 배포 직전 불필요한
# 위험을 만들지 않으려고 일단 그대로 남겨뒀다 - 다른 패키지에 해가 되진 않는다. 다음에 여유
# 있을 때 빼도 되는지 실제로 테스트해봐도 됨.
# EC2 has no NVIDIA GPU.  PyPI's current torch wheel may pull several GB of
# CUDA/NVIDIA dependencies, which exhausts the small production disk.
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.5.1+cpu \
    && pip install --no-cache-dir "setuptools<81" wheel \
    && pip install --no-cache-dir --no-build-isolation -r requirements.txt
COPY app ./app

EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
