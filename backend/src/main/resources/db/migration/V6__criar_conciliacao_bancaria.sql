-- Conciliacao bancaria.
--
-- Cobre REQ-REC-003 (arquivo processado uma vez), REQ-REC-009 (divergencia
-- como estado) e REQ-REC-016 (banco pagador do proprio retorno).
--
-- Tres das colunas abaixo nao sao novidade desta migracao: PAYMENT.ddm:95,
-- :97 e :110 as declaram, as duas ultimas desde 17/11/2015, e nenhum programa
-- do acervo as grava.

ALTER TABLE payment
    -- PAYMENT.ddm:95 - dominio fixo C=RECONCILED D=DIVERGENT.
    ADD COLUMN reconciliation_status VARCHAR(12),
    -- PAYMENT.ddm:97 - BANK-CONFIRMED AMOUNT.
    ADD COLUMN amount_reconciled     NUMERIC(11,2),
    -- PAYMENT.ddm:72 - codigo FEBRABAN. BATCHCON.NSP:212 grava o literal 1.
    ADD COLUMN bank_code             VARCHAR(3),
    ADD COLUMN bank_return_code      VARCHAR(2),
    ADD COLUMN credit_date           DATE,
    ADD COLUMN reconciled_at         TIMESTAMPTZ;

ALTER TABLE payment
    ADD CONSTRAINT ck_payment_reconciliation_status CHECK (
        reconciliation_status IS NULL
        OR reconciliation_status IN ('CONCILIADO', 'DIVERGENTE')
    ),
    -- Divergente sem o valor do banco seria a mesma opacidade de hoje.
    ADD CONSTRAINT ck_payment_divergence_amount CHECK (
        reconciliation_status <> 'DIVERGENTE' OR amount_reconciled IS NOT NULL
    );

-- AC-009.2: listar os divergentes de um periodo sem varrer a trilha.
CREATE INDEX idx_payment_reconciliation
    ON payment (reference_period, reconciliation_status)
    WHERE reconciliation_status IS NOT NULL;

CREATE SEQUENCE reconciliation_file_id_seq AS BIGINT INCREMENT BY 50;
CREATE SEQUENCE reconciliation_issue_id_seq AS BIGINT INCREMENT BY 500;

-- REQ-REC-003. A identidade e o resumo do conteudo: BATCHCON.NSP:139-141
-- registra que o nome informado na tela e documentacao, porque a ligacao real
-- e a DD CMWKF01 do JCL.
CREATE TABLE reconciliation_file (
    id             BIGINT       NOT NULL DEFAULT nextval('reconciliation_file_id_seq') PRIMARY KEY,
    sha256         VARCHAR(64)  NOT NULL UNIQUE,
    declared_name  VARCHAR(120) NOT NULL,
    reference_period VARCHAR(6) NOT NULL,
    records_read   INTEGER      NOT NULL DEFAULT 0,
    status         VARCHAR(12)  NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL,
    processed_by   VARCHAR(50)  NOT NULL,

    CONSTRAINT ck_reconciliation_file_status CHECK (
        status IN ('EM_ANDAMENTO', 'CONCLUIDO', 'INTERROMPIDO')
    )
);

-- REQ-REC-008, REQ-REC-010 e REQ-REC-016. O legado escreve estas ocorrencias
-- no CMPRINT de uma execucao manual e nao as guarda em lugar nenhum.
CREATE TABLE reconciliation_issue (
    id              BIGINT        NOT NULL DEFAULT nextval('reconciliation_issue_id_seq') PRIMARY KEY,
    file_id         BIGINT        NOT NULL REFERENCES reconciliation_file (id),
    issue_type      VARCHAR(24)   NOT NULL,
    cpf             VARCHAR(11),
    reference_period VARCHAR(6),
    payment_number  VARCHAR(20),
    declared_amount NUMERIC(11,2),
    return_code     VARCHAR(2),
    detail          VARCHAR(200)  NOT NULL,
    detected_at     TIMESTAMPTZ   NOT NULL,

    CONSTRAINT ck_reconciliation_issue_type CHECK (
        issue_type IN ('SEM_CORRESPONDENCIA', 'CODIGO_DESCONHECIDO', 'AMBIGUIDADE',
                       'VALOR_INVALIDO', 'BANCO_DESCONHECIDO', 'TRANSICAO_INVALIDA')
    )
);

CREATE INDEX idx_reconciliation_issue_file ON reconciliation_issue (file_id, issue_type);

GRANT SELECT, INSERT, UPDATE ON reconciliation_file TO sifap_app;
GRANT SELECT, INSERT ON reconciliation_issue TO sifap_app;
GRANT USAGE ON SEQUENCE reconciliation_file_id_seq, reconciliation_issue_id_seq TO sifap_app;
