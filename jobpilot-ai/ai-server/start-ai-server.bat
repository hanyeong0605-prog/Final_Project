@echo off
cd /d "%~dp0"

if not exist "%~dp0.venv\Scripts\activate.bat" (
    echo [ERROR] venv not found: %~dp0.venv
    echo Run these first inside ai-server folder:
    echo     python -m venv .venv
    echo     .venv\Scripts\activate
    echo     pip install -r requirements.txt
    pause
    exit /b 1
)

call "%~dp0.venv\Scripts\activate.bat"

where uvicorn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] venv found but uvicorn is not installed.
    echo Run inside ai-server folder: pip install -r requirements.txt
    pause
    exit /b 1
)

chcp 65001 >nul
uvicorn app.main:app --port 8001 --reload
pause
