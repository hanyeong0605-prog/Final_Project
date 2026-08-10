# Word Cloud FastAPI service

This service responds immediately with a cached word-cloud image, then refreshes
the cache from the latest `job_requirements` data in the background. It never runs
TF-IDF during a browser request.

`cache/wordcloud_cache.json` is a versioned seed/fallback cache. Fresh data is written
to the ignored `runtime-cache/wordcloud_cache.json`, so automatic refreshes do not dirty Git.
The production Docker image copies the seed cache and installs NanumGothic.

## Local Windows setup

Use Python 3.11, then create the virtual environment and install the runtime dependencies once.

```bat
C:\Python311\python.exe -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.prod.txt
```

Start the local API with `start-local.bat`. The script checks the virtual environment,
the seed cache file, and a Korean font before it starts Uvicorn. If the project `.env`
contains DB credentials, the service refreshes from the local MySQL database at startup.

```bat
start-local.bat
```

The health endpoint is available at `http://127.0.0.1:8000/health` and the image
endpoint is `http://127.0.0.1:8000/api/wordcloud?importance=all`.

## Cache refresh policy

The service refreshes once at startup and then every six hours by default. Configure it
with environment variables when needed:

```env
WORDCLOUD_CACHE_REFRESH_ENABLED=true
WORDCLOUD_CACHE_REFRESH_ON_START=true
WORDCLOUD_CACHE_REFRESH_INTERVAL_MINUTES=360
```

If the database is temporarily unavailable, the last usable runtime cache or the versioned
seed cache remains available. Do not add `cache/wordcloud_cache.json` to `.gitignore`.
