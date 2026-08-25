@echo off
cd /d "%~dp0"

if not exist "%~dp0.venv\Scripts\python.exe" (
    echo [ERROR] venv not found: %~dp0.venv
    echo Run the setup command in README.md first.
    pause
    exit /b 1
)

if not exist "%~dp0cache\wordcloud_cache.json" (
    echo [ERROR] cache\wordcloud_cache.json is missing.
    echo Restore the repository-managed cache file before starting the service.
    pause
    exit /b 1
)

if not defined WORDCLOUD_FONT_PATH set "WORDCLOUD_FONT_PATH=C:\Windows\Fonts\malgun.ttf"
if not exist "%WORDCLOUD_FONT_PATH%" (
    echo [ERROR] Korean font not found: %WORDCLOUD_FONT_PATH%
    echo Set WORDCLOUD_FONT_PATH to a valid .ttf file and run this script again.
    pause
    exit /b 1
)

chcp 65001 >nul
echo Starting Word Cloud FastAPI (dev, auto-reload) on http://127.0.0.1:8000
"%~dp0.venv\Scripts\python.exe" -m uvicorn app:app --host 127.0.0.1 --port 8000 --reload
pause
