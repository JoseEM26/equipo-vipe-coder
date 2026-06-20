<div align="center">

# 🗳️ VotaYa

**Sistema de Votaciones en Tiempo Real**

Plataforma web fullstack para gestionar encuestas con resultados en vivo,
control de acceso por roles y arquitectura lista para producción.

[![Angular](https://img.shields.io/badge/Angular-17-DD0031?style=flat-square&logo=angular)](https://angular.dev)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://mysql.com)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)](https://docs.docker.com/compose)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org)

</div>

---

## ¿Qué es VotaYa?

VotaYa es una aplicación web de votaciones en tiempo real diseñada para equipos y organizaciones. Los administradores crean y gestionan encuestas con un panel completo, mientras que los usuarios votan y ven los resultados actualizarse al instante vía WebSocket.

### Características principales

| Característica | Descripción |
|---|---|
| **Votación en tiempo real** | Los resultados se actualizan en vivo con WebSocket/STOMP sin recargar la página |
| **Control de roles** | Dos roles: `admin` (gestión completa) y `usuario` (participación) |
| **Integridad garantizada** | Un voto por usuario por encuesta, validado a nivel de base de datos |
| **Máquina de estados** | Flujo unidireccional `borrador → activa → finalizada / cancelada` |
| **JWT stateless** | Autenticación sin sesión en servidor, tokens de 24 horas |
| **Flyway automático** | Migraciones y seed de datos al iniciar, sin configuración manual |

---

## Stack tecnológico

```
┌─────────────────────────────────────────────────────────┐
│                       FRONTEND                          │
│         Angular 17  ·  Standalone  ·  Signals           │
│         WebSocket (STOMP + SockJS)  ·  JWT              │
├─────────────────────────────────────────────────────────┤
│                        BACKEND                          │
│    Spring Boot 3.5  ·  Spring Security  ·  JPA          │
│    REST  ·  WebSocket  ·  SOAP  ·  Flyway               │
├─────────────────────────────────────────────────────────┤
│                      BASE DE DATOS                      │
│        MySQL 8.0  ·  Triggers  ·  Vistas  ·  UUID       │
└─────────────────────────────────────────────────────────┘
```

---

## Inicio rápido

### Requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop) instalado y corriendo

### 1 · Verificar que los puertos estén libres

```powershell
# PowerShell
.\check-ports.ps1

# CMD
check-ports.bat
```

Los puertos requeridos son `3307` (MySQL), `8080` (Backend) y `4200` (Frontend).

### 2 · Levantar todo con Docker

```powershell
docker compose up --build -d
```

La primera vez descarga las imágenes y compila el proyecto (~5 min). Las siguientes son rápidas gracias al caché de capas.

### 3 · Acceder a la aplicación

| Servicio | URL |
|---|---|
| **Frontend** | http://localhost:4200 |
| **Backend API** | http://localhost:8080 |
| **MySQL** (host) | `localhost:3307` |

> **Nota:** MySQL interno entre contenedores usa el puerto `3306`. El `3307` es solo para acceso externo desde tu máquina, para no chocar con una instancia local.

### 4 · Ver logs

```powershell
docker compose logs -f backend
docker compose logs -f frontend
```

### 5 · Bajar los servicios

```powershell
# Conserva los datos de la BD
docker compose down

# Borra todo incluyendo la BD
docker compose down -v
```

---

## Sin Docker (desarrollo local)

Necesitás **Java 25**, **Maven 3.9+** y **MySQL 8** corriendo localmente.

```bat
REM CMD — verifica puertos, compila y arranca el backend
start.bat
```

El frontend se desarrolla por separado:

```bash
cd EncuestasApp/frontend-angular
npm install
npm start          # http://localhost:4200
```

---

## Estructura del proyecto

```
equipo-vipe-coder/
│
├── EncuestasApp/                  # Proyecto principal
│   ├── src/main/java/             # Backend Spring Boot
│   │   └── cibertec.edu/
│   │       ├── controllers/       # REST endpoints
│   │       ├── services/          # Lógica de negocio
│   │       ├── entity/            # Entidades JPA
│   │       ├── auth/              # JWT + Spring Security
│   │       ├── config/            # WebSocket, SOAP, CORS
│   │       └── soap/              # Endpoint SOAP/WSDL
│   │
│   ├── src/main/resources/
│   │   ├── application.yml        # Configuración (DB, JWT, logging)
│   │   └── db/migration/          # Migraciones Flyway
│   │       ├── V1__schema_inicial.sql   # Schema + datos de prueba
│   │       └── V2__estado_cancelada.sql # Estado cancelada
│   │
│   ├── frontend-angular/          # Frontend Angular 17
│   │   └── src/app/
│   │       ├── core/              # Servicios, interceptor, guards
│   │       └── pages/             # Login, Home, Admin, Detalle
│   │
│   ├── Dockerfile                 # Backend: Maven build + JRE
│   └── EncuestasApp.postman_collection.json
│
├── docker-compose.yml             # Orquestación completa (3 servicios)
├── check-ports.ps1                # Verificar puertos (PowerShell)
├── check-ports.bat                # Verificar puertos (CMD)
├── start.bat                      # Arranque sin Docker
├── .env.example                   # Variables de entorno de referencia
└── voting_system_docs_1.md        # Documentación técnica de la BD
```

---

## API REST — resumen de endpoints

### Autenticación (públicos)

```
POST /api/auth/login      { email, password }       → token JWT
POST /api/auth/registro   { nombre, email, password } → token JWT
```

### Encuestas

```
GET    /api/encuestas              → todas (admin)
GET    /api/encuestas/activas      → disponibles para votar
GET    /api/encuestas/finalizadas  → historial
GET    /api/encuestas/{id}         → detalle
POST   /api/encuestas              → crear (admin)
PUT    /api/encuestas/{id}         → editar en borrador (admin)
PATCH  /api/encuestas/{id}/estado  → cambiar estado (admin)
DELETE /api/encuestas/{id}         → eliminar en borrador (admin)
```

### Votación

```
POST  /api/encuestas/{id}/votar      → emitir voto → devuelve resultados
GET   /api/encuestas/{id}/resultados → ver resultados
```

### WebSocket (admin)

```
Handshake: /ws
Suscripción: /topic/admin/encuesta/{id}   → ResultadoEncuesta en vivo
```

---

## Estados de una encuesta

```
              ┌─────────┐
              │ borrador │  ← estado inicial al crear
              └────┬────┘
                   │ activar
                   ▼
              ┌─────────┐
              │  activa  │  ← usuarios pueden votar
              └────┬────┘
           ┌───────┴────────┐
           │ finalizar       │ cancelar
           ▼                 ▼
     ┌──────────┐      ┌───────────┐
     │finalizada│      │ cancelada │  ← estados terminales
     └──────────┘      └───────────┘
```

> Los cambios de estado son **unidireccionales** y están validados por triggers en la base de datos. No hay vuelta atrás desde `finalizada` o `cancelada`.

---

## Variables de entorno

Copiá `.env.example` a `.env` y ajustá los valores:

```env
DB_NAME=votaciones
DB_USER=root
DB_PASSWORD=12345
DB_PORT=3307          # puerto host para MySQL

APP_PORT=8080         # puerto host para el backend
FRONTEND_PORT=4200    # puerto host para el frontend

JWT_SECRET=...        # cambiar en producción (openssl rand -base64 64)
JWT_EXPIRATION_MS=86400000
```

---

## Datos de prueba

El seed en `V1__schema_inicial.sql` crea usuarios y una encuesta de ejemplo al iniciar por primera vez.

> **Importante:** los hashes de contraseña en el seed son placeholders. Para usar los usuarios de prueba, registrá nuevos usuarios desde `/registro` o reemplazá los hashes con valores bcrypt válidos.

---

## Documentación adicional

| Archivo | Contenido |
|---|---|
| [`voting_system_docs_1.md`](./voting_system_docs_1.md) | Diseño de BD, triggers, integración Java/WebSocket |
| [`EncuestasApp/FRONTEND.md`](./EncuestasApp/FRONTEND.md) | Guía completa de integración del frontend |
| [`EncuestasApp/EncuestasApp.postman_collection.json`](./EncuestasApp/EncuestasApp.postman_collection.json) | Colección Postman con todos los endpoints |

---

## Equipo

Desarrollado por **Equipo VIPE Coder** · Cibertec

---

<div align="center">
<sub>Hecho con ☕ y Spring Boot</sub>
</div>
