# Plano técnico — Trilha de Auditoria

> **Especificação:** [`spec.md`](spec.md) · **Tarefas:** [`tasks.md`](tasks.md)

| Campo | Valor |
|---|---|
| **Fatia de migração** | 1 — Fundações transversais |
| **Contexto** | `audit` |
| **Requisitos cobertos** | `REQ-AUD-001` a `REQ-AUD-013` |
| **Volume de referência** | 417.884.120 registros e 311 GB no legado |

---

## Decisões de projeto

### Separar eventos de alteração de eventos de acesso

O legado usa uma tabela única para tudo. A composição do volume mostra por que isso é um problema:

| Categoria | Registros | Proporção |
|---|---:|---:|
| Consulta e conciliação | 371.800.000 | 89% |
| Login e logout | 25.000.000 | 6% |
| Cadastro | 21.000.000 | 5% |
| Demais ações de negócio | 84.120 | 0,02% |

Fonte: `AUDIT.ddm:145-158`.

Os eventos de negócio que um auditor procura são **0,02% do volume**. Mantê-los na mesma tabela que os acessos significa varrer 311 GB para encontrar 84 mil registros.

**Decisão:** duas tabelas.

| Tabela | Conteúdo | Retenção | Imutável |
|---|---|---|---|
| `audit_change_event` | Inclusão, alteração, exclusão | 10 anos, `Lei 8159` | Sim |
| `audit_access_event` | Consulta a dado pessoal | Configurável | Sim |

Ciclos de vida diferentes justificam armazenamentos diferentes. Também resolve o conflito normativo do `REQ-AUD-007`: registra o acesso, atendendo à `IN-TCU 63/2010`, e limita o crescimento, atendendo à preocupação da `PORT. CGTI 213/2010`.

### Garantir imutabilidade por permissão, não por convenção

O dicionário declara `UPDATE/DELETE NOT ALLOWED` e nada no legado impede a operação — é convenção.

**Decisão:** revogar `UPDATE` e `DELETE` da role da aplicação nas duas tabelas. O expurgo por retenção usa role própria, com permissão restrita a `DELETE` sobre partições fora do prazo.

Convenção que depende de disciplina foi o que produziu as cinco rotinas de CPF divergentes. Invariante desta importância pertence ao banco.

### Registrar na mesma transação do dado

O legado grava auditoria na mesma transação do dado alterado (`CCAUDIT.NSC:53-59`, notas 3 e 4). O `BACKOUT` desfaz os dois juntos.

**Decisão:** preservar. `@TransactionalEventListener(phase = BEFORE_COMMIT)`.

Isso garante que não existe dado alterado sem evento correspondente. A alternativa assíncrona traria desempenho e abriria a janela em que o dado existe e a auditoria não — exatamente o defeito que o `REQ-AUD-001` corrige.

### Numeração por sequência do banco

O legado lê o maior número e incrementa em memória (`CCAUDIT.NSC:64-71`), sobre uma chave declarada única.

**Decisão:** `BIGSERIAL`. Elimina a corrida por construção, sem lock de aplicação.

### Valores anterior e posterior em JSONB

O dicionário usa dois grupos MU de até 20 ocorrências para nome de campo e valor (`AUDIT.ddm:61-69`). O limite de 20 é restrição do Adabas, não regra de negócio.

**Decisão:** coluna `JSONB` com o conjunto de mudanças.

```json
{ "amtFamilyIncome": { "before": "1200.00", "after": "1450.00" } }
```

Remove o limite artificial e permite consulta por campo alterado, que o legado não suporta.

### Particionamento por período

**Decisão:** partição declarativa por range mensal em `occurred_at`, nas duas tabelas.

O expurgo de retenção passa a ser `DROP PARTITION`, não `DELETE` sobre centenas de milhões de linhas.

---

## Modelo de dados

```sql
CREATE TABLE audit_change_event (
    id              BIGSERIAL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    action          VARCHAR(20)  NOT NULL,
    entity_type     VARCHAR(20)  NOT NULL,
    entity_id       VARCHAR(50)  NOT NULL,
    subject_cpf     VARCHAR(11),
    actor_id        VARCHAR(50)  NOT NULL,
    actor_profile   VARCHAR(20)  NOT NULL,
    changes         JSONB,
    batch_run_id    VARCHAR(50),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE INDEX idx_change_entity ON audit_change_event (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_change_subject ON audit_change_event (subject_cpf, occurred_at DESC);
```

O índice por entidade e data é o equivalente ao superdescritor `S2` que o dicionário manda usar em consultas pesadas (`REQ-AUD-012`).

`audit_access_event` tem estrutura análoga, sem `changes` e sem `batch_run_id`.

### Códigos de ação

O `REQ-AUD-008` exige códigos não ambíguos. O legado colapsa três significados em `CO`.

| Novo | Legado | Observação |
|---|---|---|
| `INCLUSAO` | `IN` | — |
| `ALTERACAO` | `AL` | — |
| `EXCLUSAO` | `EX` | — |
| `CONSULTA` | `CO` a partir de 2016 | Desambiguado por período |
| `CONCILIACAO` | `CO` entre 2014 e 2018 | Desambiguado por período |
| `PROCESSAMENTO` | `BT` | — |

A migração dos históricos usa o período de origem para desambiguar, e registra a regra aplicada. Está na Fatia 5.

---

## Contrato do módulo

O contexto **não** expõe API de escrita. Registrar auditoria não é operação que um módulo solicita — é consequência de alterar dado.

O conjunto de eventos publicados por cada contexto está em [`domain-events.md`](../../02-modern-spec/domain-events.md), com o mapeamento para ação de auditoria e o roteamento entre as duas tabelas.

```java
// publicado pelo contexto de origem
public record BeneficiaryUpdated(String cpf, Map<String, Change> changes, Actor actor) { }

// consumido pelo contexto de auditoria
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
void on(BeneficiaryUpdated event) { ... }
```

Leitura é exposta por `AuditQuery`, usada pelo relatório.

---

## Estratégia de testes

| Requisito | Verificação |
|---|---|
| `REQ-AUD-002` | Teste de integração confirma que `UPDATE` e `DELETE` falham por permissão |
| `REQ-AUD-003` | Teste concorrente com múltiplas threads registrando eventos simultâneos |
| `REQ-AUD-004` | Dois eventos no mesmo segundo têm ordem determinística |
| `REQ-AUD-001` | Teste de rollback: transação desfeita não deixa evento órfão |
| `REQ-AUD-012` | `EXPLAIN` confirma uso de índice, não varredura |

Testcontainers com PostgreSQL 16. Particionamento e permissões não são simuláveis em banco em memória.

---

## Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Listener na transação atrasa a folha mensal | 3,8 milhões de eventos por ciclo dentro da janela de 4 horas | Inserção em lote para eventos de processamento; medir antes da Fatia 4 |
| Volume de acesso cresce sem limite | Repete o problema que motivou a portaria de 2010 | Retenção configurável e partição própria desde o início |
| Desambiguação de `CO` na migração pode errar | Evento histórico classificado incorretamente | Regra por período registrada; casos ambíguos migram com marcação |
| Escopo real é 4 arquivos, não 1 | Três arquivos históricos sem DDM | Fora desta fatia; decisão de escopo pendente em `SIFAP-M-...` |

---

## Fora deste plano

| Item | Destino |
|---|---|
| Migração dos FNR 154, 155 e 156 | Fatia 5 |
| Relatório de auditoria | Fatia 5, junto dos demais relatórios |
| Eventos de autenticação | Fora do escopo de modernização |

> O `REQ-AUD-011`, que corrige a omissão de exclusões, é especificado aqui e implementado na Fatia 5. A separação é deliberada: a regra pertence ao contexto de auditoria; a tela pertence ao módulo de relatórios.

---

## Definição de pronto

- [x] Modelo de dados definido, com particionamento e índices justificados.
- [x] Invariante de imutabilidade com mecanismo de garantia definido.
- [x] Contrato de comunicação entre contextos especificado.
- [x] Riscos identificados com mitigação.
- [ ] Tarefas geradas em [`tasks.md`](tasks.md).
