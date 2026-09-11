# Plano técnico — Processamento de Folha

> **Especificação:** [`spec.md`](spec.md) · **Tarefas:** [`tasks.md`](tasks.md)

| Campo | Valor |
|---|---|
| **Fatia de migração** | 4 — Folha |
| **Contexto** | `payment`, parte 1 de 2 |
| **Requisitos cobertos** | `REQ-PAY-001` a `REQ-PAY-025` |
| **Volume de referência** | ~180.000.000 registros, crescendo 3,8 milhões por mês em janela de 4 horas |

---

## Decisões de projeto

### O cálculo não persiste

É a correção que resolve o `SIFAP-M-05` e o `SIFAP-M-08` de uma vez: `BenefitCalculator` é uma **função pura** que recebe insumos e devolve um resultado. Não conhece repositório, não abre transação, não grava.

```java
public BenefitCalculation calculate(BenefitInput input, ProgramParameters parameters);
```

Quem persiste é o ciclo, em um único ponto. O legado grava em dois (`CALCBENF.NSN:319` e `BATCHPGT.NSP:488`) porque o cálculo foi convertido de programa online para subprograma em 2011 e a gravação veio junto.

Consequência direta: o teste de que existe um pagamento por beneficiário deixa de ser uma verificação sobre comportamento e passa a ser uma propriedade da estrutura — não há segundo lugar que possa gravar.

### Cada fator é um componente próprio

O `REQ-PAY-010` preserva a fórmula multiplicativa; os `PS` preservam regras que a validação humana pode derrubar. Isolar cada fator é o que torna a reversão localizada.

```text
payment/internal/calculation/
├── BenefitCalculator.java       # orquestra, não decide
├── RegionalFactor.java          # REQ-PAY-011
├── FamilyFactor.java            # dependentes
├── IncomeFactor.java            # REQ-PAY-012
├── AgeFactor.java               # faixa etária
└── ThirteenthSalary.java        # REQ-PAY-015
```

Se o `SIFAP-M-09` for validado contra a fórmula multiplicativa, o que muda é `BenefitCalculator`. Se o `SIFAP-M-11` for validado, muda `IncomeFactor`. Nada além.

### O resultado carrega os fatores aplicados

O `AC-010.2` exige que cada fator seja recuperável. Não é conveniência de depuração: é o que transforma a divergência com a `RN-013` em medição.

```java
public record BenefitCalculation(
        BigDecimal amountBase,
        Map<FactorType, BigDecimal> appliedFactors,
        BigDecimal grossAmount,
        BigDecimal thirteenthAmount,
        BigDecimal bonusAmount,
        List<AppliedDiscount> discounts,
        BigDecimal netAmount) { }
```

O evento `PaymentGenerated` carrega esse mapa. Depois de uma folha, é possível responder quanto o fator regional contribuiu para o total pago — pergunta que hoje não tem resposta.

### O fator de renda deixa de ser parcial

O defeito do `REQ-PAY-012` é que a última faixa termina em `9.999,99` e acima disso a variável mantém o valor do beneficiário anterior.

**Decisão:** `IncomeFactor` é uma função total. A faixa superior do programa é aberta — a Fatia 3 já admite `income_to` nulo — e um programa sem faixa que cubra a renda faz o cálculo falhar com motivo, em vez de adotar valor silencioso.

```java
BigDecimal factorFor(BigDecimal income, List<CalculationBand> bands) {
    return bands.stream()
            .filter(band -> band.covers(income))
            .findFirst()
            .orElseThrow(() -> new DomainRuleException("REQ-PAY-012", "renda fora das faixas do programa"))
            .multiplier();
}
```

### Truncamento explícito, não acidental

O legado trunca porque move para um campo inteiro (`#AMT-TEMP (N11)`) e divide de volta. O comportamento é correto e a causa é acidental.

**Decisão:** `RoundingMode.DOWN` em escala 2, aplicado por um único método. Um `BigDecimal` com escala maior nunca chega ao banco.

```java
static BigDecimal truncate(BigDecimal value) {
    return value.setScale(2, RoundingMode.DOWN);
}
```

### O ciclo processa em lote, com paginação por chave

3,8 milhões de beneficiários em 4 horas são 264 por segundo. O `AuditableEventBatch`, medido na `T-110` da Fatia 1, entrega mais de 10 mil eventos por segundo — a auditoria não é o gargalo.

**Decisão:** leitura paginada por CPF, processamento em blocos de 5.000, com inserção em lote e publicação de um `AuditableEventBatch` por bloco.

A ordem de leitura é por CPF, como no legado (`BATCHPGT.NSP:250`), mas o resultado **não depende dela** — é o que o `REQ-PAY-012` garante.

### Descontos como parte do agregado Pagamento

`PAYMENT.ddm:41-48` declara os descontos como grupo periódico de até 8. Mesmo tratamento dos dependentes na Fatia 2 e das faixas na Fatia 3: `@OneToMany` com entidade própria.

A invariante de teto vive no agregado, porque depende de somar todos os itens e distinguir os de origem judicial:

```java
// REQ-PAY-019: o teto legado corta o total acumulado e reduz o judicial,
// que o proprio programa declara nao ter teto.
BigDecimal cappedTotal() {
    BigDecimal judicial = sumOf(AppliedDiscount::isJudicial);
    BigDecimal others = sumOf(d -> !d.isJudicial());
    return judicial.add(others.min(grossAmount.multiply(CAP)));
}
```

### Idempotência por restrição, não por verificação

O legado verifica duplicidade com `FIND NUMBER` antes de gravar (`BATCHPGT.NSP:294`). Entre a verificação e a gravação existe uma janela.

**Decisão:** `UNIQUE (cpf, reference_period)` no banco. A verificação prévia continua, por eficiência; a garantia é da restrição.

---

## Modelo de dados

```sql
CREATE TABLE payment (
    id                BIGINT       NOT NULL DEFAULT nextval('payment_id_seq') PRIMARY KEY,
    cpf               VARCHAR(11)  NOT NULL,
    program_code      VARCHAR(4)   NOT NULL,
    reference_period  CHAR(6)      NOT NULL,
    cycle_id          VARCHAR(20)  NOT NULL,

    amount_base       NUMERIC(11,2) NOT NULL,
    applied_factors   JSONB         NOT NULL,
    amount_gross      NUMERIC(11,2) NOT NULL,
    amount_thirteenth NUMERIC(11,2) NOT NULL DEFAULT 0,
    amount_bonus      NUMERIC(11,2) NOT NULL DEFAULT 0,
    amount_discount   NUMERIC(11,2) NOT NULL DEFAULT 0,
    amount_net        NUMERIC(11,2) NOT NULL,

    status            VARCHAR(12)  NOT NULL,
    type              VARCHAR(12)  NOT NULL,

    amount_correction NUMERIC(11,2),
    correction_index  NUMERIC(11,6),
    corrected_at      DATE,

    generated_at      TIMESTAMPTZ  NOT NULL,
    generated_by      VARCHAR(50)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_payment_cpf_period UNIQUE (cpf, reference_period),
    CONSTRAINT ck_payment_status CHECK (
        status IN ('PENDENTE','GERADO','EMITIDO','CONFIRMADO','DEVOLVIDO','CANCELADO','REPROCESSADO')
    ),
    CONSTRAINT ck_payment_type CHECK (type IN ('NORMAL','RETROATIVO','ABONO','CORRECAO')),
    CONSTRAINT ck_payment_net CHECK (amount_net >= 0),
    CONSTRAINT ck_payment_net_consistent CHECK (amount_net = amount_gross - amount_discount)
);

CREATE TABLE payment_discount (
    id             BIGINT       NOT NULL DEFAULT nextval('payment_discount_id_seq') PRIMARY KEY,
    payment_id     BIGINT       NOT NULL REFERENCES payment (id) ON DELETE CASCADE,
    type           VARCHAR(12)  NOT NULL,
    amount         NUMERIC(11,2) NOT NULL,
    percentage     NUMERIC(5,2),
    case_number    VARCHAR(20),
    CONSTRAINT ck_discount_type CHECK (
        type IN ('IRRF','JUDICIAL','CONSIGNADO','PENSAO','EMPRESTIMO','TAXA','OUTRO','EXTRAORDINARIO')
    ),
    CONSTRAINT ck_discount_amount CHECK (amount >= 0)
);

CREATE TABLE correction_index (
    id          BIGINT       NOT NULL DEFAULT nextval('correction_index_id_seq') PRIMARY KEY,
    period      CHAR(6)      NOT NULL UNIQUE,
    rate        NUMERIC(11,6) NOT NULL,
    source      VARCHAR(120) NOT NULL
);
```

Três restrições carregam requisitos que seriam frágeis em código.

**`uq_payment_cpf_period`** é o `REQ-PAY-001` inteiro. Nenhuma janela entre verificar e gravar.

**`ck_payment_net_consistent`** é o `REQ-PAY-020`. O defeito do `CALCDSCT` — atualizar descontos sem recalcular o líquido — deixa de ser possível: o banco recusa.

**`ck_payment_type`** é o `REQ-PAY-016`. O valor `D` que `CALCBENF.NSN:273` grava não tem correspondente e não entra.

### Particionamento

180 milhões de registros, crescendo 3,8 milhões por mês. Partição por range em `reference_period`, com a mesma mecânica já validada na trilha de auditoria da Fatia 1.

A diferença é a retenção: pagamento não expira. As partições existem para consulta e manutenção, não para expurgo.

### Índices

```sql
CREATE INDEX idx_payment_cpf_period ON payment (cpf, reference_period DESC);
CREATE INDEX idx_payment_cycle ON payment (cycle_id);
CREATE INDEX idx_payment_status_period ON payment (status, reference_period);
```

O primeiro é o superdescritor `S1 SUPER-CPF-PERIOD`, que `BATCHPGT.NSP:294` usa e que o Estágio 1 identificou como a única consulta otimizada de todo o acervo.

---

## Contrato do módulo

### Eventos publicados

Confirmam o catálogo, com uma correção de leitura: o legado **audita** a correção retroativa (`CALCCORR.NSP:243`), ao contrário do que o catálogo registrava.

| Evento | Ação | Requisito | Situação |
|---|---|---|---|
| `PaymentGenerated` | `INCLUSAO` | `REQ-PAY-023` | Confirmado |
| `PaymentDiscountsApplied` | `ALTERACAO` | `REQ-PAY-023` | Confirmado — o legado não audita |
| `PaymentCorrected` | `ALTERACAO` | `REQ-PAY-021` | Confirmado — o legado **audita** |
| `PayrollCycleCompleted` | `PROCESSAMENTO` | `REQ-PAY-023` | Confirmado |

`PaymentGenerated` carrega o mapa de fatores aplicados. É o que permite medir a divergência com a `RN-013` depois de uma folha real.

### Interfaces consumidas

| Interface | Contexto | Entregue em |
|---|---|---|
| `BeneficiaryQuery` | Cadastro | Fatia 2 |
| `SocialProgramQuery.parametersOf` | Catálogo | Fatia 3 |
| `DocumentValidator` | Kernel | Fatia 1 |

O contexto Pagamento é o primeiro a consumir os três. Nenhum deles precisou ser alterado para isto.

---

## Estratégia de testes

| Requisito | Verificação |
|---|---|
| `REQ-PAY-001` | Ciclo executado duas vezes gera um pagamento; a restrição do banco é exercitada |
| `REQ-PAY-002` | Mil pagamentos concorrentes, nenhum número repetido |
| `REQ-PAY-010` | Vetores de cálculo com resultado conferido contra a fórmula do legado |
| `REQ-PAY-012` | Dois beneficiários idênticos em ordens diferentes produzem o mesmo valor |
| `REQ-PAY-013` | Fator de ajuste aparece uma vez no resultado |
| `REQ-PAY-014` | 100,999 trunca para 100,99, e não arredonda para 101,00 |
| `REQ-PAY-018` | Cada um dos oito tipos declarados é tratado ou recusado |
| `REQ-PAY-019` | Judicial de 50% mais administrativo: judicial permanece íntegro |
| `REQ-PAY-020` | Tentativa de gravar líquido inconsistente é recusada pelo banco |
| `REQ-PAY-022` | Correção sem índice do período falha com motivo |

### O teste que vale por toda a fatia

```java
// REQ-PAY-012, nivel C. No BATCHPGT, uma renda acima da ultima faixa deixa
// #FACTOR-INCOME com o valor do beneficiario anterior: dois beneficiarios
// identicos recebem valores diferentes conforme a ordem de leitura.
@Test
void deve_produzir_o_mesmo_valor_independentemente_da_ordem_de_processamento() { ... }
```

É a propriedade mais básica de um cálculo, e o legado não a tem.

### Teste de carga

A `T-110` da Fatia 1 mediu 10 mil eventos por segundo na trilha. Falta medir o ciclo inteiro: leitura, cálculo, persistência e evento.

O alvo é 3,8 milhões de pagamentos em 4 horas — 264 por segundo. O teste usa 100 mil, como na Fatia 1, e extrapola.

### Limitação de caracterização

> [!WARNING]
> **Não há ambiente legado disponível.** Esta limitação está registrada desde a Fatia 1 e **pesa mais aqui do que em qualquer outro ponto do projeto**: os vetores de cálculo derivam da leitura do código, e é o cálculo que determina quanto cada pessoa recebe.
>
> Se o acesso for obtido, a reconfirmação dos vetores de `REQ-PAY-010` é pré-requisito para a primeira folha em produção, não item de melhoria.

---

## Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Vetores derivados de leitura, não de execução | O valor pago pode divergir do legado em caso de borda | Registrado acima; reconfirmação é pré-requisito de produção |
| A primeira folha gera metade dos pagamentos atuais | Parece falha e é correção | `REQ-PAY-001` e ADR-0008; explicado antes de acontecer |
| Correção do fator de ajuste altera valor | Programas com fator diferente de zero | `REQ-PRG-015` mede o alcance antes da carga |
| 180 milhões de registros na carga | Janela de migração | Partição por período e inserção em lote |
| Validação humana derruba a fórmula | Retrabalho no cálculo | Cada fator em componente próprio; troca localizada |
| Pagamentos duplicados no histórico migrado | Metade dos registros pode ser fantasma | Identificáveis pela ausência de número; decisão na Fatia 5 |

---

## Fora deste plano

| Item | Destino |
|---|---|
| Conciliação do retorno bancário | Fatia 5 |
| Destino dos pagamentos duplicados no histórico | Fatia 5 |
| Relatórios | Fatia 5 |
| Correção da correspondência região/fator | Decisão de negócio; ver ADR-0008 |
| Integração SIAFI | Fora do escopo de modernização |
| Interface Next.js | Fatia própria de frontend |

---

## Definição de pronto

- [x] Estrutura de pacote definida e alinhada ao mapa de contextos.
- [x] Modelo de dados definido, com restrições que carregam requisitos.
- [x] Contrato de comunicação especificado, com os quatro eventos confirmados.
- [x] Riscos identificados com mitigação.
- [x] Tarefas geradas em [`tasks.md`](tasks.md).

---

## Ajustes durante a implementação

O que a execução revelou e o planejamento não previa.

### 1. A folha precisa de uma porta de leitura própria no Cadastro

`BeneficiaryQuery` exige `Actor` e publica `BeneficiaryQueried` a cada consulta — foi assim que a Fatia 2 resolveu o `REQ-BEN-016`. Usar essa interface na folha produziria 3,8 milhões de eventos de acesso por ciclo, o mesmo problema de volume que a `PORT. CGTI 213/2010` usou para justificar não auditar consultas.

Criou-se `BeneficiaryPayrollFeed`, com paginação por CPF e sem evento por registro. A prestação de contas do lote vem do `PayrollCycleCompleted`, com `batchRunId`, e a de cada valor vem do `PaymentGenerated`. A paginação é por cursor de CPF, e não por `OFFSET`, porque `BATCHPGT.NSP:189` lê com `READ LOGICAL BY CPF` e retomar de um CPF conhecido sobrevive a inclusões concorrentes.

### 2. Publicar evento fora de transação é publicar no vazio

`PayrollCycleService` não é `@Transactional` — nenhuma transação sobrevive à janela de quatro horas. Consequência não prevista: `AuditEventListener` é `@TransactionalEventListener(BEFORE_COMMIT)`, e sem transação ativa o evento de conclusão do ciclo era descartado em silêncio.

Introduziu-se `PayrollEventRecorder`, que abre uma transação própria apenas para registrar. O sintoma era exatamente o que o `REQ-AUD-010` existe para eliminar: a folha termina e não há registro de que terminou.

### 3. A contribuição social precisou de coluna própria

A restrição `ck_payment_net_consistent` exige `amount_net = amount_gross - amount_discount`. Mas a contribuição social é calculada na geração (`CALCBENF.NSN:344-350`) e os demais descontos chegam depois (`CALCDSCT`). Somar tudo em `amount_discount` sem separar tornaria impossível responder qual das duas contribuições sociais do legado (`SIFAP-M-10`) incidiu sobre um pagamento.

Acrescentou-se `amount_social`. O total continua sendo a soma, e a restrição continua valendo.

### 4. O teto de desconto é uma decisão de agregado, não de laço

`CALCDSCT.NSP:170-175` corta `#AMT-TOTAL-DISC` dentro do laço. Como o acumulador soma todos os tipos, um desconto comum processado depois de um judicial reduz o judicial — que a linha `:132` declara isento. `DiscountCap` soma os isentos fora do teto e aplica o limite apenas ao restante: o resultado deixa de depender da ordem de processamento.

### 5. A aplicação não cria partição, mas recusa a folha sem ela

Criar partição é DDL, e a role da aplicação tem apenas `SELECT`, `INSERT` e `UPDATE` — a mesma decisão da Fatia 1. `PaymentPartitionGuard` verifica a existência antes do primeiro bloco e falha com motivo legível, em vez de estourar no meio da gravação com `no partition of relation found for row`.

### 6. Colunas de período são `VARCHAR(6)`, não `CHAR(6)`

O PostgreSQL reporta `CHAR` como `bpchar` e a validação de schema do Hibernate recusa. Mesma lição da Fatia 2, agora na direção oposta: lá o `CHAR(1)` foi adotado para casar com `length = 1`; aqui o `VARCHAR(6)` foi adotado para casar com o padrão de `String`. A ordenação lexicográfica do `RANGE` continua correta porque todo período tem seis dígitos.

### 7. O duplicado do histórico não é escolhido pela carga

`uq_payment_cpf_period` impede a segunda ocorrência. Decidir qual vale exige a conciliação bancária, que é da Fatia 5. `PaymentLoader` migra o primeiro, conta os demais em `duplicatedInPeriod` e devolve `noPaymentDiscarded() == false`. O registro fica visível em vez de silenciosamente escolhido.

### Verificação

| Medida | Resultado |
|---|---|
| Testes | 100 unitários + 68 de integração, todos verdes |
| Cobertura de linhas | 90,9% (portão: 60%) |
| Vazão medida | acima dos 264 pagamentos/s que a janela legada exige |
| Regras ArchUnit | 11, incluindo três novas para a fronteira da folha |
