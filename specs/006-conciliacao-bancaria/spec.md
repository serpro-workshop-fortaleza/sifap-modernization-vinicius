# Especificação — Conciliação Bancária

> **Fatia de migração:** 5 — Conciliação e Relatórios
> **Destino arquitetural:** contexto delimitado (`payment`), parte 3 de 3
> **Dados sob responsabilidade:** `PAYMENT` (FNR 152) — situação e conciliação

| Campo | Valor |
|---|---|
| **Estágio** | Estágio 2 — Especificação |
| **Data** | 2026-09-11 |
| **ADRs aplicáveis** | [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md), [ADR-0008](../../docs/adr/0008-calculo-do-beneficio.md), [ADR-0009](../../docs/adr/0009-conciliacao-bancaria.md) |
| **Entrada** | [`business-rules-catalog.md`](../../01-archaeology/business-rules-catalog.md), regras 131-137, 158-161 e 166 |
| **Legado de origem** | `BATCHCON` |

---

## Contexto

A conciliação é o único ponto do sistema em que uma informação **externa** altera o estado de um pagamento. Tudo que as fatias anteriores fizeram — calcular, descontar, corrigir, remeter — termina aqui, quando o banco responde se o crédito aconteceu.

A leitura do `BATCHCON` encontrou quatro coisas que organizam esta especificação.

### A conciliação procura por um campo que metade dos pagamentos não tem

O casamento entre o retorno do banco e o pagamento é feito por número:

```text
COMPUTE #NUM-PAYMENT = VAL(#CNAB-NUM-DOC)
FIND PAYMENT-V WITH NUM-PAYMENT = #NUM-PAYMENT
```

Fonte: [`BATCHCON.NSP:160`](../../01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP) e `:176`.

A Fatia 4 mostrou que `CALCBENF.NSN:319` grava pagamentos **sem** `NUM-PAYMENT`. Esses pagamentos não podem ser encontrados por esta busca. Não é que a conciliação falhe neles — ela nunca os alcança. Eles permanecem na situação `G` indefinidamente, que é exatamente o sintoma que o `SIFAP-M-08` registra como impacto.

O `SIFAP-M-05`, o `SIFAP-M-08` e a conciliação são o mesmo problema em três programas.

### Um código de retorno desconhecido é contado como conciliado

A ordem das instruções decide o resultado:

```text
ADD 1 TO #QTY-RECONCILED
DECIDE ON FIRST VALUE OF #COD-RETURN
  VALUE '00' ... VALUE '01' ... VALUE '02' ...
  NONE
    COMPRESS 'UNKNOWN RETURN CODE:' #COD-RETURN ... INTO #MSG
    WRITE #MSG (AL=78)
END-DECIDE
```

Fonte: [`BATCHCON.NSP:203-243`](../../01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP).

O contador é incrementado **antes** do `DECIDE`. Um código que o programa não conhece escreve uma linha no log, não atualiza o pagamento e mesmo assim entra no total de conciliados. O resumo final declara sucesso sobre registros que ninguém tratou.

Pior: o código de retorno entra no `IF` de conciliados apenas porque a **diferença de valor** era aceitável. Valor certo e código ininteligível produzem "conciliado".

### Divergência de valor não altera o pagamento

Quando o valor do banco difere do valor do SIFAP em mais de um centavo, o programa registra auditoria e segue:

```text
IF #DIFF > 0.01
  ADD 1 TO #QTY-DIVERGENT
  WRITE #MSG (AL=78)
  PERFORM WRITE-AUDIT-DIVERG
ELSE
  ...atualiza situação...
END-IF
```

Fonte: [`BATCHCON.NSP:191-201`](../../01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP).

O registro de divergência é o **único** lugar de todo o acervo que preenche `AMT-PREV` e `AMT-NEW` — os campos de valor anterior e posterior que o dicionário declara desde 1997 e que a Fatia 1 encontrou vazios em toda parte. Está em `BATCHCON.NSP:332-336`.

Mas o pagamento divergente fica na situação anterior para sempre. Não há fila, não há marca no registro, não há como listar os divergentes sem varrer a trilha de auditoria. A única evidência de que algo deu errado é uma linha no `CMPRINT` de uma execução manual.

### O processo não é automatizado e não se protege de repetição

O cabeçalho declara: *"JOB: NOT AUTOMATED - MANUAL EXECUTION THROUGH NATURAL - TICKET 8110/2017 - DEDICATED JCL REQUESTED"*. Fonte: [`BATCHCON.NSP:14-15`](../../01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP).

Nada registra qual arquivo foi processado. Rodar o mesmo retorno duas vezes reaplica todas as atualizações e grava a auditoria de novo. Como o `UPDATE` é idempotente por acaso — a situação final é a mesma —, o efeito passa despercebido, exceto pela trilha, que dobra.

Há ainda um bug de tipo em `BATCHCON.NSP:212`: `MOVE 1 TO PAYMENT-V.COD-BANK`, onde `COD-BANK` é `A3`. O campo que deveria identificar o banco pagador recebe um literal numérico.

### O dicionário já resolveu isto em 2015, e ninguém implementou

O `PAYMENT.ddm` declara três campos que nenhum programa do acervo grava:

| Campo | Declaração | Para que serve |
|---|---|---|
| `STAT-RECONCIL` | `A 1 F  C=RECONCILED D=DIVERGENT` (`:95`) | Situação de conciliação, com domínio fixo |
| `AMT-RECONCILED` | `P 9,2 N  BANK-CONFIRMED AMOUNT` (`:97`) | Valor que o banco confirmou |
| `HASH-RETURN-FILE` | `A 64 N  SHA-256 RETURN FILE` (`:110`) | Identidade do arquivo de retorno processado |

A regra 161 do catálogo registra a ausência. O cabeçalho do dicionário data os dois campos de hash: *"17/11/2015 - CARLOS E. MENDES - FILE HASH FIELD"*.

Os três problemas anteriores — divergência sem rastro, reprocessamento sem proteção, valor do banco perdido — têm solução declarada no dicionário há mais de dez anos. É o mesmo padrão que a Fatia 1 encontrou nos campos de valor anterior e posterior da auditoria e a Fatia 3 nas faixas de cálculo: a estrutura existe, o código não a alimenta.

Esta especificação não inventa campo novo para esses três casos. Ela implementa o que o dicionário já previa.

---

## Requisitos

### REQ-REC-001 — Identificar o pagamento correspondente ao retorno

O sistema DEVE localizar o pagamento correspondente a cada registro de retorno pelo número do pagamento.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L176
- nível: `P` — preservado; a busca por número é o critério atual e continua válido
- AC-001.1: Dado um registro de retorno com número de pagamento existente, Quando conciliado, Então o pagamento correspondente é localizado.
- AC-001.2: Dado um registro de retorno cujo número não exista, Quando conciliado, Então o sistema registra pendência e não altera nenhum pagamento.

### REQ-REC-002 — Conciliar pagamento migrado sem número pelo par CPF e período

ONDE o registro de retorno não trouxer número de pagamento utilizável, o sistema DEVE localizar o pagamento pelo CPF e pelo período de referência.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN#L308-L319
- nível: `C` — corrigido; hoje o pagamento sem número é inalcançável pela conciliação
- AC-002.1: Dado um pagamento migrado sem número, Quando o retorno chegar com CPF e período, Então o pagamento é localizado.
- AC-002.2: Dado que o par CPF e período identifique mais de um pagamento, Quando conciliado, Então nenhum é atualizado e a ambiguidade é registrada.

> O `REQ-PAY-001` tornou o par CPF e período único para pagamentos novos. Para o histórico migrado, a unicidade não é garantida — é justamente o que o `PaymentMigrationReport` conta em `duplicatedInPeriod`.

### REQ-REC-003 — Recusar o reprocessamento do mesmo arquivo de retorno

SE um arquivo de retorno já tiver sido processado, ENTÃO o sistema DEVE recusar o processamento.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm#L110
- nível: `C` — corrigido; o campo de hash existe desde 2015 e nenhum programa o grava
- AC-003.1: Dado um arquivo já processado, Quando submetido de novo, Então o sistema recusa e informa a execução anterior.
- AC-003.2: Dado um arquivo novo, Quando processado, Então sua identificação fica registrada com a data e o responsável.

> A identidade é o resumo criptográfico do conteúdo, e não o nome do arquivo. `BATCHCON.NSP:139-141` registra que o nome informado na tela é apenas documentação: a ligação real é a `DD CMWKF01` do JCL, o que torna o nome inútil como identificador.

### REQ-REC-004 — Processar somente registros de detalhe do arquivo

O sistema DEVE processar apenas os registros de detalhe do arquivo de retorno.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L146-L149
- nível: `P` — preservado; o filtro por tipo `3` do CNAB 240 permanece
- AC-004.1: Dado um registro de cabeçalho ou rodapé, Quando lido, Então é ignorado sem contar como não conciliado.

### REQ-REC-005 — Converter o valor do retorno de centavos para reais

O sistema DEVE converter o valor do registro de retorno dividindo por cem.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L166-L170
- nível: `P` — preservado
- AC-005.1: Dado um valor de retorno em centavos, Quando convertido, Então o resultado tem duas casas decimais.
- AC-005.2: Dado um valor não numérico, Quando convertido, Então o registro vira pendência e não é tratado como zero.

> O comentário de `:166-168` registra que **até 2017 o campo alfanumérico ia direto para o campo decimal**, sem divisão. O ticket 8112/2017 corrigiu. Conciliações anteriores a essa data foram feitas comparando valores em escalas diferentes.

### REQ-REC-006 — Aceitar diferença de valor dentro da tolerância

ONDE a diferença entre o valor do SIFAP e o valor do retorno não superar um centavo, o sistema DEVE tratar o pagamento como conciliado.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L191
- nível: `PS` — preservado com sinalização; a tolerância permanece e cada uso fica contável
- AC-006.1: Dada uma diferença de até um centavo, Quando conciliada, Então o pagamento é conciliado.
- AC-006.2: Dado o encerramento do ciclo, Quando o resumo for produzido, Então a quantidade de conciliações dentro da tolerância é informada.

> A tolerância não consta de nenhuma norma do acervo. Preservada porque recusá-la transformaria em pendência um volume desconhecido de pagamentos hoje conciliados; sinalizada porque ninguém sabe esse volume.

### REQ-REC-007 — Atualizar a situação conforme o código de retorno

QUANDO o código de retorno for conhecido, o sistema DEVE atualizar a situação do pagamento conforme o código.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L204-L236
- nível: `P` — preservado; os três códigos e seus destinos permanecem
- AC-007.1: Dado o código de crédito efetuado, Quando conciliado, Então a situação passa a confirmada e a data de crédito é gravada.
- AC-007.2: Dado o código de devolução, Quando conciliado, Então a situação passa a devolvida.
- AC-007.3: Dado o código de estorno, Quando conciliado, Então a situação passa a estornada.

### REQ-REC-008 — Não contar como conciliado o retorno de código desconhecido

SE o código de retorno não pertencer ao domínio conhecido, ENTÃO o sistema NÃO DEVE contar o registro como conciliado.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L203
- nível: `C` — corrigido; o contador é incrementado antes da decisão
- AC-008.1: Dado um código de retorno desconhecido, Quando processado, Então o registro é contado como pendente.
- AC-008.2: Dado um código de retorno desconhecido, Quando processado, Então o código recebido fica registrado para análise.

### REQ-REC-009 — Registrar a divergência de valor como pendência do pagamento

SE o valor do retorno divergir do valor do pagamento além da tolerância, ENTÃO o sistema DEVE registrar a divergência no próprio pagamento.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm#L95-L97
- nível: `C` — corrigido; o dicionário declara a situação e o valor confirmado, e nenhum programa os grava
- AC-009.1: Dada uma divergência de valor, Quando registrada, Então o pagamento fica marcado como divergente, com o valor informado pelo banco.
- AC-009.2: Dada uma divergência registrada, Quando consultada, Então é possível listar todos os pagamentos divergentes de um período sem varrer a auditoria.
- AC-009.3: Dada uma divergência, Quando registrada, Então a situação anterior do pagamento é preservada.

> O domínio de `STAT-RECONCIL` é fixo e tem exatamente os dois valores necessários: `C` para conciliado e `D` para divergente. Não há decisão de modelagem a tomar — há um campo a alimentar.

### REQ-REC-010 — Registrar o retorno sem pagamento correspondente

SE o registro de retorno não corresponder a nenhum pagamento, ENTÃO o sistema DEVE registrar a ocorrência de forma consultável.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L181-L187
- nível: `C` — corrigido; hoje a única evidência é uma linha no log de execução
- AC-010.1: Dado um retorno sem pagamento correspondente, Quando processado, Então a ocorrência fica registrada com CPF, valor e identificação do arquivo.
- AC-010.2: Dado o encerramento do ciclo, Quando consultado, Então as ocorrências sem correspondência podem ser listadas.

### REQ-REC-011 — Respeitar as transições válidas de situação

O sistema DEVE recusar transição de situação que não seja válida para a situação atual do pagamento.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm#L60
- nível: `C` — corrigido; o legado grava a nova situação sem consultar a anterior
- AC-011.1: Dado um pagamento já confirmado, Quando um segundo retorno o confirmar, Então o sistema recusa e registra a tentativa.
- AC-011.2: Dado um pagamento cancelado, Quando um retorno chegar, Então o sistema recusa a atualização.
- AC-011.3: Dado um pagamento não remetido ao banco, Quando um retorno chegar, Então o sistema recusa e registra a inconsistência.

### REQ-REC-012 — Registrar cada conciliação na trilha de auditoria

QUANDO um pagamento for conciliado, o sistema DEVE registrar o evento na trilha de auditoria.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L316-L329
- nível: `P` — preservado; o `BATCHCON` já audita cada conciliação
- AC-012.1: Dada uma conciliação, Quando registrada, Então o evento identifica o pagamento, o código de retorno e a execução de origem.
- AC-012.2: Dada uma divergência, Quando registrada, Então o evento carrega o valor do SIFAP e o valor do banco.

> `WRITE-AUDIT-DIVERG` é o único ponto do acervo que preenche `AMT-PREV` e `AMT-NEW`. Esse comportamento é preservado e generalizado: na Fatia 1 todo evento de alteração já carrega valor anterior e posterior.

### REQ-REC-013 — Distinguir consulta de conciliação no código de ação

O sistema DEVE usar códigos de ação distintos para consulta e para conciliação.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L322
- nível: `C` — corrigido; decisão do `SIFAP-M-19`
- AC-013.1: Dado um evento de conciliação, Quando registrado, Então usa o código de conciliação.
- AC-013.2: Dado um evento de consulta, Quando registrado, Então usa o código de consulta.
- AC-013.3: Dado o histórico migrado, Quando carregado, Então o significado de cada registro é determinado pelo período de origem.

> `BATCHCON.NSP:322` grava `CO` para conciliação; `CONSBENF.NSP:172` grava `CO` para consulta. O dicionário registra 178 milhões de eventos `CO` como consulta até 2010 e 193,8 milhões como conciliação a partir de 2014. A data separa os dois significados sem ambiguidade, porque a portaria de 2010 interrompeu o registro de consultas.

### REQ-REC-014 — Encerrar o ciclo com o resultado consolidado

QUANDO um ciclo de conciliação terminar, o sistema DEVE registrar lidos, conciliados, divergentes e sem correspondência.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L262-L272
- nível: `P` — preservado
- AC-014.1: Dado o fim do ciclo, Quando registrado, Então o evento identifica o arquivo de origem e o período.
- AC-014.2: Dado um ciclo com pendências, Quando encerrado, Então o resultado indica que há pendências.

### REQ-REC-015 — Retomar a conciliação interrompida sem repetir efeitos

SE um ciclo de conciliação for interrompido, ENTÃO o sistema DEVE permitir retomá-lo sem reaplicar o que já foi conciliado.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L299-L307
- nível: `C` — corrigido; hoje `BACKOUT TRANSACTION` desfaz apenas a última transação
- AC-015.1: Dada uma interrupção no meio do arquivo, Quando o ciclo for retomado, Então os registros já conciliados não são processados de novo.
- AC-015.2: Dada uma retomada, Quando concluída, Então o total do ciclo corresponde ao arquivo inteiro.

### REQ-REC-016 — Identificar o banco pagador do retorno

O sistema DEVE registrar no pagamento o banco que informou o crédito.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP#L212
- nível: `C` — corrigido; o campo alfanumérico de código FEBRABAN recebe um literal numérico
- AC-016.1: Dado um retorno processado, Quando o pagamento for atualizado, Então o código do banco vem do próprio arquivo.
- AC-016.2: Dado um código de banco fora do domínio conhecido, Quando processado, Então o registro vira pendência.

---

## Fora desta especificação

| Item | Motivo |
|---|---|
| Integração Banco Real | Descontinuada em 2007; código comentado desde então em `BATCHCON.NSP:247-262` |
| Geração do arquivo de remessa | Fatia 4, `REQ-PAY-024` |
| Relatórios de conciliação | [Especificação 007](../007-relatorios/spec.md) |
| Destino dos pagamentos duplicados do histórico | Depende do resultado da conciliação; ver [ADR-0009](../../docs/adr/0009-conciliacao-bancaria.md) |

---

## Rastreabilidade

| REQ-ID | Regra do catálogo | Mistério associado | Nível |
|---|---|---|---|
| `REQ-REC-001` | 132, 158 | — | `P` |
| `REQ-REC-002` | 132, 158 | `SIFAP-M-08` | `C` |
| `REQ-REC-003` | 135, 161 | — | `C` |
| `REQ-REC-004` | 131 | — | `P` |
| `REQ-REC-005` | — | — | `P` |
| `REQ-REC-006` | 133 | — | `PS` |
| `REQ-REC-007` | 134 | — | `P` |
| `REQ-REC-008` | 134 | — | `C` |
| `REQ-REC-009` | 161 | `SIFAP-F5-02` | `C` |
| `REQ-REC-010` | 132 | — | `C` |
| `REQ-REC-011` | 159 | — | `C` |
| `REQ-REC-012` | 137 | — | `P` |
| `REQ-REC-013` | 139, 166 | `SIFAP-M-19` | `C` |
| `REQ-REC-014` | — | — | `P` |
| `REQ-REC-015` | 135 | — | `C` |
| `REQ-REC-016` | 161 | `SIFAP-F5-03` | `C` |

**Seis preservações, uma com sinalização, nove correções.**

Nenhuma correção altera valor de benefício. A conciliação não calcula: ela compara o que foi pago com o que foi devido e registra o resultado. As nove correções tratam de integridade — contagem honesta, pendência rastreável, transição válida, execução retomável — e não de quanto alguém recebe.

A única preservação com sinalização é a tolerância de um centavo, que **pode** mudar o destino de um pagamento. Preservada por isso mesmo.

**Mistérios que permanecem abertos.** `SIFAP-M-08` e `SIFAP-M-19` continuam sem validação humana em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md).

**Quatro achados desta leitura.**

Um código de retorno desconhecido é contado como conciliado, porque o contador é incrementado antes da decisão que o classifica. Registrado como `SIFAP-F5-01`.

A divergência de valor não deixa marca no pagamento: a única evidência vive na trilha de auditoria, e listar os divergentes de um período exige varrê-la. Registrado como `SIFAP-F5-02`.

O campo que identifica o banco pagador, declarado como código FEBRABAN alfanumérico, recebe o literal numérico `1`. Registrado como `SIFAP-F5-03`.

Os três campos que resolveriam os problemas acima — situação de conciliação, valor confirmado pelo banco e resumo do arquivo de retorno — estão declarados no dicionário, os dois últimos desde 2015, e nenhum programa os grava. Já constava como regra 161 do catálogo.
