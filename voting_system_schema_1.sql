Create database votaciones


CREATE TABLE usuarios (
    id             CHAR(36)      NOT NULL DEFAULT (UUID()),
    nombre         VARCHAR(100)  NOT NULL,
    email          VARCHAR(255)  NOT NULL,
    password_hash  VARCHAR(255)  NOT NULL  COMMENT 'Hash bcrypt/argon2. Nunca texto plano.',
    rol            ENUM('admin','usuario') NOT NULL DEFAULT 'usuario'
                   COMMENT 'admin gestiona encuestas; usuario vota.',
    activo         BOOLEAN       NOT NULL DEFAULT TRUE
                   COMMENT 'Soft-delete: FALSE deshabilita el acceso.',
    creado_en      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT chk_usuarios_email
        CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Usuarios del sistema con roles admin y usuario.';

-- ============================================================
--  TABLA: encuestas
-- ============================================================

CREATE TABLE encuestas (
    id             CHAR(36)      NOT NULL DEFAULT (UUID()),
    titulo         VARCHAR(255)  NOT NULL,
    descripcion    TEXT,
    estado         ENUM('borrador','activa','finalizada') NOT NULL DEFAULT 'borrador'
                   COMMENT 'Flujo unidireccional: borrador -> activa -> finalizada.',
    creado_por     CHAR(36)      NOT NULL  COMMENT 'Administrador que creó la encuesta.',
    creado_en      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activada_en    DATETIME      NULL DEFAULT NULL,
    finalizada_en  DATETIME      NULL DEFAULT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_encuestas_creado_por
        FOREIGN KEY (creado_por) REFERENCES usuarios(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_encuestas_titulo CHECK (CHAR_LENGTH(TRIM(titulo)) > 0),

    -- Coherencia de fechas de ciclo de vida
    CONSTRAINT chk_encuestas_activada_en
        CHECK (activada_en IS NULL OR estado IN ('activa','finalizada')),
    CONSTRAINT chk_encuestas_finalizada_en
        CHECK (finalizada_en IS NULL OR estado = 'finalizada'),
    CONSTRAINT chk_encuestas_fechas_orden
        CHECK (activada_en IS NULL OR finalizada_en IS NULL
               OR activada_en <= finalizada_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Encuestas creadas por administradores.';

CREATE INDEX idx_encuestas_estado     ON encuestas(estado);
CREATE INDEX idx_encuestas_creado_por ON encuestas(creado_por);

-- ============================================================
--  TABLA: opciones
-- ============================================================

CREATE TABLE opciones (
    id          CHAR(36)     NOT NULL DEFAULT (UUID()),
    encuesta_id CHAR(36)     NOT NULL  COMMENT 'Encuesta a la que pertenece la opción.',
    texto       VARCHAR(500) NOT NULL,
    orden       SMALLINT     NOT NULL DEFAULT 0
                COMMENT 'Posición de la opción dentro de la encuesta (0-based).',
    creado_en   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_opciones_encuesta
        FOREIGN KEY (encuesta_id) REFERENCES encuestas(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_opciones_texto CHECK (CHAR_LENGTH(TRIM(texto)) > 0),
    CONSTRAINT chk_opciones_orden CHECK (orden >= 0),
    CONSTRAINT uq_opciones_orden  UNIQUE (encuesta_id, orden)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Opciones de respuesta para cada encuesta.';

CREATE INDEX idx_opciones_encuesta_id ON opciones(encuesta_id);

-- ============================================================
--  TABLA: votos
-- ============================================================

CREATE TABLE votos (
    id          CHAR(36)  NOT NULL DEFAULT (UUID()),
    encuesta_id CHAR(36)  NOT NULL,
    usuario_id  CHAR(36)  NOT NULL,
    opcion_id   CHAR(36)  NOT NULL,
    votado_en   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Registro inmutable de votos emitidos por usuarios.';

CREATE INDEX idx_votos_encuesta_id ON votos(encuesta_id);
CREATE INDEX idx_votos_usuario_id  ON votos(usuario_id);
CREATE INDEX idx_votos_opcion_id   ON votos(opcion_id);

-- ============================================================
--  TRIGGERS
--  (DELIMITER es necesario para el cliente mysql / mysql CLI)
-- ============================================================

DELIMITER $$

-- ------------------------------------------------------------
--  usuarios: actualizado_en automático
-- ------------------------------------------------------------
CREATE TRIGGER trg_usuarios_before_update
BEFORE UPDATE ON usuarios
FOR EACH ROW
BEGIN
    SET NEW.actualizado_en = NOW();
END$$

-- ------------------------------------------------------------
--  encuestas: lógica consolidada BEFORE UPDATE
--    1) actualizado_en automático
--    2) si cambia el estado -> validar transición y sellar fechas
--    3) si NO cambia el estado -> bloquear edición fuera de borrador
-- ------------------------------------------------------------
CREATE TRIGGER trg_encuestas_before_update
BEFORE UPDATE ON encuestas
FOR EACH ROW
BEGIN
    -- 1) updated_at
    SET NEW.actualizado_en = NOW();

    IF NOT (NEW.estado <=> OLD.estado) THEN
        -- 2) cambio de estado: solo se permite avanzar
        IF (OLD.estado = 'borrador' AND NEW.estado = 'activa')
        OR (OLD.estado = 'activa'   AND NEW.estado = 'finalizada') THEN
            IF NEW.estado = 'activa'     THEN SET NEW.activada_en   = NOW(); END IF;
            IF NEW.estado = 'finalizada' THEN SET NEW.finalizada_en = NOW(); END IF;
        ELSE
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Transicion de estado no permitida. Flujo: borrador -> activa -> finalizada.';
        END IF;
    ELSE
        -- 3) sin cambio de estado: bloquear edicion si no es borrador
        IF OLD.estado <> 'borrador' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Solo se pueden editar encuestas en estado borrador.';
        END IF;
    END IF;
END$$

-- ------------------------------------------------------------
--  opciones: bloquear modificaciones fuera de borrador
--  (MySQL no soporta INSERT/UPDATE/DELETE en un mismo trigger)
-- ------------------------------------------------------------
CREATE TRIGGER trg_opciones_before_insert
BEFORE INSERT ON opciones
FOR EACH ROW
BEGIN
    DECLARE v_estado VARCHAR(20);
    SELECT estado INTO v_estado FROM encuestas WHERE id = NEW.encuesta_id;
    IF v_estado <> 'borrador' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo se pueden agregar opciones a encuestas en borrador.';
    END IF;
END$$

CREATE TRIGGER trg_opciones_before_update
BEFORE UPDATE ON opciones
FOR EACH ROW
BEGIN
    DECLARE v_estado VARCHAR(20);
    SELECT estado INTO v_estado FROM encuestas WHERE id = NEW.encuesta_id;
    IF v_estado <> 'borrador' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo se pueden modificar opciones de encuestas en borrador.';
    END IF;
END$$

CREATE TRIGGER trg_opciones_before_delete
BEFORE DELETE ON opciones
FOR EACH ROW
BEGIN
    DECLARE v_estado VARCHAR(20);
    SELECT estado INTO v_estado FROM encuestas WHERE id = OLD.encuesta_id;
    -- v_estado puede ser NULL si la encuesta ya fue borrada (ON DELETE CASCADE)
    IF v_estado IS NOT NULL AND v_estado <> 'borrador' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo se pueden borrar opciones de encuestas en borrador.';
    END IF;
END$$

-- ------------------------------------------------------------
--  votos: validar encuesta activa + opción perteneciente
-- ------------------------------------------------------------
CREATE TRIGGER trg_votos_before_insert
BEFORE INSERT ON votos
FOR EACH ROW
BEGIN
    DECLARE v_estado        VARCHAR(20);
    DECLARE v_opcion_valida INT DEFAULT 0;

    SELECT estado INTO v_estado FROM encuestas WHERE id = NEW.encuesta_id;

    IF v_estado IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Encuesta no encontrada.';
    END IF;

    IF v_estado <> 'activa' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo se permite votar en encuestas activas.';
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM opciones
        WHERE id = NEW.opcion_id AND encuesta_id = NEW.encuesta_id
    ) INTO v_opcion_valida;

    IF v_opcion_valida = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La opcion no pertenece a la encuesta indicada.';
    END IF;
END$$

DELIMITER ;

-- ============================================================
--  VISTAS ÚTILES
-- ============================================================

-- Resultados agregados por encuesta y opción
CREATE VIEW v_resultados_encuesta AS
SELECT
    e.id        AS encuesta_id,
    e.titulo    AS encuesta_titulo,
    e.estado,
    o.id        AS opcion_id,
    o.texto     AS opcion_texto,
    o.orden,
    COUNT(v.id) AS total_votos,
    ROUND(
        COUNT(v.id) * 100.0
        / NULLIF(SUM(COUNT(v.id)) OVER (PARTITION BY e.id), 0),
        2
    )           AS porcentaje
FROM encuestas e
JOIN opciones  o ON o.encuesta_id = e.id
LEFT JOIN votos v ON v.opcion_id  = o.id
GROUP BY e.id, e.titulo, e.estado, o.id, o.texto, o.orden
ORDER BY e.id, o.orden;

-- Encuestas finalizadas en las que participó un usuario
CREATE VIEW v_participacion_usuario AS
SELECT
    u.id     AS usuario_id,
    u.nombre AS usuario_nombre,
    e.id     AS encuesta_id,
    e.titulo AS encuesta_titulo,
    e.finalizada_en,
    o.texto  AS opcion_votada,
    vt.votado_en
FROM votos     vt
JOIN usuarios  u ON u.id = vt.usuario_id
JOIN encuestas e ON e.id = vt.encuesta_id AND e.estado = 'finalizada'
JOIN opciones  o ON o.id = vt.opcion_id
ORDER BY vt.votado_en DESC;

-- ============================================================
--  DATOS DE EJEMPLO
-- ============================================================

-- Usuarios
INSERT INTO usuarios (nombre, email, password_hash, rol) VALUES
    ('Admin Principal', 'admin@votaciones.com', '$2b$12$hash_admin_aqui',  'admin'),
    ('Maria Garcia',    'maria@example.com',    '$2b$12$hash_maria_aqui',  'usuario'),
    ('Carlos Lopez',    'carlos@example.com',   '$2b$12$hash_carlos_aqui', 'usuario');

-- Encuesta en borrador
INSERT INTO encuestas (titulo, descripcion, creado_por)
VALUES (
    '¿Cuál es tu lenguaje de programación favorito?',
    'Ayúdanos a conocer las preferencias del equipo.',
    (SELECT id FROM usuarios WHERE email = 'admin@votaciones.com')
);

-- Opciones (la encuesta aún está en borrador, así que se permiten)
INSERT INTO opciones (encuesta_id, texto, orden) VALUES
    ((SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'), 'Java',       0),
    ((SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'), 'Python',     1),
    ((SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'), 'TypeScript', 2),
    ((SELECT id FROM encuestas WHERE titulo LIKE '¿Cuál es tu lenguaje%'), 'Go',         3);

-- Activar la encuesta (borrador -> activa; el trigger sella activada_en)
UPDATE encuestas
SET    estado = 'activa'
WHERE  titulo LIKE '¿Cuál es tu lenguaje%';

-- Votos de ejemplo
INSERT INTO votos (encuesta_id, usuario_id, opcion_id) VALUES
(
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
