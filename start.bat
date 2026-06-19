@echo off
setlocal EnableDelayedExpansion
title Arranque - Sistema Votaciones

:: ── Configuracion (edita aqui o crea un archivo .env.bat con tus valores) ──
if exist "%~dp0.env.bat" call "%~dp0.env.bat"

if not defined DB_HOST     set DB_HOST=localhost
if not defined DB_PORT     set DB_PORT=3306
if not defined DB_NAME     set DB_NAME=votaciones
if not defined DB_USER     set DB_USER=root
if not defined DB_PASSWORD set DB_PASSWORD=12345
if not defined APP_PORT    set APP_PORT=8080
if not defined JWT_SECRET  set JWT_SECRET=c2VjcmV0b3NlZ3Vyb3BhcmF2b3RhY2lvbmVzZW50aWVtcG9yZWFsMTIzNDU2Nzg=

set BACKEND_DIR=%~dp0EncuestasApp

:: ══════════════════════════════════════════════════════════════════════════
echo.
echo =====================================================
echo   Sistema de Votaciones -- arranque sin Docker
echo =====================================================
echo.

:: ── 1. Java ───────────────────────────────────────────────────────────────
echo [1/4] Verificando Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo   ERROR: Java no encontrado. Instala JDK 25 (o 21^) y agrega al PATH.
    echo   https://adoptium.net/
    goto :fin_error
)
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    echo   OK - Java %%~V
    goto :java_ok
)
:java_ok

:: ── 2. Maven ──────────────────────────────────────────────────────────────
echo [2/4] Verificando Maven...
mvn -version >nul 2>&1
if errorlevel 1 (
    echo   ERROR: Maven no encontrado. Instala Maven 3.9+ y agrega al PATH.
    echo   https://maven.apache.org/download.cgi
    goto :fin_error
)
for /f "tokens=1,3" %%A in ('mvn -version 2^>^&1 ^| findstr /i "Apache Maven"') do (
    echo   OK - %%A %%B
    goto :mvn_ok
)
:mvn_ok

:: ── 3. MySQL accesible ────────────────────────────────────────────────────
echo [3/4] Verificando conexion a MySQL en %DB_HOST%:%DB_PORT%...
set MYSQL_CON_OK=0
for /f %%L in ('netstat -ano ^| findstr ":%DB_PORT% " ^| findstr "LISTENING"') do (
    set MYSQL_CON_OK=1
)
if "!MYSQL_CON_OK!"=="0" (
    echo   ADVERTENCIA: No se detecta nada escuchando en el puerto %DB_PORT%.
    echo   Asegurate de que MySQL este corriendo antes de que el backend arranque.
    echo   El backend reintentara la conexion al iniciar (Flyway esperara^).
    echo.
)
if "!MYSQL_CON_OK!"=="1" (
    echo   OK - Puerto %DB_PORT% activo.
)

:: ── 4. Compilar y ejecutar ────────────────────────────────────────────────
echo [4/4] Compilando el backend (esto puede tardar la primera vez^)...
echo.

cd /d "%BACKEND_DIR%"
if not exist "target\votaciones-api-0.0.1-SNAPSHOT.jar" (
    echo   No se encontro el JAR. Compilando con Maven...
    call mvn clean package -DskipTests -q
    if errorlevel 1 (
        echo   ERROR: La compilacion fallo. Revisa la salida de Maven.
        goto :fin_error
    )
    echo   Compilacion exitosa.
) else (
    echo   JAR existente encontrado. Saltando compilacion.
    echo   (Para recompilar, borra target\ o usa: mvn clean package -DskipTests^)
)

echo.
echo =====================================================
echo   Iniciando backend en http://localhost:%APP_PORT%
echo   Flyway aplicara las migraciones/seed al conectarse
echo   Presiona Ctrl+C para detener
echo =====================================================
echo.

java -jar "target\votaciones-api-0.0.1-SNAPSHOT.jar" ^
     --server.port=%APP_PORT% ^
     --spring.datasource.url="jdbc:mysql://%DB_HOST%:%DB_PORT%/%DB_NAME%?createDatabaseIfNotExist=true&serverTimezone=UTC" ^
     --spring.datasource.username=%DB_USER% ^
     --spring.datasource.password=%DB_PASSWORD% ^
     --jwt.secret=%JWT_SECRET%

goto :fin_ok

:fin_error
echo.
echo Arranque cancelado por errores.
pause
exit /b 1

:fin_ok
exit /b 0
