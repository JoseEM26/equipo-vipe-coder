-- ============================================================
--  V2: estado 'cancelada' (cancelar / desactivar una encuesta)
-- ============================================================
--  Permite retirar una encuesta desde 'borrador' o 'activa' sin
--  borrar datos (los votos existentes se conservan).
--
--  Transiciones tras esta migración:
--    borrador  -> activa | cancelada
--    activa    -> finalizada | cancelada
--    finalizada, cancelada : terminales
-- ============================================================

-- 1) Columna de auditoría (cuándo se canceló)
ALTER TABLE encuestas
    ADD COLUMN cancelada_en DATETIME NULL DEFAULT NULL AFTER finalizada_en;

-- 2) Ampliar el ENUM de estado
ALTER TABLE encuestas
    MODIFY COLUMN estado ENUM('borrador','activa','finalizada','cancelada')
        NOT NULL DEFAULT 'borrador'
        COMMENT 'borrador -> activa -> finalizada; cancelable desde borrador o activa.';

-- 3) Ajustar el CHECK de activada_en para admitir 'cancelada'
--    (una encuesta activa->cancelada conserva su activada_en)
ALTER TABLE encuestas DROP CHECK chk_encuestas_activada_en;
ALTER TABLE encuestas
    ADD CONSTRAINT chk_encuestas_activada_en
        CHECK (activada_en IS NULL OR estado IN ('activa','finalizada','cancelada'));

-- 4) Coherencia de cancelada_en
ALTER TABLE encuestas
    ADD CONSTRAINT chk_encuestas_cancelada_en
        CHECK (cancelada_en IS NULL OR estado = 'cancelada');

-- 5) Recrear el trigger BEFORE UPDATE con las nuevas transiciones
DROP TRIGGER IF EXISTS trg_encuestas_before_update;

DELIMITER $$
CREATE TRIGGER trg_encuestas_before_update
BEFORE UPDATE ON encuestas
FOR EACH ROW
BEGIN
    -- updated_at automático
    SET NEW.actualizado_en = NOW();

    IF NOT (NEW.estado <=> OLD.estado) THEN
        -- cambio de estado: solo transiciones permitidas
        IF (OLD.estado = 'borrador' AND NEW.estado = 'activa')
        OR (OLD.estado = 'activa'   AND NEW.estado = 'finalizada')
        OR (OLD.estado = 'borrador' AND NEW.estado = 'cancelada')
        OR (OLD.estado = 'activa'   AND NEW.estado = 'cancelada') THEN
            IF NEW.estado = 'activa'     THEN SET NEW.activada_en   = NOW(); END IF;
            IF NEW.estado = 'finalizada' THEN SET NEW.finalizada_en = NOW(); END IF;
            IF NEW.estado = 'cancelada'  THEN SET NEW.cancelada_en  = NOW(); END IF;
        ELSE
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Transicion de estado no permitida. Flujo: borrador->activa->finalizada; cancelable desde borrador o activa.';
        END IF;
    ELSE
        -- sin cambio de estado: bloquear edicion fuera de borrador
        IF OLD.estado <> 'borrador' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Solo se pueden editar encuestas en estado borrador.';
        END IF;
    END IF;
END$$
DELIMITER ;
