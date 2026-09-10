-- Trilha de auditoria do SIFAP 2.0.
--
-- Cobre REQ-AUD-003 (numeracao por sequencia), REQ-AUD-004 (instante em
-- TIMESTAMPTZ), REQ-AUD-005 (autor e perfil), REQ-AUD-006 (valores anterior e
-- posterior), REQ-AUD-009 (contexto de execucao) e REQ-AUD-012 (consulta por
-- entidade e periodo).
--
-- Duas tabelas, e nao uma: no legado 89% do volume e consulta e conciliacao e
-- os eventos de negocio sao 0,02% de 311 GB (AUDIT.ddm:145-158). Ciclos de vida
-- distintos justificam armazenamentos distintos.

-- INCREMENT BY 500 casa com o allocationSize do Hibernate: em carga de folha sao
-- 500 eventos por ida ao banco em vez de 500 idas. REQ-AUD-003 exige unicidade,
-- nao contiguidade.
CREATE SEQUENCE audit_change_event_id_seq AS BIGINT INCREMENT BY 500;
CREATE SEQUENCE audit_access_event_id_seq AS BIGINT INCREMENT BY 500;

CREATE TABLE audit_change_event (
    id            BIGINT      NOT NULL DEFAULT nextval('audit_change_event_id_seq'),
    occurred_at   TIMESTAMPTZ NOT NULL,
    action        VARCHAR(20) NOT NULL,
    entity_type   VARCHAR(40) NOT NULL,
    entity_id     VARCHAR(50) NOT NULL,
    subject_cpf   VARCHAR(11),
    actor_id      VARCHAR(50) NOT NULL,
    actor_profile VARCHAR(30) NOT NULL,
    actor_type    VARCHAR(20) NOT NULL,
    changes       JSONB,
    batch_run_id  VARCHAR(50),
    PRIMARY KEY (id, occurred_at),
    -- CONSULTA nao pertence a esta tabela: o roteamento vira invariante do banco.
    CONSTRAINT ck_change_action CHECK (
        action IN ('INCLUSAO', 'ALTERACAO', 'EXCLUSAO', 'CONCILIACAO', 'PROCESSAMENTO')
    )
) PARTITION BY RANGE (occurred_at);

CREATE TABLE audit_access_event (
    id            BIGINT      NOT NULL DEFAULT nextval('audit_access_event_id_seq'),
    occurred_at   TIMESTAMPTZ NOT NULL,
    action        VARCHAR(20) NOT NULL,
    entity_type   VARCHAR(40) NOT NULL,
    entity_id     VARCHAR(50) NOT NULL,
    subject_cpf   VARCHAR(11),
    actor_id      VARCHAR(50) NOT NULL,
    actor_profile VARCHAR(30) NOT NULL,
    actor_type    VARCHAR(20) NOT NULL,
    PRIMARY KEY (id, occurred_at),
    CONSTRAINT ck_access_action CHECK (action = 'CONSULTA')
) PARTITION BY RANGE (occurred_at);

-- Equivalente ao superdescritor S2, que AUDIT.ddm:102 manda usar em consulta pesada.
CREATE INDEX idx_change_entity ON audit_change_event (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_change_subject ON audit_change_event (subject_cpf, occurred_at DESC);
CREATE INDEX idx_change_batch ON audit_change_event (batch_run_id) WHERE batch_run_id IS NOT NULL;

CREATE INDEX idx_access_entity ON audit_access_event (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_access_subject ON audit_access_event (subject_cpf, occurred_at DESC);

-- Particao mensal declarativa: o expurgo de retencao passa a ser DROP TABLE,
-- e nao DELETE sobre centenas de milhoes de linhas.
CREATE OR REPLACE FUNCTION audit_create_partition(p_table TEXT, p_month DATE)
RETURNS TEXT
SECURITY DEFINER
-- Fixo e sem pg_catalog a frente: o catalogo ja e pesquisado implicitamente, e
-- deixa-lo em primeiro faria o CREATE TABLE tentar escrever no catalogo do sistema.
SET search_path = public
AS $$
DECLARE
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end   DATE := (date_trunc('month', p_month) + INTERVAL '1 month')::DATE;
    v_name  TEXT := p_table || '_' || to_char(v_start, 'YYYYMM');
BEGIN
    IF p_table NOT IN ('audit_change_event', 'audit_access_event') THEN
        RAISE EXCEPTION 'tabela fora da trilha de auditoria: %', p_table;
    END IF;

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        v_name, p_table, v_start, v_end);

    RETURN v_name;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    v_offset INT;
BEGIN
    FOR v_offset IN -1..3 LOOP
        PERFORM audit_create_partition(
            'audit_change_event', (CURRENT_DATE + (v_offset || ' month')::INTERVAL)::DATE);
        PERFORM audit_create_partition(
            'audit_access_event', (CURRENT_DATE + (v_offset || ' month')::INTERVAL)::DATE);
    END LOOP;
END $$;
