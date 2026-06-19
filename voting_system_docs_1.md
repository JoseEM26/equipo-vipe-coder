# Sistema de Votaciones en Tiempo Real

> Documentación de Base de Datos y Recomendaciones de Integración  
> Stack: MySQL · Java · Spring Boot · WebSockets

---

## Índice

1. [Arquitectura general del esquema](#1-arquitectura-general-del-esquema)
2. [Decisiones de diseño explicadas](#2-decisiones-de-diseño-explicadas)
3. [Reglas de negocio y cómo se aplican](#3-reglas-de-negocio-y-cómo-se-aplican)
4. [Triggers: detalle y orden de ejecución](#4-triggers-detalle-y-orden-de-ejecución)
5. [Integración con Java y WebSockets](#5-integración-con-java-y-websockets)
6. [Consultas útiles para el backend Java](#6-consultas-útiles-para-el-backend-java)
7. [Índices y rendimiento](#7-índices-y-rendimiento)
8. [Seguridad y buenas prácticas](#8-seguridad-y-buenas-prácticas)
9. [Resumen de SQLStates importantes para Java](#9-resumen-de-sqlstates-importantes-para-java)

---

## 1. Arquitectura general del esquema

El esquema está diseñado con un principio clave: **la base de datos es la última línea de defensa**. No se confía ciegamente en que la capa de aplicación Java valide todo correctamente. Cada regla de negocio crítica tiene su propio respaldo en la base de datos mediante constraints, triggers o tipos ENUM.

Esto es especialmente importante en sistemas con WebSockets, donde múltiples clientes pueden enviar peticiones simultáneas y las **condiciones de carrera (race conditions)** son un riesgo real.

### Tablas del sistema

| Tabla      | Descripción                                                  |
|------------|--------------------------------------------------------------|
| `usuarios` | Personas que interactúan con el sistema (`admin` o `usuario`) |
| `encuestas`| Encuestas creadas y gestionadas por administradores          |
| `opciones` | Opciones de respuesta asociadas a cada encuesta              |
| `votos`    | Registro inmutable de cada voto emitido                      |

### Relaciones principales

```
encuestas  ──(creado_por)──▶  usuarios
opciones   ──(encuesta_id)──▶ encuestas
votos      ──(encuesta_id)──▶ encuestas
votos      ──(usuario_id)───▶ usuarios
votos      ──(opcion_id)────▶ opciones
```

---

## 2. Decisiones de diseño explicadas

### 2.1 UUIDs como claves primarias

Se eligió UUID en lugar de IDs secuenciales (`SERIAL`/`BIGSERIAL`) por tres razones concretas:

- **Optimistic UI con WebSockets:** el cliente puede generar el ID del recurso antes de que el servidor lo confirme.
- **Seguridad:** los IDs secuenciales (1, 2, 3…) permiten enumerar registros fácilmente. Los UUIDs son impredecibles.
- **Sistemas distribuidos:** en arquitecturas con réplicas, los UUIDs no colisionan entre nodos.

### 2.2 ENUMs nativos

```
user_role     → 'admin', 'usuario'
survey_status → 'borrador', 'activa', 'finalizada', 'cancelada'
```

Usar ENUMs en lugar de `VARCHAR` libre tiene ventajas concretas:

- La base de datos **rechaza cualquier valor fuera del ENUM** en el momento del `INSERT`/`UPDATE`, sin importar lo que haga Java.
- Ocupa menos espacio en disco que `VARCHAR`.
- Las consultas con filtros sobre ENUMs se optimizan mejor.

> **Atención:** agregar valores a un ENUM requiere `ALTER TYPE ... ADD VALUE`. Planifica los valores desde el inicio.

### 2.3 Soft delete en usuarios

La columna `activo BOOLEAN DEFAULT TRUE` permite deshabilitar usuarios sin borrar su historial de votos.

```sql
-- Nunca usar DELETE sobre usuarios
UPDATE usuarios SET activo = FALSE WHERE id = '...';
```

Si se usara `DELETE`, la clave foránea en `votos` lo impediría (`ON DELETE RESTRICT`), o peor, se perderían votos con `ON DELETE CASCADE`.

### 2.4 Columnas de ciclo de vida en encuestas

| Columna         | Cuándo se llena                          | Quién la llena |
|-----------------|------------------------------------------|----------------|
| `creado_en`     | Al crear la encuesta                     | Base de datos  |
| `activada_en`   | Al pasar a estado `activa`               | Trigger        |
| `finalizada_en` | Al pasar a estado `finalizada`           | Trigger        |
| `cancelada_en`  | Al pasar a estado `cancelada`            | Trigger        |
| `actualizado_en`| En cada modificación                     | Trigger        |

> Tu código Java **no necesita enviarlas**. Si las enviás igualmente, el trigger las sobreescribirá.

---

## 3. Reglas de negocio y cómo se aplican

### 3.1 Flujo de estados

El flujo es **único y unidireccional**:

```
borrador ──▶ activa ──▶ finalizada
                  └──▶ cancelada
```

| Transición                    | Estado       |
|-------------------------------|--------------|
| `borrador` → `activa`         | ✅ Permitido |
| `activa` → `finalizada`       | ✅ Permitido |
| `activa` → `cancelada`        | ✅ Permitido |
| `activa` → `borrador`         | ❌ Bloqueado |
| `finalizada` → cualquier otro | ❌ Bloqueado |
| `borrador` → `finalizada`     | ❌ Bloqueado (no se puede saltar estado) |

El trigger `trg_encuestas_estado` intercepta cualquier `UPDATE` sobre la columna `estado` y lanza una excepción si la transición no es válida. Desde Java, ese error llega como:

```java
PSQLException e;
e.getServerErrorMessage().getMessage(); // mensaje descriptivo del trigger
```

### 3.2 Edición bloqueada fuera del estado borrador

El trigger `trg_encuestas_bloquear_edicion` asegura que solo se puede modificar (título, descripción) cuando la encuesta está en `borrador`.

Esto también aplica a las **opciones**: el trigger `trg_opciones_bloquear_edicion` bloquea `INSERT`, `UPDATE` y `DELETE` sobre `opciones` si la encuesta no está en `borrador`.

**Ejemplo de escenario bloqueado:**

1. Encuesta A está en estado `activa`.
2. Un admin intenta agregar una nueva opción vía API REST.
3. MySQL lanza excepción desde el trigger.
4. Java recibe el error con `SQLState P0001`.
5. El servidor WebSocket notifica al admin: *"No se pueden modificar opciones de una encuesta activa."*

### 3.3 Un usuario, un voto por encuesta

Esta es la restricción más crítica. Se aplica en **dos niveles**:

| Nivel | Mecanismo | Qué verifica |
|-------|-----------|--------------|
| 1 | Trigger `fn_validar_voto` | Que la encuesta esté activa y que la opción pertenezca a esa encuesta |
| 2 | `UNIQUE (usuario_id, encuesta_id)` | Que el usuario no haya votado ya, incluso con peticiones simultáneas |

> El trigger valida la lógica; el `UNIQUE` constraint es el **candado a nivel de base de datos** que no puede ser evadido.

---

## 4. Triggers: detalle y orden de ejecución

### Al hacer `UPDATE` sobre `encuestas`

Los triggers se ejecutan en orden alfabético (`BEFORE UPDATE`):

```
1. trg_encuestas_bloquear_edicion  → ¿Se puede editar?
2. trg_encuestas_estado            → ¿La transición de estado es válida?
3. trg_encuestas_updated_at        → Actualiza actualizado_en
```

### Al hacer `INSERT` sobre `votos`

```
1. trg_votos_validar   → Verifica encuesta activa + opción válida
2. UNIQUE constraint   → Verifica que no exista ya un voto del mismo usuario en la misma encuesta
```

> Si el trigger pasa pero el `UNIQUE` falla, se lanza `PSQLException` con `SQLState 23505`.

---

## 5. Integración con Java y WebSockets

### 5.1 Manejo de conexiones con WebSockets

En un sistema de WebSockets, **cada mensaje del cliente NO debe abrir una conexión nueva** a la base de datos. Usá un pool de conexiones con HikariCP (ya configurado en `application.yml`):

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:mysql://localhost:3306/votaciones");
config.setMaximumPoolSize(20);
config.setMinimumIdle(5);
config.setConnectionTimeout(30000);  // 30 segundos
config.setIdleTimeout(600000);       // 10 minutos
HikariDataSource dataSource = new HikariDataSource(config);
```

Cada handler de mensaje WebSocket obtiene una conexión del pool, ejecuta la operación y la devuelve. **Nunca mantener una conexión abierta mientras se espera input del cliente.**

### 5.2 Captura de errores de base de datos en Java

```java
public void registrarVoto(UUID encuestaId, UUID usuarioId, UUID opcionId)
    throws VotoException {

    String sql = "INSERT INTO votos (encuesta_id, usuario_id, opcion_id) "
               + "VALUES (?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setObject(1, encuestaId);
        ps.setObject(2, usuarioId);
        ps.setObject(3, opcionId);
        ps.executeUpdate();

    } catch (PSQLException e) {
        switch (e.getSQLState()) {
            case "23505":
                // El usuario ya votó en esta encuesta
                throw new VotoException("Ya has votado en esta encuesta.", e);
            case "23503":
                // Referencia inválida (encuesta, usuario u opción no existe)
                throw new VotoException("Datos de voto inválidos.", e);
            case "P0001":
                // Error de trigger: encuesta no activa, opción inválida, etc.
                throw new VotoException(e.getServerErrorMessage().getMessage(), e);
            default:
                throw new VotoException("Error al registrar el voto.", e);
        }
    }
}
```

### 5.3 Notificación en tiempo real con LISTEN/NOTIFY

En lugar de hacer polling para detectar nuevos votos, MySQL puede notificar a la aplicación automáticamente.

**Paso 1 — Trigger de notificación:**

```sql
CREATE OR REPLACE FUNCTION fn_notificar_nuevo_voto()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_notify(
        'nuevo_voto',
        json_build_object(
            'encuesta_id', NEW.encuesta_id,
            'opcion_id',   NEW.opcion_id
        )::text
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_votos_notify
    AFTER INSERT ON votos
    FOR EACH ROW EXECUTE FUNCTION fn_notificar_nuevo_voto();
```

**Paso 2 — Escuchar desde Java:**

```java
// Hilo dedicado para escuchar notificaciones
PGConnection pgConn = conn.unwrap(PGConnection.class);
Statement stmt = conn.createStatement();
stmt.execute("LISTEN nuevo_voto");
stmt.close();

while (true) {
    PGNotification[] notifications = pgConn.getNotifications(5000);
    if (notifications != null) {
        for (PGNotification notification : notifications) {
            String payload = notification.getParameter();
            broadcastToClients(payload);
        }
    }
}
```

**Paso 3 — Broadcast a clientes WebSocket:**

```java
public void broadcastToClients(String payload) {
    // payload: {"encuesta_id": "...", "opcion_id": "..."}
    ResultadosDTO resultados = obtenerResultados(encuestaId);
    for (Session session : activeSessions.get(encuestaId)) {
        session.getAsyncRemote().sendText(toJson(resultados));
    }
}
```

Con este patrón, cada voto dispara una notificación de la BD → Java → todos los clientes WebSocket. **Sin polling.**

### 5.4 Consulta eficiente de resultados en tiempo real

```sql
SELECT opcion_id, opcion_texto, total_votos, porcentaje
FROM   v_resultados_encuesta
WHERE  encuesta_id = ?
ORDER BY orden;
```

La vista `v_resultados_encuesta` usa una window function (`SUM OVER PARTITION`) para calcular el porcentaje sin subconsultas adicionales. El resultado puede serializarse directamente a JSON para enviarlo por WebSocket.

### 5.5 Verificar si un usuario ya votó

```sql
SELECT EXISTS (
    SELECT 1 FROM votos
    WHERE  usuario_id  = ?
    AND    encuesta_id = ?
) AS ya_voto;
```

> **Importante:** este check y el `INSERT` posterior **no son atómicos** si no están en la misma transacción. Siempre confiá en el error `23505` como fuente de verdad. Este `SELECT` solo sirve para UX preventiva (deshabilitar el botón de votar).

---

## 6. Consultas útiles para el backend Java

**Encuestas activas disponibles para votar:**

```sql
SELECT id, titulo, descripcion, activada_en
FROM   encuestas
WHERE  estado = 'activa'
ORDER BY activada_en DESC;
```

**Encuestas finalizadas donde participó un usuario:**

```sql
SELECT encuesta_id, encuesta_titulo, opcion_votada, votado_en, finalizada_en
FROM   v_participacion_usuario
WHERE  usuario_id = ?
ORDER BY finalizada_en DESC;
```

**Todas las opciones de una encuesta:**

```sql
SELECT id, texto, orden
FROM   opciones
WHERE  encuesta_id = ?
ORDER BY orden;
```

**Cambiar estado de una encuesta (admin):**

```sql
-- Activar
UPDATE encuestas SET estado = 'activa'     WHERE id = ? AND creado_por = ?;

-- Finalizar
UPDATE encuestas SET estado = 'finalizada' WHERE id = ? AND creado_por = ?;

-- Cancelar
UPDATE encuestas SET estado = 'cancelada'  WHERE id = ? AND creado_por = ?;
```

> Siempre incluir `creado_por` en el `WHERE` para que un admin no pueda modificar encuestas de otro admin.

**Eliminar encuesta (solo en borrador):**

```sql
-- Las opciones se borran en cascada (ON DELETE CASCADE).
-- Una encuesta en borrador no tiene votos, así que es seguro.
DELETE FROM encuestas
WHERE  id = ?
AND    estado = 'borrador'
AND    creado_por = ?;
```

---

## 7. Índices y rendimiento

### Índices existentes

| Índice                    | Columna(s)            | Para qué sirve                          |
|---------------------------|-----------------------|-----------------------------------------|
| `idx_encuestas_estado`    | `estado`              | Filtrar encuestas por estado            |
| `idx_encuestas_creado_por`| `creado_por`          | Ver encuestas de un admin específico    |
| `idx_opciones_encuesta_id`| `encuesta_id`         | Obtener opciones de una encuesta        |
| `idx_votos_encuesta_id`   | `encuesta_id`         | Contar votos por encuesta               |
| `idx_votos_usuario_id`    | `usuario_id`          | Ver historial de votos de un usuario    |
| `idx_votos_opcion_id`     | `opcion_id`           | Contar votos por opción                 |

### Optimizaciones para alta concurrencia

Para sistemas con miles de usuarios votando simultáneamente, considerar:

**a) Contadores materializados**

En lugar de `COUNT(*)` en tiempo real, mantener una tabla de contadores actualizada por trigger:

```sql
-- Tabla auxiliar
contadores_opciones (opcion_id, total)
-- Actualizada por trigger en cada INSERT sobre votos
-- Hace que la consulta de resultados sea O(1) en lugar de O(n)
```

**b) Caché de resultados en Redis**

Los resultados cambian con cada voto. Redis con TTL corto (1–2 segundos) reduce la carga sobre MySQL en picos de votación.

**c) Particionado de la tabla votos**

Si el sistema maneja millones de votos, particionar `votos` por `encuesta_id` o por rango de fechas mantiene el rendimiento de consultas históricas.

---

## 8. Seguridad y buenas prácticas

### 8.1 Contraseñas

Nunca almacenar contraseñas en texto plano. Usar **bcrypt** o **Argon2** desde Java antes de guardar en `password_hash`. Spring Security provee `BCryptPasswordEncoder`.

### 8.2 Validación de roles en doble capa

El campo `rol` en `usuarios` controla los permisos en la BD, pero la validación final debe estar en Java: verificar que el JWT tenga rol `admin` antes de llamar a endpoints de gestión de encuestas.

### 8.3 Siempre usar PreparedStatements

```java
// MAL — vulnerable a SQL injection
String sql = "SELECT * FROM usuarios WHERE email = '" + email + "'";

// BIEN — seguro y con query plan caching
String sql = "SELECT * FROM usuarios WHERE email = ?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setString(1, email);
```

### 8.4 Limitar privilegios del usuario de base de datos

```sql
CREATE USER app_votaciones WITH PASSWORD 'password_seguro';
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES
  IN SCHEMA public TO app_votaciones;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO app_votaciones;
-- No otorgar DROP, CREATE ni superuser.
```

### 8.5 Auditoría de cambios de estado

Las columnas `activada_en`, `finalizada_en` y `cancelada_en` proveen un registro básico. Para auditoría completa, se puede agregar una tabla separada:

```sql
CREATE TABLE auditoria_encuestas (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    encuesta_id   UUID         NOT NULL,
    usuario_id    UUID         NOT NULL,
    accion        VARCHAR(50)  NOT NULL,  -- 'activada', 'finalizada', 'cancelada', 'editada'
    detalle       JSON,
    registrado_en TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

Populada desde un trigger `AFTER UPDATE` en `encuestas`, o desde la capa de aplicación Java.

---

## 9. Resumen de SQLStates importantes para Java

| SQLState | Nombre                  | Cuándo ocurre                                        | Acción recomendada              |
|----------|-------------------------|------------------------------------------------------|---------------------------------|
| `23505`  | `unique_violation`      | Usuario intentó votar dos veces                      | Devolver error 409 al cliente   |
| `23503`  | `foreign_key_violation` | Referencia a registro inexistente                    | Devolver error 400 al cliente   |
| `23514`  | `check_violation`       | Violación de `CHECK` constraint                      | Devolver error 422 al cliente   |
| `23502`  | `not_null_violation`    | Campo obligatorio nulo                               | Devolver error 400 al cliente   |
| `P0001`  | `raise_exception`       | Error lanzado por trigger (estado inválido, etc.)    | Reenviar mensaje del trigger    |
| `40001`  | `serialization_failure` | Conflicto de transacción concurrente                 | Reintentar con backoff          |
| `40P01`  | `deadlock_detected`     | Deadlock detectado                                   | Reintentar con backoff exponencial |

> Para `40001` y `40P01` implementar lógica de **reintento con backoff exponencial** en Java, ya que son errores transitorios esperados en sistemas de alta concurrencia.

---

*Documentación generada para el proyecto **equipo-vipe-coder** · Sistema de Votaciones en Tiempo Real*
