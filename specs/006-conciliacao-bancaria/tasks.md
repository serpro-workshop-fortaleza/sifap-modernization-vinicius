# Tarefas — Conciliação Bancária

> **Especificação:** [`spec.md`](spec.md) · **Plano:** [`plan.md`](plan.md)

**Ordem de execução.** Cada tarefa entrega teste e implementação juntos, conforme a regra do repositório: testes durante a implementação, nunca depois.

| Campo | Valor |
|---|---|
| **Fatia** | 5 — Conciliação e Relatórios |
| **Módulo** | `backend/src/main/java/br/gov/sifap/payment/internal/reconciliation/` |
| **Pré-requisito** | Fatia 4 concluída: agregado `Payment`, remessa e trilha em uso |

---

## T-500 — Schema da conciliação

Migração `V6` com as colunas de conciliação em `payment` e as tabelas `reconciliation_file` e `reconciliation_issue`.

- **Requisitos:** `REQ-REC-003`, `REQ-REC-009`, `REQ-REC-016`
- **Testes:** situação de conciliação fora do domínio é recusada; resumo de arquivo duplicado é recusado
- **Verificação:** o índice parcial por período e situação de conciliação existe
- **Depende de:** Fatia 4

> As colunas não são invenção desta fatia. `PAYMENT.ddm:95`, `:97` e `:110` já as declaram; o que nunca existiu foi código que as gravasse.

---

## T-501 — Domínio dos códigos de retorno

`ReturnCode` com os três códigos conhecidos e `ReconciliationStatus` com o domínio fixo do dicionário.

- **Requisitos:** `REQ-REC-007`, `REQ-REC-008`
- **Testes:** os três códigos convertem; código fora do domínio devolve vazio em vez de valor padrão
- **Verificação:** `ReconciliationStatus` tem exatamente `CONCILIADO` e `DIVERGENTE`
- **Depende de:** T-500

> `PAYMENT.ddm:95` declara domínio fixo de dois valores. O tipo não acrescenta nada — apenas impede que outro apareça.

---

## T-502 — Leitura do arquivo de retorno

`ReturnFileParser` convertendo o layout posicional em `ReturnRecord` tipado, com as posições em configuração.

- **Requisitos:** `REQ-REC-004`, `REQ-REC-005`
- **Testes:** cabeçalho e rodapé são ignorados sem contar como pendência; valor em centavos converte com duas casas; valor não numérico vira pendência
- **Verificação:** nenhuma posição de campo aparece como literal no código de negócio
- **Depende de:** T-501

> Até 2017 o campo alfanumérico ia direto para o campo decimal, sem dividir por cem (`BATCHCON.NSP:166-168`, ticket 8112/2017). Conciliações anteriores compararam valores em escalas diferentes.

---

## T-503 — Identidade do arquivo processado

`ReconciliationFile` com resumo SHA-256 do conteúdo, e recusa de reprocessamento.

- **Requisitos:** `REQ-REC-003`
- **Testes:** o mesmo conteúdo submetido duas vezes é recusado na segunda, com referência à execução anterior; conteúdos diferentes com o mesmo nome são aceitos
- **Verificação:** a identidade não usa o nome do arquivo
- **Depende de:** T-502

> `BATCHCON.NSP:139-141` registra que o nome informado na tela é documentação: a ligação real é a `DD CMWKF01`. O nome nunca foi identificador.

---

## T-504 — Casamento do retorno com o pagamento

`PaymentMatcher` com busca por número e recuo para CPF e período.

- **Requisitos:** `REQ-REC-001`, `REQ-REC-002`, `REQ-REC-010`
- **Testes:** pagamento com número é localizado pelo número; pagamento migrado sem número é localizado pelo par; par ambíguo não atualiza nada e registra pendência; retorno sem correspondência vira pendência consultável
- **Verificação:** o segundo critério só é acionado quando o primeiro não se aplica
- **Depende de:** T-503

> O `SIFAP-M-08` deixa de ser apenas um mistério do cálculo e passa a ter consequência medível: os pagamentos que `CALCBENF.NSN:319` grava sem número nunca foram alcançados pela conciliação.

---

## T-505 — Máquina de estados da conciliação

Métodos de transição em `Payment`, validando a situação atual antes de aplicar a nova.

- **Requisitos:** `REQ-REC-007`, `REQ-REC-011`
- **Testes:** pagamento remetido aceita confirmação; pagamento já confirmado recusa segunda confirmação; pagamento cancelado recusa retorno; pagamento não remetido recusa e registra inconsistência
- **Verificação:** nenhuma transição é aplicável fora do agregado
- **Depende de:** T-504

> O legado grava a nova situação sem consultar a anterior. A regra no serviço seria burlável pelo próximo chamador; no agregado, é a única porta.

---

## T-506 — Divergência de valor como estado do pagamento

`markDivergent` gravando situação de conciliação e valor confirmado pelo banco, preservando a situação do pagamento.

- **Requisitos:** `REQ-REC-006`, `REQ-REC-009`
- **Testes:** diferença de até um centavo concilia; diferença maior marca divergente; a situação anterior do pagamento é preservada; os divergentes de um período são listáveis por consulta ao pagamento
- **Verificação:** a consulta de divergentes usa o índice parcial e não toca a trilha de auditoria
- **Depende de:** T-505

> Duas dimensões onde o legado tinha uma: `STAT-PAYMENT` diz o que aconteceu com o pagamento, `STAT-RECONCIL` diz o que aconteceu com a conferência.

---

## T-507 — Banco pagador vindo do arquivo

Gravação do código do banco a partir do próprio retorno, com recusa de código fora do domínio.

- **Requisitos:** `REQ-REC-016`
- **Testes:** o código do banco do arquivo é gravado; código desconhecido vira pendência
- **Verificação:** nenhum literal de banco no código
- **Depende de:** T-505

> `BATCHCON.NSP:212` faz `MOVE 1 TO PAYMENT-V.COD-BANK` sobre um campo `A3` declarado como código FEBRABAN. O banco que creditou nunca foi preservado.

---

## T-508 — Contadores do ciclo

`ReconciliationTally` recebendo o resultado da classificação, nunca a intenção dela.

- **Requisitos:** `REQ-REC-008`, `REQ-REC-014`
- **Testes:** código desconhecido conta como pendente e não como conciliado; conciliação dentro da tolerância é contada em separado; os totais fecham com a quantidade lida
- **Verificação:** `reconciled()` só é chamado do ramo que aplicou transição
- **Depende de:** T-506, T-507

> [!WARNING]
> É o defeito de ordem de instruções do `SIFAP-F5-01`. `ADD 1 TO #QTY-RECONCILED` precede o `DECIDE` que classifica o código, e o ramo `NONE` apenas escreve no log. O resumo declara sucesso sobre registros que ninguém tratou.

---

## T-509 — Ciclo de conciliação

`ReconciliationService` orquestrando leitura, casamento, transição e contagem, com transação por bloco.

- **Requisitos:** `REQ-REC-001` a `REQ-REC-014`
- **Testes:** ciclo completo sobre arquivo de exemplo; ciclo com pendências não falha; o resultado indica a existência de pendências
- **Verificação:** o serviço não conhece o parser posicional nem a trilha de auditoria
- **Depende de:** T-508

---

## T-510 — Retomada de ciclo interrompido

Verificação de conciliação já aplicada antes de processar cada registro.

- **Requisitos:** `REQ-REC-015`
- **Testes:** interrupção no meio do arquivo, retomada não reaplica o que já foi conciliado; o total após a retomada corresponde ao arquivo inteiro
- **Verificação:** a retomada não depende de estado em memória
- **Depende de:** T-509

> `BACKOUT TRANSACTION` desfaz apenas a última transação. Tudo antes dela permanece, e nada registra onde parou.

---

## T-511 — Eventos de conciliação

`PaymentReconciled`, `PaymentDivergenceDetected`, `ReconciliationIssueRegistered` e `ReconciliationCycleCompleted`.

- **Requisitos:** `REQ-REC-012`, `REQ-REC-013`, `REQ-REC-014`
- **Testes:** cada conciliação gera evento; a divergência carrega valor do SIFAP e do banco; o evento de ciclo não carrega CPF; conciliação usa ação de conciliação e consulta usa ação de consulta
- **Verificação:** o encerramento do ciclo é publicado dentro de transação própria
- **Depende de:** T-509

> A lição da Fatia 4: ciclo não transacional publicando em listener `BEFORE_COMMIT` publica no vazio. `PayrollEventRecorder` já resolve isso e é reaproveitado.

---

## T-512 — Carga da conciliação histórica

Leitura do histórico de conciliações com inventário dos registros sem situação de conciliação.

- **Requisitos:** `REQ-REC-009`
- **Testes:** nenhum valor é recalculado; pagamentos sem situação de conciliação são contados; o relatório distingue conciliado, divergente e nunca conciliado
- **Verificação:** a carga não altera `STAT-PAYMENT`
- **Depende de:** T-506

> Os campos nunca foram gravados. Todo o histórico chega sem situação de conciliação, e a carga mede quantos pagamentos jamais passaram por conferência.

---

## T-513 — Redução do conjunto ambíguo de duplicados

Cruzamento dos duplicados do histórico com os créditos bancários conciliados.

- **Requisitos:** `REQ-REC-002`
- **Testes:** duplicado com um único crédito correspondente tem vencedor determinável; duplicado sem crédito permanece ambíguo; nenhum registro é excluído
- **Verificação:** a decisão sobre os ambíguos remanescentes não é tomada em código
- **Depende de:** T-512

> A Fatia 4 identificou os duplicados e não escolheu. Esta tarefa não escolhe tampouco: ela reduz o conjunto ao que de fato exige decisão humana.

---

## T-514 — Divergências deliberadas do legado

Testes que falham contra o SIFAP original, reunidos em uma classe.

- **Requisitos:** as nove correções desta especificação
- **Testes:** um por correção, cada um citando `arquivo:linha` da origem
- **Verificação:** nenhuma correção altera valor calculado de benefício
- **Depende de:** T-511

---

## T-515 — Fronteira do módulo

Regras ArchUnit para o subpacote de conciliação.

- **Requisitos:** decisão de fronteira do [`plan.md`](plan.md)
- **Testes:** conciliação não é alcançável de fora de `payment`; não depende de `audit`; não depende de `report`
- **Verificação:** a suíte de arquitetura continua verde
- **Depende de:** T-514

---

## Resumo

| Tarefa | Requisitos | Depende de |
|---|---|---|
| T-500 | `REQ-REC-003`, `009`, `016` | Fatia 4 |
| T-501 | `REQ-REC-007`, `008` | T-500 |
| T-502 | `REQ-REC-004`, `005` | T-501 |
| T-503 | `REQ-REC-003` | T-502 |
| T-504 | `REQ-REC-001`, `002`, `010` | T-503 |
| T-505 | `REQ-REC-007`, `011` | T-504 |
| T-506 | `REQ-REC-006`, `009` | T-505 |
| T-507 | `REQ-REC-016` | T-505 |
| T-508 | `REQ-REC-008`, `014` | T-506, T-507 |
| T-509 | `REQ-REC-001` a `014` | T-508 |
| T-510 | `REQ-REC-015` | T-509 |
| T-511 | `REQ-REC-012`, `013`, `014` | T-509 |
| T-512 | `REQ-REC-009` | T-506 |
| T-513 | `REQ-REC-002` | T-512 |
| T-514 | correções | T-511 |
| T-515 | fronteira | T-514 |
