@echo off
rem ===========================================================================
rem  ProSkillHub 1.0 (PSKH) - one click start
rem  Usage:  start.cmd           (default port 30802)
rem          start.cmd 30810     (custom port)
rem  This file is intentionally ASCII-only: cmd.exe with a GBK code page will
rem  corrupt UTF-8 text inside .cmd files. All Chinese messages live in
rem  scripts\start.ps1 (saved as UTF-8 with BOM).
rem ===========================================================================
setlocal
chcp 65001 >nul 2>&1
set "SCRIPT=%~dp0scripts\start.ps1"
if not exist "%SCRIPT%" (
  echo [ERROR] missing %SCRIPT%
  pause
  exit /b 3
)
if "%~1"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Port %~1
)
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo.
  echo [ERROR] start failed with exit code %RC%
  pause
)
endlocal & exit /b %RC%
