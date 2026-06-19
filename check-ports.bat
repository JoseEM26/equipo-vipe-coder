@echo off
setlocal EnableDelayedExpansion
title Verificacion de puertos - Sistema Votaciones

echo.
echo === Verificacion de puertos para el Sistema de Votaciones ===
echo.

set CONFLICTOS=0

call :checkPort 3306 "MySQL (BD)"
call :checkPort 8080 "Backend Spring Boot"
call :checkPort 4200 "Frontend Angular"

echo.
if %CONFLICTOS% GTR 0 (
    echo [RESULTADO] %CONFLICTOS% puerto(s) ocupado(s).
    echo.
    echo Opciones:
    echo   1. Detener el proceso desde el Administrador de Tareas (PID mostrado arriba^).
    echo   2. O cambiar el puerto en application.yml antes de arrancar.
    echo.
    exit /b 1
) else (
    echo [RESULTADO] Todos los puertos estan libres.
    echo.
    echo Puedes arrancar con:
    echo   start.bat
    echo   -- o --
    echo   docker compose up --build -d
    echo.
    exit /b 0
)

:: ── Funcion interna ───────────────────────────────────────────────────────
:checkPort
set PORT=%~1
set LABEL=%~2

:: netstat devuelve lineas como: TCP  0.0.0.0:8080  ...  LISTENING  1234
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R ":%PORT% " ^| findstr "LISTENING"') do (
    set PID=%%P
    for /f "tokens=1" %%N in ('tasklist /FI "PID eq %%P" /NH /FO CSV 2^>nul') do (
        set PROCNAME=%%~N
    )
    echo   [OCUPADO]  :%PORT%  %LABEL%  ^<-- !PROCNAME! (PID !PID!^)
    set /a CONFLICTOS+=1
    goto :eof
)
echo   [LIBRE]    :%PORT%  %LABEL%
goto :eof
