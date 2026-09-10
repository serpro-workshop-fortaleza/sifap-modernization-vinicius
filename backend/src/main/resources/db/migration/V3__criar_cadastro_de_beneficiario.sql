-- Cadastro de beneficiario e dependentes.
--
-- Cobre REQ-BEN-001 (CPF unico), REQ-BEN-004 (situacao no dominio),
-- REQ-BEN-010 (situacao do dependente), REQ-BEN-011 (dependente sem CPF),
-- REQ-BEN-012 (CPF de dependente unico por titular) e REQ-BEN-019 (autoria).

-- Sequencia, e nao IDENTITY: a carga inicial insere 4,2 milhoes de registros e
-- o Hibernate desabilita insercao em lote quando a chave vem por IDENTITY.
CREATE SEQUENCE beneficiary_id_seq AS BIGINT INCREMENT BY 500;
CREATE SEQUENCE dependent_id_seq AS BIGINT INCREMENT BY 500;
CREATE SEQUENCE beneficiary_migration_issue_id_seq AS BIGINT INCREMENT BY 500;

CREATE TABLE beneficiary (
    id                BIGINT       NOT NULL DEFAULT nextval('beneficiary_id_seq') PRIMARY KEY,
    cpf               VARCHAR(11)  NOT NULL UNIQUE,
    nis               VARCHAR(11)  UNIQUE,
    full_name         VARCHAR(60)  NOT NULL,
    -- Anulavel pela mesma razao de status: o campo e N8 no Adabas e pode conter
    -- zeros ou data impossivel. O REQ-BEN-020 proibe descartar o registro.
    birth_date        DATE,
    -- CHAR(1) e nao VARCHAR: com length 1 o Hibernate mapeia o enum para CHAR,
    -- e ddl-auto validate recusa a divergencia.
    sex               CHAR(1)      NOT NULL,

    -- Anulavel apenas para registro migrado: BENEFIC nao tem situacao confiavel,
    -- porque CADBENEF.NSP:314 grava branco em toda alteracao ate 75 anos.
    -- Registro criado pelo sistema novo tem situacao garantida na construcao.
    status            VARCHAR(12),
    status_reason     VARCHAR(60),
    status_changed_at TIMESTAMPTZ,

    program_code      VARCHAR(4),
    family_income     NUMERIC(11,2),

    street            VARCHAR(120),
    street_number     VARCHAR(10),
    complement        VARCHAR(30),
    district          VARCHAR(40),
    city              VARCHAR(40),
    uf                VARCHAR(2),
    postal_code       VARCHAR(8),
    region_code       VARCHAR(2),

    phone_mobile      VARCHAR(15),
    rg_number         VARCHAR(15),

    registered_at     DATE         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(50)  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    updated_by        VARCHAR(50)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_beneficiary_status CHECK (
        status IN ('ATIVO', 'SUSPENSO', 'CANCELADO', 'INATIVO', 'DESLIGADO')
    ),
    CONSTRAINT ck_beneficiary_sex CHECK (sex IN ('M', 'F', 'I'))
);

CREATE TABLE dependent (
    id             BIGINT      NOT NULL DEFAULT nextval('dependent_id_seq') PRIMARY KEY,
    beneficiary_id BIGINT      NOT NULL REFERENCES beneficiary (id) ON DELETE CASCADE,
    cpf            VARCHAR(11),
    full_name      VARCHAR(60) NOT NULL,
    birth_date     DATE,
    relation       VARCHAR(2)  NOT NULL,
    status         VARCHAR(12) NOT NULL,
    disability     BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_dependent_status CHECK (status IN ('ATIVO', 'INATIVO', 'DESLIGADO')),
    -- NULL nao colide com NULL em PostgreSQL: a mesma restricao atende o
    -- REQ-BEN-012 e permite varios dependentes sem CPF do REQ-BEN-011.
    CONSTRAINT uq_dependent_cpf UNIQUE (beneficiary_id, cpf)
);

CREATE INDEX idx_dependent_beneficiary ON dependent (beneficiary_id);

-- Sinalizacao de carga fica fora do dominio de negocio: um valor PENDENTE em
-- status obrigaria a Fatia 4 a conhecer um estado que so existe pela migracao.
CREATE TABLE beneficiary_migration_issue (
    id             BIGINT       NOT NULL DEFAULT nextval('beneficiary_migration_issue_id_seq') PRIMARY KEY,
    beneficiary_id BIGINT       REFERENCES beneficiary (id) ON DELETE CASCADE,
    source_cpf     VARCHAR(20)  NOT NULL,
    issue_type     VARCHAR(40)  NOT NULL,
    field_name     VARCHAR(40),
    original_value VARCHAR(120),
    detected_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_migration_issue_type ON beneficiary_migration_issue (issue_type);
CREATE INDEX idx_migration_issue_field ON beneficiary_migration_issue (field_name, original_value);

-- Equivalentes aos superdescritores S2 e S3 e ao subdescritor SA, que o
-- dicionario indica como padrao de acesso real (BENEFIC.ddm:147-152).
CREATE INDEX idx_beneficiary_uf_status ON beneficiary (uf, status);
CREATE INDEX idx_beneficiary_program_status ON beneficiary (program_code, status);
CREATE INDEX idx_beneficiary_birth_year ON beneficiary (EXTRACT(YEAR FROM birth_date));

GRANT SELECT, INSERT, UPDATE ON beneficiary, dependent, beneficiary_migration_issue TO sifap_app;
GRANT USAGE ON SEQUENCE beneficiary_id_seq, dependent_id_seq,
    beneficiary_migration_issue_id_seq TO sifap_app;
