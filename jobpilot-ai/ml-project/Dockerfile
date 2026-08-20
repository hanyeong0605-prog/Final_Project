FROM python:3.11-slim

WORKDIR /app

# NanumGothic renders Korean keywords inside the generated PNG.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        fonts-nanum \
        libgl1 \
        libglib2.0-0 \
        libxcb1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.prod.txt .
RUN pip install --no-cache-dir -r requirements.prod.txt

COPY app.py .
# Cache files are optional seed data.  Git does not retain an empty directory,
# so copying it makes a clean production checkout fail to build.
RUN mkdir -p /app/cache /app/runtime-cache /app/admin_photos

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
