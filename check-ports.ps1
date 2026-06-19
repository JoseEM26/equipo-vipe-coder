<#
.SYNOPSIS
    Verifica que los puertos requeridos por docker-compose estén libres.
.DESCRIPTION
    Comprueba los puertos 3306 (MySQL), 8080 (Backend) y 4200 (Frontend).
    Si alguno está ocupado, muestra el proceso que lo usa y sale con código 1.
.EXAMPLE
    .\check-ports.ps1
    .\check-ports.ps1 -Ports 3306,8080          # solo esos puertos
    .\check-ports.ps1 -Kill                      # intenta matar procesos que bloquean
#>
param(
    [int[]]  $Ports = @(3306, 8080, 4200),
    [switch] $Kill
)

$services = @{
    3306 = "MySQL (BD)"
    8080 = "Backend Spring Boot"
    4200 = "Frontend Angular"
}

$conflicts = @()

Write-Host ""
Write-Host "=== Verificacion de puertos para docker-compose ===" -ForegroundColor Cyan
Write-Host ""

foreach ($port in $Ports) {
    $label = if ($services.ContainsKey($port)) { $services[$port] } else { "servicio desconocido" }

    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue

    if ($conn) {
        $procId = $conn[0].OwningProcess
        $proc   = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $name   = if ($proc) { $proc.ProcessName } else { "PID $procId" }

        Write-Host "  [OCUPADO]  :$port  $label  <-- $name (PID $procId)" -ForegroundColor Red

        if ($Kill) {
            Write-Host "             Terminando $name (PID $procId)..." -ForegroundColor Yellow
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            Start-Sleep -Milliseconds 500
            $still = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
            if ($still) {
                Write-Host "             No se pudo terminar el proceso." -ForegroundColor Red
                $conflicts += $port
            } else {
                Write-Host "             Puerto $port liberado." -ForegroundColor Green
            }
        } else {
            $conflicts += $port
        }
    } else {
        Write-Host "  [LIBRE]    :$port  $label" -ForegroundColor Green
    }
}

Write-Host ""

if ($conflicts.Count -gt 0) {
    Write-Host "RESULTADO: hay $($conflicts.Count) puerto(s) ocupado(s): $($conflicts -join ', ')" -ForegroundColor Red
    Write-Host ""
    Write-Host "Opciones:" -ForegroundColor Yellow
    Write-Host "  1. Detener el proceso manualmente y volver a ejecutar este script."
    Write-Host "  2. Ejecutar con -Kill para terminarlo automaticamente:"
    Write-Host "       .\check-ports.ps1 -Kill"
    Write-Host "  3. Cambiar el puerto en docker-compose.yml usando variables de entorno:"
    Write-Host "       DB_PORT=3307 APP_PORT=8081 docker compose up -d"
    Write-Host ""
    exit 1
} else {
    Write-Host "RESULTADO: todos los puertos estan libres." -ForegroundColor Green
    Write-Host ""
    Write-Host "Puedes levantar el proyecto con:" -ForegroundColor Cyan
    Write-Host "  docker compose up --build -d"
    Write-Host ""
    exit 0
}
