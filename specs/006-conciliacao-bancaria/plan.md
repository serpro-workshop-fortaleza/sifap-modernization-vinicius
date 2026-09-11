# Plano técnico — Conciliação Bancária

> **Especificação:** [`spec.md`](spec.md) · **Tarefas:** [`tasks.md`](tasks.md)

| Campo | Valor |
|---|---|
| **Fatia de migração** | 5 — Conciliação e Relatórios |
| **Contexto** | `payment`, parte 3 de 3 |
| **Requisitos cobertos** | `REQ-REC-001` a `REQ-REC-016` |
| **Volume de referência** | até 3,8 milhões de registros de retorno por ciclo mensal |

---

## Decisões de projeto

### A conciliação vive dentro do contexto `payment`

A primeira decisão é de fronteira, e contraria a leitura mais óbvia da fatia.

Conciliação tem vocabulário próprio — arquivo de retorno, CNAB, banco pagador, código de devolução — e consome uma fonte externa. Isso sugere contexto separado. Mas ela **escreve no agregado `Payment`**, e dois contextos escrevendo o mesmo agregado é exatamente o antipadrão que a regra de escritor único já rejeitou na Fatia 3.

```text
payment/internal/reconciliation/
├── ReconciliationService.java      # orquestra o ciclo
├── ReturnFileParser.java           # CNAB 240 -> registros
├── ReturnRecord.java               # registro de retorno já tipado
├── PaymentMatcher.java             # REQ-REC-001, REQ-REC-002
├── ReturnCode.java                 # domínio dos códigos
├── ReconciliationFile.java         # REQ-REC-003
└── ReconciliationTally.java        # contadores do ciclo
```

A alternativa — contexto `reconciliation` chamando uma porta de comando do `payment` — criaria um contexto cuja única razão de existir seria delegar ao outro.

### A transição de situação vive no agregado

O `REQ-REC-011` é uma máquina de estados, e ela pertence a `Payment`, ao lado de `markIssued()` da Fatia 4:

```java
public void confirmCredit(LocalDate creditDate, String bankCode, ReturnCode code, Clock clock);
public void markReturned(ReturnCode code);
public void markReversed(ReturnCode code);
public void markDivergent(BigDecimal bankAmount, ReturnCode code);
```

Cada método valida a situação atual antes de aplicar a nova. O legado grava sem consultar:

```text
FIND PAYMENT-V WITH NUM-PAYMENT = #NUM-PAYMENT
  MOVE 'P' TO PAYMENT-V.STAT-PAYMENT
  UPDATE PAYMENT-V
```

Fonte: [`BATCHCON.NSP:205-213`](../../01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP).

Colocar a regra no serviço a deixaria burlável por qualquer chamador futuro. No agregado, ela é a única porta.

### A divergência é estado, e o estado já está declarado

O dicionário resolve a modelagem do `REQ-REC-009` sem deixar escolha a fazer:

| Campo | Declaração | Uso |
|---|---|---|
| `STAT-RECONCIL` | `A 1 F  C=RECONCILED D=DIVERGENT` (`PAYMENT.ddm:95`) | `ReconciliationStatus` |
| `AMT-RECONCILED` | `P 9,2  BANK-CONFIRMED AMOUNT` (`PAYMENT.ddm:97`) | valor informado pelo banco |

Domínio fixo, dois valores, exatamente os necessários. A tarefa não é decidir como representar a divergência — é alimentar o campo que existe desde sempre e nunca foi gravado.

`STAT-RECONCIL` é ortogonal a `STAT-PAYMENT`: um pagamento divergente **preserva** a situação anterior (`AC-009.3`). São duas dimensões, e o legado só tinha uma.

### O arquivo é identificado pelo conteúdo, não pelo nome

`REQ-REC-003`. `PAYMENT.ddm:110` declara `HASH-RETURN-FILE A 64 N SHA-256 RETURN FILE`, adicionado em 17/11/2015 e nunca gravado.

O nome do arquivo não serve como identidade. O próprio `BATCHCON.NSP:139-141` registra por quê: *"IN Z/OS THE BINDING USES DD CMWKF01 - THE NAME PROVIDED ABOVE IS FOR DOCUMENTATION ONLY"*. O nome digitado na tela não tem relação com o arquivo lido.

```java
record ReconciliationFile(String sha256, String declaredName, Instant processedAt, String processedBy) { }
```

O resumo criptográfico também é o que torna o `REQ-REC-015` possível: retomar uma execução interrompida exige saber que é o mesmo arquivo.

### O casamento tem dois critérios, em ordem, e recusa ambiguidade

```java
Optional<Payment> match(ReturnRecord record) {
    return record.paymentNumber()
            .flatMap(payments::findById)
            .or(() -> matchByCpfAndPeriod(record));
}
```

O segundo critério existe só para o histórico migrado, cujos pagamentos sem número o `PaymentMigrationReport.withoutNumber()` já conta. Para pagamentos novos ele nunca dispara, porque o `REQ-PAY-002` garante número a todos.

Quando o par CPF e período devolve mais de um registro — possível apenas no histórico, pelo `duplicatedInPeriod` da Fatia 4 —, nada é atualizado e a ambiguidade vira pendência (`AC-002.2`).

### A pendência é registro, não linha de log

`REQ-REC-008`, `REQ-REC-010` e `REQ-REC-016` produzem o mesmo tipo de resultado: um registro de retorno que o sistema não soube tratar. Uma tabela própria:

```sql
CREATE TABLE reconciliation_issue (
    id, file_sha256, issue_type, cpf, reference_period,
    declared_amount, return_code, detected_at
);
```

`issue_type` cobre `SEM_CORRESPONDENCIA`, `CODIGO_DESCONHECIDO`, `AMBIGUIDADE`, `VALOR_INVALIDO`, `BANCO_DESCONHECIDO` e `TRANSICAO_INVALIDA`. É o mesmo padrão de `beneficiary_migration_issue` da Fatia 2 e `payment` da Fatia 4: o que não se sabe tratar fica contável em vez de virar linha no `CMPRINT` de uma execução manual.

### O contador só incrementa depois da decisão

`REQ-REC-008`. O defeito do legado é de ordem de instruções, não de lógica:

```text
ADD 1 TO #QTY-RECONCILED      <- antes
DECIDE ON FIRST VALUE OF #COD-RETURN
  ...
  NONE
    WRITE #MSG                 <- não atualiza nada
END-DECIDE
```

`ReconciliationTally` recebe o resultado da classificação, nunca a intenção dela. O método `reconciled()` só é chamado do ramo que efetivamente aplicou a transição.

---

## Modelo de dados

### Alterações em `payment`

```sql
ALTER TABLE payment
    ADD COLUMN reconciliation_status  VARCHAR(12),
    ADD COLUMN amount_reconciled      NUMERIC(11,2),
    ADD COLUMN bank_code              VARCHAR(3),
    ADD COLUMN bank_return_code       VARCHAR(2),
    ADD COLUMN credit_date            DATE,
    ADD COLUMN reconciled_at          TIMESTAMPTZ;

CREATE INDEX idx_payment_reconciliation
    ON payment (reference_period, reconciliation_status)
    WHERE reconciliation_status IS NOT NULL;
```

O índice parcial atende o `AC-009.2`: listar os divergentes de um período sem varrer a trilha de auditoria.

### Tabelas novas

| Tabela | Finalidade | Requisito |
|---|---|---|
| `reconciliation_file` | arquivo processado, por resumo SHA-256 | `REQ-REC-003`, `REQ-REC-015` |
| `reconciliation_issue` | pendências consultáveis | `REQ-REC-008`, `REQ-REC-010`, `REQ-REC-016` |

---

## Contrato de comunicação

Quatro eventos, todos implementando `AuditableEvent`:

| Evento | Ação | Origem legada |
|---|---|---|
| `PaymentReconciled` | `CONCILIACAO` | `BATCHCON.NSP:316-329` |
| `PaymentDivergenceDetected` | `CONCILIACAO` | `BATCHCON.NSP:331-345` |
| `ReconciliationCycleCompleted` | `PROCESSAMENTO` | `BATCHCON.NSP:275-285` |
| `ReconciliationIssueRegistered` | `PROCESSAMENTO` | sem equivalente |

`PaymentDivergenceDetected` carrega valor do SIFAP e valor do banco em `changes()`. É a generalização do único ponto do acervo que preenche `AMT-PREV` e `AMT-NEW`.

O `AuditAction.CONCILIACAO` já existe desde a Fatia 1 e resolve o `REQ-REC-013` sem trabalho novo: consulta usa `CONSULTA`, conciliação usa `CONCILIACAO`. A colisão do `CO` legado não se reproduz porque os dois nunca compartilharam um enum.

---

## Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Pendências em volume muito maior que o atual | Operação sem capacidade de tratá-las | O ciclo não falha por pendência; elas se acumulam e são consultáveis |
| Layout CNAB 240 com variações por banco | Parser quebra em produção | Posições do layout em configuração, não em constante |
| Retomada aplicando efeito duas vezes | Conciliação duplicada | Verificação por resumo do arquivo + situação atual do pagamento |
| Histórico migrado com par CPF e período ambíguo | Conciliação impossível para esses registros | Recusa explícita; o conjunto ambíguo é reduzido, não resolvido |

---

## Fora deste plano

| Item | Destino |
|---|---|
| Integração SIAFI (`STAT-INTEG-SIAFI`) | Fora do escopo de modernização |
| Hash do arquivo de remessa (`HASH-REMITTANCE-FILE`) | Complemento do `REQ-PAY-024`; fatia própria se necessário |
| Decisão final sobre duplicados do histórico | Negócio; esta fatia entrega a evidência bancária |
| Relatório de conciliação | [Especificação 007](../007-relatorios/spec.md) |

---

## Definição de pronto

- [x] Fronteira decidida e justificada contra a alternativa.
- [x] Modelo de dados definido, ancorado nos campos que o dicionário já declara.
- [x] Contrato de comunicação especificado.
- [x] Riscos identificados com mitigação.
- [x] Tarefas geradas em [`tasks.md`](tasks.md).

---

## Ajustes durante a implementação

O que a execução revelou e o planejamento não previa.

### 1. Transação única no ciclo torna o `REQ-REC-015` inatendível

O plano dizia que o arquivo de retorno cabe em uma transação, porque a operação por registro é uma atualização pontual. Escrever o teste de retomada mostrou o erro: com `@Transactional` no ciclo inteiro, uma falha desfaz **também o registro do arquivo**, e a retomada não tem de onde partir.

Duas peças resolveram. `ReconciliationFileRegistry` abre, conclui e interrompe o arquivo em `REQUIRES_NEW`, de modo que o registro sobrevive ao que acontecer com o processamento. `ReconciliationBlockProcessor` aplica os registros em blocos transacionais, como `PayrollBlockProcessor` faz na folha.

O legado tem o problema inverso e igualmente ruim: `BATCHCON` confirma cada registro individualmente e, quando falha, `BACKOUT TRANSACTION` desfaz só o último — tudo antes permanece, e nada registra onde parou.

### 2. O identificador de execução não cabe no que a trilha aceita

`batch_run_id` e `entity_id` da trilha são `VARCHAR(50)`, definidos na Fatia 1. O SHA-256 em hexadecimal tem 64 caracteres.

Ampliar a coluna significaria `ALTER TABLE` em tabela particionada declarada imutável. A alternativa adotada é um identificador curto, `REC-{id}`, com o resumo no payload do evento de encerramento. O `runId` também passou a fazer parte do `ReconciliationResult`, porque sem ele o chamador não consegue localizar a execução na trilha.

### 3. `CHAR(64)` volta a ser `bpchar`

Terceira ocorrência da mesma lição, agora na coluna do resumo. O PostgreSQL reporta `CHAR(n)` como `bpchar` e a validação de schema do Hibernate recusa contra `String`. Toda coluna de texto de largura fixa neste projeto é `VARCHAR`.

### 4. A carga do histórico é apuração, não migração

O plano tratava a conciliação histórica como carga. Não é: os campos nunca foram gravados, então não há dado de origem a transformar. `ReconciliationHistoryLoader` faz um inventário — quantos pagamentos jamais passaram por conferência registrada, quantos duplicados têm vencedor determinável pelo crédito bancário e quantos permanecem ambíguos.

Usa consulta direta em vez de carregar agregados, pela mesma razão que os relatórios agregam no banco: contar milhões de linhas em memória troca um problema de consulta por um de heap.

### 5. O crédito sem situação de conciliação é um estado que só a migração revela

Apareceu ao escrever a consulta de inventário. `BATCHCON.NSP:209` grava `DT-CREDIT` e nunca toca `STAT-RECONCIL`, porque o campo não é gravado por programa nenhum. O resultado é um pagamento com data de crédito e sem marca de conferência — visível apenas agora, e contado em `statusOutOfDomain`.

### Verificação

| Medida | Resultado |
|---|---|
| Testes | 115, todos verdes |
| Cobertura de linhas | 90,0% (portão: 60%) |
| Regras ArchUnit | 12, incluindo a fronteira da conciliação |
| Requisitos cobertos | `REQ-REC-001` a `REQ-REC-016` |
