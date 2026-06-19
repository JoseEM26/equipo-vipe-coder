# Guía de integración Frontend — Votaciones API

Contexto y referencia para construir el frontend de la API de Votaciones en Tiempo Real.

> **Stack del frontend:** Angular 17+ (standalone). Hay un cliente base ya listo
> en [`frontend-angular/`](./frontend-angular/README.md) que implementa todo lo
> descrito aquí (auth, interceptor, servicios y tiempo real). Ver §12.

---

## 1. Información base

- **Backend:** Spring Boot 3 (REST + SOAP + WebSocket). Para el front solo se usan **REST** y **WebSocket** (SOAP es un canal aparte).
- **Base URL (dev):** `http://localhost:8080`
- **Formato:** JSON en request y response. `Content-Type: application/json`.
- **CORS:** abierto (`*`) en dev; se puede llamar desde cualquier origen local.

---

## 2. Autenticación (JWT)

Flujo stateless con JWT:

1. Login o registro → se recibe un `token`.
2. Guardar el token (p. ej. `localStorage`).
3. En cada petición protegida enviar el header:
   ```
   Authorization: Bearer <token>
   ```
4. Token ausente/ inválido en endpoint protegido → **401**. Token válido sin permisos → **403**.

**Respuesta de login/registro (`AuthResponse`):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "usuarioId": "uuid",
  "nombre": "Admin",
  "email": "admin@admin.com",
  "rol": "admin",
  "expiraEn": 1750000000000
}
```

- **Roles:** `"admin"` y `"usuario"`. El registro siempre crea `usuario`; los admin se asignan en BD.
- `expiraEn` es epoch en ms (token dura 24 h) → útil para auto-logout.

---

## 3. Endpoints REST

### Auth (públicos)
| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| POST | `/api/auth/login` | `{ email, password }` | 200 `AuthResponse` · 401 · 400 |
| POST | `/api/auth/registro` | `{ nombre, email, password }` | 201 `AuthResponse` · 409 · 400 |

### Encuestas — Admin (rol `admin`)
| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| POST | `/api/encuestas` | `EncuestaRequest` | 201 `EncuestaDto` |
| PUT | `/api/encuestas/{id}` | `EncuestaRequest` | 200 `EncuestaDto` (solo borrador) |
| PATCH | `/api/encuestas/{id}/estado` | `{ estado }` | 200 `EncuestaDto` |
| DELETE | `/api/encuestas/{id}` | — | 204 (solo borrador) |
| GET | `/api/encuestas` | — | 200 `EncuestaResumenDto[]` |

### Encuestas — Cualquier autenticado
| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/api/encuestas/activas` | — | 200 `EncuestaResumenDto[]` |
| GET | `/api/encuestas/finalizadas` | — | 200 `EncuestaResumenDto[]` |
| GET | `/api/encuestas/{id}` | — | 200 `EncuestaDto` |
| POST | `/api/encuestas/{id}/votar` | `{ opcionId }` | 201 `ResultadoEncuestaDto` |
| GET | `/api/encuestas/{id}/resultados` | — | 200 `ResultadoEncuestaDto` |

---

## 4. Formas de los datos (DTOs)

**`EncuestaRequest`** (crear/editar):
```json
{ "titulo": "...", "descripcion": "...", "opciones": ["A", "B", "C"] }
```

**`EncuestaResumenDto`** (listados):
```json
{
  "id": "uuid", "titulo": "...", "descripcion": "...",
  "estado": "activa", "creadoEn": "2026-06-19T07:47:04",
  "totalOpciones": 3, "totalVotos": 12,
  "yaVoto": true
}
```

**`EncuestaDto`** (detalle):
```json
{
  "id": "uuid", "titulo": "...", "descripcion": "...",
  "estado": "borrador",
  "creadoEn": "...", "activadaEn": null,
  "finalizadaEn": null, "canceladaEn": null,
  "opciones": [ { "id": "uuid", "texto": "Java", "orden": 0 } ],
  "yaVoto": false
}
```

**`ResultadoEncuestaDto`** (votar / resultados / WebSocket):
```json
{
  "encuestaId": "uuid", "titulo": "...", "estado": "activa",
  "totalVotos": 12,
  "opciones": [
    { "opcionId": "uuid", "texto": "Java", "orden": 0,
      "totalVotos": 8, "porcentaje": 66.67 }
  ]
}
```

> Los campos `null` **no aparecen** en el JSON. `yaVoto` ausente = no aplica (admin); `activadaEn` ausente = aún no activada.

---

## 5. Estados de encuesta

```
borrador ──► activa ──► finalizada     (solo avanza)
   │           │
   └──► cancelada ◄──┘                 (cancelable desde borrador o activa)
```

- `finalizada` y `cancelada` son terminales.
- Editar/eliminar solo en `borrador` (si no → **422**).
- Votar solo en `activa`.
- `PATCH .../estado` body: `{ "estado": "activa" | "finalizada" | "cancelada" }`.

---

## 6. Visibilidad de resultados (regla clave)

`GET /api/encuestas/{id}/resultados`:
- **Admin:** siempre.
- **Usuario:** solo si **ya votó** o si está **finalizada**; si no → **403**.

En la UI del usuario: muestra resultados tras votar (la respuesta de `POST /votar` ya trae el `ResultadoEncuestaDto`) o cuando la encuesta finaliza. Antes, oculta los porcentajes para no sesgar el voto.

---

## 7. Formato de errores

Todos los errores siguen `ErrorResponse`:
```json
{
  "status": 422,
  "error": "Estado inválido",
  "mensaje": "Solo se pueden editar encuestas en borrador",
  "timestamp": "2026-06-19T10:30:00Z"
}
```

Validación (400) añade `campos`:
```json
{
  "status": 400, "error": "Validación fallida",
  "mensaje": "Hay errores en los campos enviados",
  "campos": { "email": "Formato de email inválido",
              "password": "La contraseña debe tener al menos 8 caracteres" }
}
```

**Códigos:** `400` validación · `401` no autenticado · `403` sin permiso/no puede ver · `404` no existe · `409` conflicto (email/voto duplicado) · `422` regla de negocio.

> Para mensajes al usuario usa `mensaje`; para resaltar inputs usa `campos`.

---

## 8. Reglas de validación (formularios)

| Campo | Regla |
|---|---|
| `email` | formato email válido |
| `password` | mínimo 8 caracteres |
| `nombre` (registro) | obligatorio, máx 100 |
| `titulo` | obligatorio, máx 255 |
| `descripcion` | opcional, máx 2000 |
| `opciones` | mínimo 2, cada una no vacía, máx 500 |
| `opcionId` (votar) | UUID obligatorio |

---

## 9. Tiempo real (WebSocket / STOMP) — solo admin

- **Handshake:** `/ws` (SockJS + STOMP).
- **Auth:** header `Authorization: Bearer <token>` en el frame CONNECT.
- **Suscripción:** `/topic/admin/encuesta/{encuestaId}` → llega un `ResultadoEncuestaDto` por cada voto. Solo admins pueden suscribirse.
- El usuario normal no usa WebSocket; usa REST.

```js
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  connectHeaders: { Authorization: `Bearer ${token}` },
  onConnect: () => {
    client.subscribe(`/topic/admin/encuesta/${encuestaId}`, (msg) => {
      const resultado = JSON.parse(msg.body); // ResultadoEncuestaDto
      // actualizar gráfico en vivo
    });
  },
});
client.activate();
```

---

## 10. Pantallas sugeridas

- **Login / Registro** → `/api/auth/*`
- **Usuario – Encuestas activas** → `GET /activas` → `POST /{id}/votar` → resultados de la respuesta.
- **Usuario – Mis finalizadas** → `GET /finalizadas`.
- **Admin – Panel** → `GET /api/encuestas` + crear/editar/cambiar estado/eliminar.
- **Admin – Detalle en vivo** → `GET /{id}` + WebSocket `/topic/admin/encuesta/{id}`.

---

## 11. Datos de prueba

- **Admin:** `admin@admin.com` / `12345678`.
- Colección Postman: `EncuestasApp.postman_collection.json` (referencia ejecutable de todos los endpoints).

---

## 12. Cliente Angular base (`frontend-angular/`)

Se incluye una capa de conexión lista para usar (no UI), en Angular 17+ standalone
con signals. Mapea 1:1 con esta guía:

```
frontend-angular/src/
├─ environments/environment.ts        # apiBaseUrl, wsUrl
└─ app/
   ├─ app.config.ts                   # provideHttpClient + authInterceptor
   └─ core/
      ├─ models/api.models.ts         # tipos TS de los DTOs (§4)
      ├─ auth/auth.service.ts         # login/registro/logout + isAuthenticated/isAdmin
      ├─ auth/auth.interceptor.ts     # Bearer token + logout en 401 (§2, §7)
      ├─ auth/auth.guards.ts          # authGuard / adminGuard (§2)
      ├─ api/encuesta.service.ts      # endpoints de encuestas (§3)
      ├─ api/voto.service.ts          # votar + resultados (§3, §6)
      └─ realtime/realtime.service.ts # resultados en vivo WebSocket (§9)
```

**Cómo empezar:**
1. Copia `src/app/core` y `src/environments/environment.ts` a tu proyecto Angular.
2. Registra el interceptor: `provideHttpClient(withInterceptors([authInterceptor]))`.
3. Para el tiempo real instala: `npm i @stomp/stompjs sockjs-client` y `npm i -D @types/sockjs-client`.

Ver [`frontend-angular/README.md`](./frontend-angular/README.md) para ejemplos de uso
(login, listar/votar, resultados en vivo) y el polyfill de SockJS.
