@echo off
chcp 65001 >nul
cd /d "%~dp0"
mvn spring-boot:run
pause
