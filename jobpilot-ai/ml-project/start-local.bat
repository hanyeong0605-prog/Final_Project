@echo off
setlocal
cd /d "%~dp0"

set "VENV_PYTHON=.venv\Scripts\python.exe"
if not exist "%VENV_PYTHON%" (
    echo [ERROR] Python virtual environment was not found.
    echo Run the setup command in README.md first.
    exit /b 1
)

if not exist "cache\wordcloud_cache.json" (
    echo [ERROR] cache\wordcloud_cache.json is missing.
    echo Restore the repository-managed cache file before starting the service.
    exit /b 1
)

if not defined WORDCLOUD_FONT_PATH set "WORDCLOUD_FONT_PATH=C:\Windows\Fonts\malgun.ttf"
if not exist "%WORDCLOUD_FONT_PATH%" (
    echo [ERROR] Korean font not found: %WORDCLOUD_FONT_PATH%
    echo Set WORDCLOUD_FONT_PATH to a valid .ttf file and run this script again.
    exit /b 1
)

echo Starting Word Cloud FastAPI on http://127.0.0.1:8000
"%VENV_PYTHON%" -m uvicorn app:app --host 127.0.0.1 --port 8000
