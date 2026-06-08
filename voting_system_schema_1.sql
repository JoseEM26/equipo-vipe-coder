-- ============================================================
--  SISTEMA DE VOTACIONES EN TIEMPO REAL
--  Base de datos PostgreSQL
-- ============================================================

-- Extensión para UUIDs
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
--  TIPOS ENUMERADOS
-- ============================================================

CREATE TYPE user_role AS ENUM ('admin', 'usuario');

CREATE TYPE survey_status AS ENUM ('borrador', 'activa', 'finalizada');

-- ============================================================
--  TABLA: usuarios
-- ============================================================

CREATE TABLE usuarios (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre        VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    rol           user_role     NOT NULL DEFAULT 'usuario',
    activo        BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_en     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT chk_usuarios_email CHECK (email ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$')
);

COMMENT ON TABLE  usuarios              IS 'Usuarios del sistema con soporte de roles admin y usuario.';
COMMENT ON COLUMN usuarios.id           IS 'Identificador único del usuario (UUID).';
COMMENT ON COLUMN usuarios.email        IS 'Email único usado para autenticación.';
COMMENT ON COLUMN usuarios.password_hash IS 'Hash bcrypt/argon2 de la contraseña. Nunca almacenar en texto plano.';
COMMENT ON COLUMN usuarios.rol          IS 'Rol del usuario: admin puede gestionar encuestas; usuario puede votar.';
COMMENT ON COLUMN usuarios.activo       IS 'Soft-delete: FALSE deshabilita el acceso sin borrar el registro.';

-- ============================================================
--  TABLA: encuestas
-- ============================================================

CREATE TABLE encuestas (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo         VARCHAR(255)  NOT NULL,
    descripcion    TEXT,
    estado         survey_status NOT NULL DEFAULT 'borrador',
    creado_por     UUID          NOT NULL,
    creado_en      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    activada_en    TIMESTAMPTZ,
    finalizada_en  TIMESTAMPTZ,

    CONSTRAINT fk_encuestas_creado_por
        FOREIGN KEY (creado_por) REFERENCES usuarios(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_encuestas_titulo CHECK (LENGTH(TRIM(titulo)) > 0),

    -- Coherencia de fechas de ciclo de vida
    CONSTRAINT chk_encuestas_activada_en
        CHECK (activada_en IS NULL OR estado IN ('activa', 'finalizada')),
    CONSTRAINT chk_encuestas_finalizada_en
        CHECK (finalizada_en IS NULL OR estado = 'finalizada'),
    CONSTRAINT chk_encuestas_fechas_orden
        CHECK (
            activada_en IS NULL OR finalizada_en IS NULL
            OR activada_en <= finalizada_en
        )
);

COMMENT ON TABLE  encuestas               IS 'Encuestas creadas por administradores.';
COMMENT ON COLUMN encuestas.estado        IS 'Flujo unidireccional: borrador → activa → finalizada.';
COMMENT ON COLUMN encuestas.activada_en   IS 'Momento en que la encuesta pasó a estado activa.';
COMMENT ON COLUMN encuestas.finalizada_en IS 'Momento en que la encuesta fue finalizada.';
COMMENT ON COLUMN encuestas.creado_por    IS 'Administrador que creó la encuesta.';

-- Índices de consultas frecuentes
CREATE INDEX idx_encuestas_estado     ON encuestas(estado);
CREATE INDEX idx_encuestas_creado_por ON encuestas(creado_por);

-- ============================================================
--  TABLA: opciones
-- ============================================================

CREATE TABLE opciones (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    encuesta_id UUID         NOT NULL,
    texto       VARCHAR(500) NOT NULL,
    orden       SMALLINT     NOT NULL DEFAULT 0,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_opciones_encuesta
        FOREIGN KEY (encuesta_id) REFERENCES encuestas(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_opciones_texto  CHECK (LENGTH(TRIM(texto)) > 0),
    CONSTRAINT chk_opciones_orden  CHECK (orden >= 0),
    CONSTRAINT uq_opciones_orden   UNIQUE (encuesta_id, orden)
);

COMMENT ON TABLE  opciones             IS 'Opciones de respuesta para cada encuesta.';
COMMENT ON COLUMN opciones.orden       IS 'Posición de la opción dentro de la encuesta (0-based).';
COMMENT ON COLUMN opciones.encuesta_id IS 'Encuesta a la que pertenece esta opción.';

CREATE INDEX idx_opciones_encuesta_id ON opciones(encuesta_id);

-- ============================================================
--  TABLA: votos
-- ============================================================

CREATE TABLE votos (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    encuesta_id UUID        NOT NULL,
    usuario_id  UUID        NOT NULL,
    opcion_id   UUID        NOT NULL,
    votado_en   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_votos_encuesta
        FOREIGN KEY (encuesta_id) REFERENCES encuestas(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_votos_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_votos_opcion
        FOREIGN KEY (opcion_id) REFERENCES opciones(id)
        ON DELETE RESTRICT,

    -- Un usuario solo puede votar UNA VEZ por encuesta
    CONSTRAINT uq_votos_usuario_encuesta UNIQUE (usuario_id, encuesta_id)
);

COMMENT ON TABLE  votos                      IS 'Registro inmutable de votos emitidos por usuarios.';
COMMENT ON COLUMN votos.encuesta_id          IS 'Encuesta en la que se emitió el voto.';
COMMENT ON COLUMN votos.usuario_id           IS 'Usuario que emitió el voto.';
COMMENT ON COLUMN votos.opcion_id            IS 'Opción elegida por el usuario.';
COMMENT ON COLUMN votos.votado_en            IS 'Marca de tiempo exacta en que se registró el voto.';

CREATE INDEX idx_votos_encuesta_id ON votos(encuesta_id);
CREATE INDEX idx_votos_usuario_id  ON votos(usuario_id);
CREATE INDEX idx_votos_opcion_id   ON votos(opcion_id);

-- ============================================================
--  FUNCIÓN + TRIGGER: actualizado_en automático
-- ============================================================

CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql AS
$$
BEGIN
    NEW.actualizado_en = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_usuarios_updated_at
    BEFORE UPDATE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_encuestas_updated_at
    BEFORE UPDATE ON encuestas
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ============================================================
--  FUNCIÓN + TRIGGER: validar transiciones de estado
--  Flujo permitido: borrador → activa → finalizada (solo avanzar)
-- ============================================================

CREATE OR REPLACE FUNCTION fn_validar_transicion_estado()
RETURNS TRIGGER
LANGUAGE plpgsql AS
$$
BEGIN
    -- Permitir sin cambios de estado
    IF NEW.estado = OLD.estado THEN
        RETURN NEW;
    END IF;

    -- Transiciones válidas
    IF (OLD.estado = 'borrador'   AND NEW.estado = 'activa')     OR
       (OLD.estado = 'activa'     AND NEW.estado = 'finalizada')
    THEN
        -- Registrar fechas de ciclo de vida automáticamente
        IF NEW.estado = 'activa'      THEN NEW.activada_en   = NOW(); END IF;
        IF NEW.estado = 'finalizada'  THEN NEW.finalizada_en = NOW(); END IF;
        RETURN NEW;
    END IF;

    RAISE EXCEPTION
        'Transición de estado no permitida: % → %. Flujo válido: borrador → activa → finalizada.',
        OLD.estado, NEW.estado;
END;
$$;

CREATE TRIGGER trg_encuestas_estado
    BEFORE UPDATE OF estado ON encuestas
    FOR EACH ROW EXECUTE FUNCTION fn_validar_transicion_estado();

-- ============================================================
--  FUNCIÓN + TRIGGER: bloquear edición de encuestas no-borrador
-- ============================================================

CREATE OR REPLACE FUNCTION fn_bloquear_edicion_encuesta()
RETURNS TRIGGER
LANGUAGE plpgsql AS
$$
BEGIN
    -- Cambio de estado siempre permitido (lo maneja su propio trigger)
    IF NEW.estado IS DISTINCT FROM OLD.estado THEN
        RETURN NEW;
    END IF;

    IF OLD.estado <> 'borrador' THEN
        RAISE EXCEPTION
            'La encuesta "%" no puede editarse porque su estado es "%" (solo se editan encuestas en borrador).',
            OLD.titulo, OLD.estado;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_encuestas_bloquear_edicion
    BEFORE UPDATE ON encuestas
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_edicion_encuesta();

-- ============================================================
--  FUNCIÓN + TRIGGER: bloquear edición de opciones en encuestas no-borrador
-- ============================================================

CREATE OR REPLACE FUNCTION fn_bloquear_edicion_opciones()
RETURNS TRIGGER
LANGUAGE plpgsql AS
$$
DECLARE
    v_estado survey_status;
BEGIN
    SELECT estado INTO v_estado
    FROM   encuestas
    WHERE  id = COALESCE(NEW.encuesta_id, OLD.encuesta_id);

    IF v_estado <> 'borrador' THEN
        RAISE EXCEPTION
            'No se pueden modificar opciones de una encuesta en estado "%". Solo se permite en borrador.',
            v_estado;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_opciones_bloquear_edicion
    BEFORE INSERT OR UPDATE OR DELETE ON opciones
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_edicion_opciones();

-- ============================================================
--  FUNCIÓN + TRIGGER: validar voto sobre encuesta activa
--  y que la opción pertenezca a esa encuesta
-- ============================================================

CREATE OR REPLACE FUNCTION fn_validar_voto()
RETURNS TRIGGER
LANGUAGE plpgsql AS
$$
DECLARE
    v_estado         survey_status;
    v_opcion_valida  BOOLEAN;
BEGIN
    -- 1. La encuesta debe estar activa
    SELECT estado INTO v_estado
    FROM   encuestas
    WHERE  id = NEW.encuesta_id;

    IF v_estado IS NULL THEN
        RAISE EXCEPTION 'Encuesta no encontrada: %', NEW.encuesta_id;
    END IF;

    IF v_estado <> 'activa' THEN
        RAISE EXCEPTION
            'No se puede votar en una encuesta con estado "%". Solo se permite votar en encuestas activas.',
            v_estado;
    END IF;

    -- 2. La opción debe pertenecer a la encuesta
    SELECT EXISTS (
        SELECT 1 FROM opciones
        WHERE  id = NEW.opcion_id
        AND    encuesta_id = NEW.encuesta_id
    ) INTO v_opcion_valida;

    IF NOT v_opcion_valida THEN
        RAISE EXCEPTION
            'La opción % no pertenece a la encuesta %.',
            NEW.opcion_id, NEW.encuesta_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_votos_validar
    BEFORE INSERT ON votos
    FOR EACH ROW EXECUTE FUNCTION fn_validar_voto();

-- ============================================================
--  VISTAS ÚTILES
-- ============================================================

-- Resultados agregados por encuesta y opción
CREATE VIEW v_resultados_encuesta AS
SELECT
    e.id            AS encuesta_id,
    e.titulo        AS encuesta_titulo,
    e.estado,
    o.id            AS opcion_id,
    o.texto         AS opcion_texto,
    o.orden,
    COUNT(v.id)     AS total_votos,
    ROUND(
        COUNT(v.id) * 100.0
        / NULLIF(SUM(COUNT(v.id)) OVER (PARTITION BY e.id), 0),
        2
    )               AS porcentaje
FROM  encuestas e
JOIN  opciones  o ON o.encuesta_id = e.id
LEFT JOIN votos v ON v.opcion_id   = o.id
GROUP BY e.id, e.titulo, e.estado, o.id, o.texto, o.orden
ORDER BY e.id, o.orden;

COMMENT ON VIEW v_resultados_encuesta IS
    'Resultados agregados con totales y porcentaje por opción.';

-- Encuestas finalizadas en las que participó un usuario
CREATE VIEW v_participacion_usuario AS
SELECT
    u.id            AS usuario_id,
    u.nombre        AS usuario_nombre,
    e.id            AS encuesta_id,
    e.titulo        AS encuesta_titulo,
    e.finalizada_en,
    o.texto         AS opcion_votada,
    vt.votado_en
FROM  votos     vt
JOIN  usuarios  u  ON u.id = vt.usuario_id
JOIN  encuestas e  ON e.id = vt.encuesta_id AND e.estado = 'finalizada'
JOIN  opciones  o  ON o.id = vt.opcion_id
ORDER BY vt.votado_en DESC;

COMMENT ON VIEW v_participacion_usuario IS
    'Historial de participación de usuarios en encuestas finalizadas.';

-- ============================================================
--  DATOS DE EJEMPLO
-- ============================================================

-- Usuarios
INSERT INTO usuarios (nombre, email, password_hash, rol) VALUES
    ('Admin Principal',  'admin@votaciones.com',  '$2b$12$hash_admin_aqui',   'admin'),
    ('María García',     'maria@example.com',      '$2b$12$hash_maria_aqui',   'usuario'),
    ('Carlos López',     'carlos@example.com',     '$2b$12$hash_carlos_aqui',  'usuario');

-- Encuesta en borrador
INSERT INTO encuestas (titulo, descripcion, creado_por)
VALUES (
    '¿Cuál es tu lenguaje de programación favorito?',
    'Ayúdanos a conocer las preferencias del equipo.',
    (SELECT id FROM usuarios WHERE email = 'admin@votaciones.com')
);

-- Opciones de la encuesta
INSERT INTO opciones (encuesta_id, texto, orden)
VALUES
    ((SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'), 'Java',       0),
    ((SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'), 'Python',     1),
    ((SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'), 'TypeScript', 2),
    ((SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'), 'Go',         3);

-- Activar la encuesta (borrador → activa)
UPDATE encuestas
SET    estado = 'activa'
WHERE  titulo LIKE '¿Cuál es tu lenguaje%';

-- Registrar votos de ejemplo
INSERT INTO votos (encuesta_id, usuario_id, opcion_id)
VALUES (
    (SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'),
    (SELECT id FROM usuarios  WHERE email  = 'maria@example.com'),
    (SELECT id FROM opciones  WHERE texto  = 'Java'
       AND encuesta_id = (SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'))
),
(
    (SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'),
    (SELECT id FROM usuarios  WHERE email  = 'carlos@example.com'),
    (SELECT id FROM opciones  WHERE texto  = 'Python'
       AND encuesta_id = (SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'))
);
