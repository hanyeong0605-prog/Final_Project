# Word Cloud FastAPI service

This service renders word-cloud images from the versioned, precomputed
`cache/wordcloud_cache.json` data. The production Docker image copies that cache
and installs NanumGothic; it does not calculate TF-IDF or query MySQL while serving requests.

## Local Windows setup

Use Python 3.11, then create the virtual environment and install the runtime dependencies once.

```bat
C:\Python311\python.exe -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.prod.txt
```

Start the local API with `start-local.bat`. The script checks the virtual environment,
the repository-managed cache file, and a Korean font before it starts Uvicorn.

```bat
start-local.bat
```

The health endpoint is available at `http://127.0.0.1:8000/health` and the image
endpoint is `http://127.0.0.1:8000/api/wordcloud?importance=all`.

## Cache policy

`cache/wordcloud_cache.json` is source-controlled input to this service, not a Python
build artifact. Do not add it to `.gitignore`. If it is absent after switching branches
or resolving a conflict, restore it from Git before starting the API.
