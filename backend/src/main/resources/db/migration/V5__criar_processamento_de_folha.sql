-- Processamento de folha.
--
-- Cobre REQ-PAY-001 (um pagamento por periodo), REQ-PAY-002 (numero unico),
-- REQ-PAY-016 (tipo no dominio), REQ-PAY-018 (tipos de desconto) e
-- REQ-PAY-020 (liquido consistente).
--
-- 180 milhoes de registros, crescendo 3,8 milhoes por mes.

CREATE SEQUENCE payment_id_seq AS BIGINT INCREMENT BY 500;
CREATE SEQUENCE payment_discount_id_seq AS BIGINT INCREMENT BY 500;
CREATE SEQUENCE correction_index_id_seq AS BIGINT INCREMENT BY 50;

CREATE TABLE payment (
    id                BIGINT        NOT NULL DEFAULT nextval('payment_id_seq'),
    cpf               VARCHAR(11)   NOT NULL,
    program_code      VARCHAR(4)    NOT NULL,
    reference_period  VARCHAR(6)    NOT NULL,
    cycle_id          VARCHAR(20)   NOT NULL,

    amount_base       NUMERIC(11,2) NOT NULL,
    -- Cada fator aplicado, para que a divergencia com a RN-013 seja mensuravel
    -- em vez de discutivel (AC-010.2).
    applied_factors   JSONB         NOT NULL,
    amount_gross      NUMERIC(11,2) NOT NULL,
    amount_thirteenth NUMERIC(11,2) NOT NULL DEFAULT 0,
    amount_bonus      NUMERIC(11,2) NOT NULL DEFAULT 0,
    -- Separada do total porque CALCBENF e CALCDSCT cobram contribuicoes diferentes
    -- sobre o mesmo pagamento (SIFAP-M-10); com a coluna propria, qual das duas
    -- incidiu deixa de ser questao de leitura de codigo.
    amount_social     NUMERIC(11,2) NOT NULL DEFAULT 0,
    amount_discount   NUMERIC(11,2) NOT NULL DEFAULT 0,
    amount_net        NUMERIC(11,2) NOT NULL,

    status            VARCHAR(14)   NOT NULL,
    type              VARCHAR(12)   NOT NULL,

    amount_correction NUMERIC(11,2),
    correction_index  NUMERIC(11,6),
    correction_period VARCHAR(6),
    corrected_at      DATE,

    generated_at      TIMESTAMPTZ   NOT NULL,
    generated_by      VARCHAR(50)   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,

    PRIMARY KEY (id, reference_period),
    -- O REQ-PAY-001 inteiro. BATCHPGT.NSP:294 verifica antes de gravar e deixa
    -- uma janela entre a verificacao e a gravacao; aqui nao ha janela.
    CONSTRAINT uq_payment_cpf_period UNIQUE (cpf, reference_period),
    CONSTRAINT ck_payment_status CHECK (
        status IN ('PENDENTE', 'GERADO', 'EMITIDO', 'CONFIRMADO',
                   'DEVOLVIDO', 'CANCELADO', 'REPROCESSADO')
    ),
    -- CALCBENF.NSN:273 grava 'D' em dezembro, valor que PAYMENT.ddm:74 nao declara.
    CONSTRAINT ck_payment_type CHECK (type IN ('NORMAL', 'RETROATIVO', 'ABONO', 'CORRECAO')),
    CONSTRAINT ck_payment_net CHECK (amount_net >= 0),
    -- Torna impossivel o defeito do CALCDSCT, que atualiza o total de descontos
    -- e nao recalcula o liquido que segue para o banco.
    CONSTRAINT ck_payment_net_consistent CHECK (amount_net = amount_gross - amount_discount)
) PARTITION BY RANGE (reference_period);

CREATE TABLE payment_discount (
    id          BIGINT        NOT NULL DEFAULT nextval('payment_discount_id_seq') PRIMARY KEY,
    payment_id  BIGINT        NOT NULL,
    type        VARCHAR(16)   NOT NULL,
    amount      NUMERIC(11,2) NOT NULL,
    percentage  NUMERIC(5,2),
    case_number VARCHAR(20),

    -- Os oito tipos de PAYMENT.ddm:44. O legado move esse campo A3 para uma
    -- variavel A1 e trata cinco deles como NONE, ignorando o desconto em silencio.
    CONSTRAINT ck_discount_type CHECK (
        type IN ('IRRF', 'JUDICIAL', 'CONSIGNADO', 'PENSAO',
                 'EMPRESTIMO', 'TAXA', 'OUTRO', 'EXTRAORDINARIO')
    ),
    CONSTRAINT ck_discount_amount CHECK (amount >= 0)
);

CREATE INDEX idx_discount_payment ON payment_discount (payment_id);

-- CALC-INDEX-ACCUM cobre dez anos; fora deles o indice fica 1.000000 e a
-- correcao resulta em zero, indistinguivel de "nao havia correcao devida".
CREATE TABLE correction_index (
    id     BIGINT        NOT NULL DEFAULT nextval('correction_index_id_seq') PRIMARY KEY,
    period VARCHAR(6)    NOT NULL UNIQUE,
    rate   NUMERIC(11,6) NOT NULL,
    source VARCHAR(120)  NOT NULL
);

CREATE OR REPLACE FUNCTION payment_create_partition(p_period VARCHAR)
RETURNS TEXT
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_year  INT  := substring(p_period from 1 for 4)::INT;
    v_month INT  := substring(p_period from 5 for 2)::INT;
    v_start VARCHAR := p_period;
    v_end   VARCHAR := to_char(make_date(v_year, v_month, 1) + INTERVAL '1 month', 'YYYYMM');
    v_name  TEXT := 'payment_' || p_period;
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF payment FOR VALUES FROM (%L) TO (%L)',
        v_name, v_start, v_end);
    RETURN v_name;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    v_offset INT;
BEGIN
    FOR v_offset IN -2..3 LOOP
        PERFORM payment_create_partition(
            to_char(CURRENT_DATE + (v_offset || ' month')::INTERVAL, 'YYYYMM'));
    END LOOP;
END $$;

-- Equivalente ao superdescritor S1 SUPER-CPF-PERIOD, unica consulta otimizada
-- de todo o acervo legado (BATCHPGT.NSP:294).
CREATE INDEX idx_payment_cpf_period ON payment (cpf, reference_period DESC);
CREATE INDEX idx_payment_cycle ON payment (cycle_id);
CREATE INDEX idx_payment_status_period ON payment (status, reference_period);

GRANT SELECT, INSERT, UPDATE ON payment TO sifap_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON payment_discount TO sifap_app;
GRANT SELECT ON correction_index TO sifap_app;
GRANT USAGE ON SEQUENCE payment_id_seq, payment_discount_id_seq,
    correction_index_id_seq TO sifap_app;
REVOKE EXECUTE ON FUNCTION payment_create_partition(VARCHAR) FROM PUBLIC;
