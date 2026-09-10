# Plano técnico — Catálogo de Programas Sociais

> **Especificação:** [`spec.md`](spec.md) · **Tarefas:** [`tasks.md`](tasks.md)

| Campo | Valor |
|---|---|
| **Fatia de migração** | 3 — Catálogo |
| **Contexto** | `socialprogram` |
| **Requisitos cobertos** | `REQ-PRG-001` a `REQ-PRG-015` |
| **Volume de referência** | ~45 registros, parametrizando 4,2 milhões de beneficiários |

---

## Decisões de projeto

### O agregado é o programa, com faixas e regiões dentro

Faixas de cálculo e parâmetros regionais são grupos periódicos no legado, sem identidade própria e sem sentido fora do programa. Seguem o mesmo tratamento dos dependentes na Fatia 2: `@OneToMany` com entidade própria, situação e restrições no banco.

A diferença em relação aos dependentes é a invariante: **as faixas não podem se sobrepor**. Isso é regra do agregado, verificada em `SocialProgram.replaceBands`, e não do banco — a sobreposição depende de comparar pares, o que nenhuma restrição declarativa expressa.

### O valor base deixa de carregar o fator

O `REQ-PRG-004` separa duas coisas que o legado funde em `CADPROG.NSP:125-130`:

| Conceito | Onde vive |
|---|---|
| Valor base informado | `amount_base`, gravado como digitado |
| Fator de ajuste do programa | `adjustment_factor`, campo próprio, já existente no dicionário |
| Coeficiente do fator | Tabela `adjustment_coefficient`, com vigência |

O cálculo do fator derivado permanece idêntico ao legado e sai da gravação para um método do domínio:

```java
public BigDecimal derivedFactor(BigDecimal coefficient) {
    return BigDecimal.ONE.add(adjustmentFactor.multiply(coefficient));
}
```

O coeficiente vem de tabela porque o `ADR-0007` exige que ele seja questionável. Uma constante em `static final` seria mais simples e reproduziria o defeito: valor sem origem, escondido no código.

### O coeficiente tem vigência, não é uma linha só

`0.347215` está em vigor desde 2003, segundo a data de alteração do `CADPROG`. Se a validação humana do `SIFAP-M-04` trouxer outro valor, ou revelar que houve mudança, a tabela comporta a história sem reescrever a existente.

```sql
CREATE TABLE adjustment_coefficient (
    id            BIGINT NOT NULL DEFAULT nextval(...) PRIMARY KEY,
    coefficient   NUMERIC(9,6) NOT NULL,
    valid_from    DATE         NOT NULL,
    valid_to      DATE,
    source        VARCHAR(120) NOT NULL,
    CONSTRAINT uq_coefficient_period UNIQUE (valid_from)
);
```

O `source` do registro inicial é literal: `CADPROG.NSP:124 — origem desconhecida (SIFAP-M-04)`. É a documentação viva da lacuna.

### Situação com transição, diferente do beneficiário

O [ADR-0006](../../docs/adr/0006-situacao-cadastral-do-beneficiario.md) não definiu máquina de estados para o beneficiário, por falta de fonte. Aqui a fonte existe: `SOCPROG.ddm:36` declara `DT-CLOSURE` com `0=ACTIVE`, o que estabelece que **encerrado é terminal** — um programa encerrado tem data de encerramento, e a data não se desfaz.

| De | Para | Permitido |
|---|---|---|
| `ATIVO` | `INATIVO`, `ENCERRADO` | sim |
| `INATIVO` | `ATIVO`, `ENCERRADO` | sim |
| `ENCERRADO` | qualquer | **não** |

A regra é estreita e ancorada no dicionário. Não inventa o que não se sabe.

### Interface de consulta separada por consumidor

Dois consumidores com necessidades distintas:

```java
public interface SocialProgramQuery {
    // consumido pela Fatia 4 — o conjunto que VALELEG e CALCBENF leem
    Optional<SocialProgramParameters> parametersOf(String programCode);

    // consumido pela interface de administração
    Optional<SocialProgramView> findByCode(String programCode);
    List<SocialProgramView> findByStatus(SocialProgramStatus status);
}
```

`SocialProgramParameters` é deliberadamente enxuto: tipo, valor base, fator de ajuste, situação, código de elegibilidade, faixa etária e teto de renda. É exatamente o que `VALELEG.NSN:106-111` e `CALCBENF.NSN:194-196` leem.

Separar evita que a Fatia 4 dependa de campos que não usa — o acoplamento que o `REQ-BEN-019` da Fatia 2 criticou no legado.

> Consulta a programa social **não** publica evento de acesso. São 45 registros de parametrização, sem dado pessoal. Auditar essa leitura repetiria o problema de volume que motivou a `PORT. CGTI 213/2010`. Decisão já registrada em [`domain-events.md`](../../02-modern-spec/domain-events.md).

---

## Modelo de dados

```sql
CREATE TABLE social_program (
    id                  BIGINT       NOT NULL DEFAULT nextval('social_program_id_seq') PRIMARY KEY,
    code                VARCHAR(4)   NOT NULL UNIQUE,
    name                VARCHAR(60)  NOT NULL,
    acronym             VARCHAR(10),
    type                VARCHAR(12)  NOT NULL,
    status              VARCHAR(12)  NOT NULL,
    status_reason       VARCHAR(60),
    amount_base         NUMERIC(9,2) NOT NULL,
    adjustment_factor   NUMERIC(7,4) NOT NULL DEFAULT 0,
    max_percap_income   NUMERIC(9,2),
    age_min             SMALLINT     NOT NULL DEFAULT 0,
    age_max             SMALLINT     NOT NULL DEFAULT 0,
    eligibility_code    VARCHAR(5),
    created_law         VARCHAR(20),
    started_at          DATE         NOT NULL,
    closed_at           DATE,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(50)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_program_type CHECK (type IN ('ASSISTENCIA', 'TRABALHO', 'PREVIDENCIA')),
    CONSTRAINT ck_program_status CHECK (status IN ('ATIVO', 'INATIVO', 'ENCERRADO')),
    CONSTRAINT ck_program_amount CHECK (amount_base > 0),
    CONSTRAINT ck_program_age_range CHECK (age_min = 0 OR age_max = 0 OR age_min <= age_max),
    CONSTRAINT ck_program_closed CHECK (status <> 'ENCERRADO' OR closed_at IS NOT NULL)
);

CREATE TABLE calculation_band (
    id                  BIGINT       NOT NULL DEFAULT nextval('calculation_band_id_seq') PRIMARY KEY,
    social_program_id   BIGINT       NOT NULL REFERENCES social_program (id) ON DELETE CASCADE,
    income_from         NUMERIC(9,2) NOT NULL,
    income_to           NUMERIC(9,2),
    multiplier          NUMERIC(7,4) NOT NULL,
    additional_amount   NUMERIC(9,2) NOT NULL DEFAULT 0,
    accumulates         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_band_range CHECK (income_to IS NULL OR income_from < income_to),
    CONSTRAINT uq_band_start UNIQUE (social_program_id, income_from)
);

CREATE TABLE regional_parameter (
    id                  BIGINT       NOT NULL DEFAULT nextval('regional_parameter_id_seq') PRIMARY KEY,
    social_program_id   BIGINT       NOT NULL REFERENCES social_program (id) ON DELETE CASCADE,
    region_code         VARCHAR(2)   NOT NULL,
    multiplier          NUMERIC(7,4) NOT NULL,
    complement_amount   NUMERIC(9,2) NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_region_code CHECK (region_code IN ('01','02','03','04','05','99')),
    CONSTRAINT uq_regional_program UNIQUE (social_program_id, region_code)
);
```

Quatro decisões merecem nota.

**`amount_base` é `NUMERIC(9,2)`, e não `NUMERIC(7,2)`.** O campo legado é `P 7,2`, com teto de R$ 99.999,99, enquanto a tela do `CADPROG` captura `P9.2`. Um valor acima do teto trunca em silêncio na gravação. A largura do destino passa a comportar a origem.

**`ck_program_age_range` admite zero nos dois lados.** Zero significa "sem limite", conforme `SOCPROG.ddm:57-58`, e a restrição não pode confundir ausência de limite com faixa invertida.

**`ck_program_closed` amarra situação e data.** É o que torna "encerrado é terminal" verificável no banco, e não apenas na aplicação.

**As faixas não têm restrição de sobreposição.** `uq_band_start` impede duas faixas com o mesmo início, mas sobreposição parcial exige comparar pares — invariante do agregado, testada em `SocialProgramTest`.

### Índice

```sql
CREATE INDEX idx_program_type_status ON social_program (type, status);
```

Equivalente ao superdescritor `S2 SUPER-TYPE-STAT` (`SOCPROG.ddm:104-105`), que é o padrão de acesso que o dicionário declara.

---

## Contrato do módulo

### Eventos publicados

O catálogo previa **um** evento, porque o legado só implementa inclusão. Os requisitos `REQ-PRG-006` e `REQ-PRG-007` criam duas operações novas, e portanto dois eventos.

| Evento | Ação de auditoria | Requisito | Situação no catálogo |
|---|---|---|---|
| `SocialProgramRegistered` | `INCLUSAO` | `REQ-PRG-014` | Confirmado |
| `SocialProgramUpdated` | `ALTERACAO` | `REQ-PRG-006` | **Novo** |
| `SocialProgramStatusChanged` | `ALTERACAO` | `REQ-PRG-007` | **Novo** |

Nenhum carrega CPF afetado, preservando o `RESET #AUD-CPF` de `CADPROG.NSP:144`. É o primeiro contexto cujos eventos não têm sujeito pessoal, e a trilha já comporta isso: `subjectCpf()` é `Optional` desde a Fatia 1.

Acrescentar evento é barato porque o consumidor existe — foi exatamente o argumento registrado em [`domain-events.md`](../../02-modern-spec/domain-events.md).

### API REST

| Método | Caminho | Requisitos |
|---|---|---|
| `POST` | `/api/v1/social-programs` | `REQ-PRG-001`, `002`, `003`, `004` |
| `PUT` | `/api/v1/social-programs/{code}` | `REQ-PRG-006` |
| `POST` | `/api/v1/social-programs/{code}/status` | `REQ-PRG-007` |
| `PUT` | `/api/v1/social-programs/{code}/bands` | `REQ-PRG-010` |
| `PUT` | `/api/v1/social-programs/{code}/regions` | `REQ-PRG-011` |
| `GET` | `/api/v1/social-programs/{code}` | `REQ-PRG-001` |
| `GET` | `/api/v1/social-programs?status=ATIVO` | `REQ-PRG-012` |

Faixas e regiões são substituídas em bloco, e não item a item: o conjunto é a unidade que precisa ser coerente, e validar sobreposição incrementalmente permitiria estados intermediários inválidos.

---

## Estrutura de pacotes

```text
backend/src/main/java/br/gov/sifap/socialprogram/
├── SocialProgramQuery.java          # interface pública
├── SocialProgramParameters.java     # projeção enxuta para o cálculo
├── SocialProgramView.java           # projeção de administração
├── SocialProgramStatus.java         # enum público
├── SocialProgramType.java           # enum público
├── event/
│   ├── SocialProgramRegistered.java
│   ├── SocialProgramUpdated.java
│   └── SocialProgramStatusChanged.java
└── internal/
    ├── SocialProgram.java           # raiz do agregado
    ├── CalculationBand.java
    ├── RegionalParameter.java
    ├── AdjustmentCoefficient.java
    ├── SocialProgramRepository.java
    ├── SocialProgramService.java
    ├── SocialProgramController.java
    └── migration/
        └── SocialProgramLoader.java
```

---

## Estratégia de testes

| Requisito | Verificação |
|---|---|
| `REQ-PRG-002` | Tipo, nome e valor base inválidos impedem a construção do agregado |
| `REQ-PRG-003` | Faixa 65–18 recusada; faixa 0–0 aceita como ausência de limite |
| `REQ-PRG-004` | Valor informado é o valor gravado, verificado no banco |
| `REQ-PRG-005` | Fator derivado reproduz o resultado de `CADPROG.NSP:124` para os mesmos insumos |
| `REQ-PRG-007` | Programa encerrado recusa qualquer transição; encerrar sem data é impossível |
| `REQ-PRG-009` | Projeção do cálculo traz exatamente os sete campos que `VALELEG` e `CALCBENF` leem |
| `REQ-PRG-010` | Faixas sobrepostas recusadas; faixas contíguas aceitas |
| `REQ-PRG-011` | Região fora do domínio recusada pelo banco |
| `REQ-PRG-015` | Carga com parametrização vazia produz inventário com contagem zero e o registra |

### Teste de equivalência com o legado

O `REQ-PRG-005` é o único ponto desta fatia em que um número precisa bater com o legado:

```java
// REQ-PRG-005: CADPROG.NSP:124 calcula 1.00 + (FACTOR-ADJUST * 0.347215).
// O coeficiente e preservado; o que muda e onde ele mora.
@Test
void deve_reproduzir_o_fator_derivado_do_legado() { ... }
```

### Limitação de caracterização

A mesma das fatias anteriores: **não há ambiente legado disponível.** Vale registrar um agravante próprio desta fatia — não se sabe o que existe nos 45 registros. O `REQ-PRG-015` transforma isso em medição, mas a medição só acontece na carga.

---

## Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Valores base migrados já vêm ajustados pelo fator | Consulta mostra número que ninguém digitou, e o cálculo pode aplicar o fator duas vezes | `REQ-PRG-015` sinaliza cada ocorrência; reverter é impossível sem o coeficiente histórico |
| Faixas e regiões nascem vazias | A Fatia 4 continua com as constantes no fonte | Decisão dela; a estrutura existe e não custa nada enquanto vazia |
| Validação do `SIFAP-M-04` traz outro coeficiente | Fator derivado muda para inclusões novas | Tabela com vigência; trocar é inserir uma linha |
| 45 registros parametrizam 4,2 milhões de pessoas | Erro de parametrização atinge ~93 mil beneficiários | `REQ-PRG-002` e `REQ-PRG-003`; validação que hoje não existe |

---

## Fora deste plano

| Item | Destino |
|---|---|
| Preencher faixas e regiões com os valores do `CALCBENF` | Fatia 4, que os consome |
| Correção do índice da tabela regional | Fatia 4 |
| Tipos de desconto aplicáveis | Fatia 4, junto do `CALCDSCT` |
| Interface Next.js de administração | Fatia própria de frontend |
| Campo `FACTOR-K` | Não será modelado; ver [ADR-0007](../../docs/adr/0007-parametrizacao-do-programa-social.md) |

---

## Ajustes durante a implementação

Um ponto divergiu do desenho, por restrição do mapeamento objeto-relacional.

| Decidido no plano | Implementado | Razão |
|---|---|---|
| Faixas e regiões como `List` | `Set` no agregado, `List` ordenada na projeção | O Hibernate recusa buscar duas coleções `bag` no mesmo *fetch join* (`MultipleBagFetchException`), e o programa tem duas. A ordem nunca foi propriedade do dado — é do modo de exibir —, então passou para o mapper. |

Nenhuma regra de negócio mudou: a invariante de não sobreposição continua no agregado, e a ordenação por renda continua garantida na projeção.

---

## Definição de pronto

- [x] Estrutura de pacote definida e alinhada ao mapa de contextos.
- [x] Modelo de dados definido, com restrições justificadas por evidência do dicionário.
- [x] Contrato de comunicação especificado, com os dois eventos novos identificados.
- [x] Riscos identificados com mitigação.
- [x] Tarefas geradas em [`tasks.md`](tasks.md).
