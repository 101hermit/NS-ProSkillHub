@echo off
rem ===========================================================================
rem  ProSkillHub 1.0 (PSKH) - stop the instance listening on the port
rem  Usage:  stop.cmd           (default port 30802)
rem          stop.cmd 30810
rem ===========================================================================
setlocal
chcp 65001 >nul 2>&1
set "SCRIPT=%~dp0scripts\stop.ps1"
if not exist "%SCRIPT%" (
  echo [ERROR] missing %SCRIPT%
  exit /b 3
)
if "%~1"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" -Port %~1
)
endlocal & exit /b %ERRORLEVEL%
