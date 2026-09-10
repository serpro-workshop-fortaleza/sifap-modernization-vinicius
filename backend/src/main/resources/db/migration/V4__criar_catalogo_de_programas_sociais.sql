-- Catalogo de programas sociais.
--
-- Cobre REQ-PRG-001 (codigo unico), REQ-PRG-002 e REQ-PRG-003 (validacao),
-- REQ-PRG-007 (encerramento), REQ-PRG-010 (faixas), REQ-PRG-011 (regioes)
-- e REQ-PRG-013 (autoria).
--
-- 45 registros parametrizam 4,2 milhoes de beneficiarios: cerca de 93 mil
-- pessoas por programa. Um erro aqui nao afeta um beneficiario, afeta um
-- programa inteiro. E CADPROG.NSP:96-106 grava tudo que a tela devolve, sem
-- uma unica verificacao.

CREATE SEQUENCE social_program_id_seq AS BIGINT INCREMENT BY 50;
CREATE SEQUENCE calculation_band_id_seq AS BIGINT INCREMENT BY 50;
CREATE SEQUENCE regional_parameter_id_seq AS BIGINT INCREMENT BY 50;
CREATE SEQUENCE adjustment_coefficient_id_seq AS BIGINT INCREMENT BY 50;

CREATE TABLE social_program (
    id                BIGINT       NOT NULL DEFAULT nextval('social_program_id_seq') PRIMARY KEY,
    code              VARCHAR(4)   NOT NULL UNIQUE,
    name              VARCHAR(60)  NOT NULL,
    acronym           VARCHAR(10),
    type              VARCHAR(12)  NOT NULL,
    status            VARCHAR(12)  NOT NULL,
    status_reason     VARCHAR(60),

    -- NUMERIC(9,2) e nao (7,2): o campo legado e P 7,2 com teto de 99.999,99
    -- e a tela do CADPROG captura P9.2, de modo que valor acima do teto trunca
    -- em silencio na gravacao.
    amount_base       NUMERIC(9,2) NOT NULL,
    adjustment_factor NUMERIC(7,4) NOT NULL DEFAULT 0,

    max_percap_income NUMERIC(9,2),
    age_min           SMALLINT     NOT NULL DEFAULT 0,
    age_max           SMALLINT     NOT NULL DEFAULT 0,
    eligibility_code  VARCHAR(5),
    creation_law      VARCHAR(20),

    started_at        DATE         NOT NULL,
    closed_at         DATE,

    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(50)  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    updated_by        VARCHAR(50)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_program_type CHECK (type IN ('ASSISTENCIA', 'TRABALHO', 'PREVIDENCIA')),
    CONSTRAINT ck_program_status CHECK (status IN ('ATIVO', 'INATIVO', 'ENCERRADO')),
    CONSTRAINT ck_program_amount CHECK (amount_base > 0),
    -- Zero significa ausencia de limite (SOCPROG.ddm:57-58): a restricao nao pode
    -- confundir isso com faixa invertida.
    CONSTRAINT ck_program_age_range CHECK (age_min = 0 OR age_max = 0 OR age_min <= age_max),
    -- Torna verificavel no banco que encerrado e terminal.
    CONSTRAINT ck_program_closed CHECK (status <> 'ENCERRADO' OR closed_at IS NOT NULL)
);

-- Estrutura declarada em SOCPROG.ddm:69 desde 1997 e nunca preenchida, enquanto
-- CALCBENF.NSN:129-137 carrega cinco fatores de faixa por MOVE no fonte.
CREATE TABLE calculation_band (
    id                BIGINT       NOT NULL DEFAULT nextval('calculation_band_id_seq') PRIMARY KEY,
    social_program_id BIGINT       NOT NULL REFERENCES social_program (id) ON DELETE CASCADE,
    income_from       NUMERIC(9,2) NOT NULL,
    income_to         NUMERIC(9,2),
    multiplier        NUMERIC(7,4) NOT NULL,
    additional_amount NUMERIC(9,2) NOT NULL DEFAULT 0,
    accumulates       BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_band_range CHECK (income_to IS NULL OR income_from < income_to),
    CONSTRAINT ck_band_from CHECK (income_from >= 0),
    CONSTRAINT uq_band_start UNIQUE (social_program_id, income_from)
);

-- Estrutura declarada em SOCPROG.ddm:86 desde 2002, enquanto CALCBENF.NSN:99-125
-- carrega 27 fatores rotulados por UF e os indexa por um codigo que vale de 1 a 5.
CREATE TABLE regional_parameter (
    id                BIGINT       NOT NULL DEFAULT nextval('regional_parameter_id_seq') PRIMARY KEY,
    social_program_id BIGINT       NOT NULL REFERENCES social_program (id) ON DELETE CASCADE,
    region_code       VARCHAR(2)   NOT NULL,
    multiplier        NUMERIC(7,4) NOT NULL,
    complement_amount NUMERIC(9,2) NOT NULL DEFAULT 0,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT ck_region_code CHECK (region_code IN ('01', '02', '03', '04', '05', '99')),
    CONSTRAINT uq_regional_program UNIQUE (social_program_id, region_code)
);

-- O coeficiente vive em tabela para ser questionavel. Uma constante em
-- static final seria mais simples e reproduziria o defeito de CADPROG.NSP:124:
-- numero sem origem, escondido no codigo.
CREATE TABLE adjustment_coefficient (
    id          BIGINT       NOT NULL DEFAULT nextval('adjustment_coefficient_id_seq') PRIMARY KEY,
    coefficient NUMERIC(9,6) NOT NULL,
    valid_from  DATE         NOT NULL,
    valid_to    DATE,
    source      VARCHAR(120) NOT NULL,

    CONSTRAINT uq_coefficient_from UNIQUE (valid_from),
    CONSTRAINT ck_coefficient_period CHECK (valid_to IS NULL OR valid_from < valid_to)
);

INSERT INTO adjustment_coefficient (coefficient, valid_from, valid_to, source)
VALUES (0.347215, DATE '2003-07-05', NULL,
        'CADPROG.NSP:124 - origem desconhecida (SIFAP-M-04)');

CREATE INDEX idx_band_program ON calculation_band (social_program_id, income_from);
CREATE INDEX idx_regional_program ON regional_parameter (social_program_id);

-- Equivalente ao superdescritor S2 SUPER-TYPE-STAT (SOCPROG.ddm:104-105).
CREATE INDEX idx_program_type_status ON social_program (type, status);

GRANT SELECT, INSERT, UPDATE, DELETE ON calculation_band, regional_parameter TO sifap_app;
GRANT SELECT, INSERT, UPDATE ON social_program TO sifap_app;
GRANT SELECT ON adjustment_coefficient TO sifap_app;
GRANT USAGE ON SEQUENCE social_program_id_seq, calculation_band_id_seq,
    regional_parameter_id_seq, adjustment_coefficient_id_seq TO sifap_app;
