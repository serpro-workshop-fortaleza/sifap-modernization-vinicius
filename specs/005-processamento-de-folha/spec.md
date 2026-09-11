# Especificação — Processamento de Folha

> **Fatia de migração:** 4 — Folha
> **Destino arquitetural:** contexto delimitado (`payment`), parte 1 de 2
> **Dados sob responsabilidade:** `PAYMENT` (FNR 152) — ~180.000.000 registros

| Campo | Valor |
|---|---|
| **Estágio** | Estágio 2 — Especificação |
| **Data** | 2026-09-11 |
| **ADRs aplicáveis** | [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md), [ADR-0007](../../docs/adr/0007-parametrizacao-do-programa-social.md), [ADR-0008](../../docs/adr/0008-calculo-do-beneficio.md) |
| **Entrada** | [`business-rules-catalog.md`](../../01-archaeology/business-rules-catalog.md), regras 64-104 e 116-130 |
| **Legado de origem** | `BATCHPGT`, `CALCBENF`, `VALELEG`, `CALCDSCT`, `CALCCORR` |

---

## Contexto

Esta é a fatia que decide **quanto cada pessoa recebe**. Concentra 8 dos 20 mistérios canônicos e cresce 3,8 milhões de registros por mês, em uma janela batch de 4 horas.

A leitura dos cinco programas encontrou três coisas que organizam esta especificação.

### O ciclo grava dois pagamentos, e um deles não tem número

O `BATCHPGT` executa a cadeia corporativa e depois refaz o cálculo por conta própria:

| Passo | O que acontece |
|---|---|
| `BATCHPGT.NSP:369` | Chama `VALELEG`; o retorno **é** testado em `:376` |
| `BATCHPGT.NSP:381` | Chama `CALCBENF`; o retorno **não é testado** |
| `CALCBENF.NSN:319` | O próprio `CALCBENF` faz `STORE PAYMENT-V` — **pagamento 1** |
| `BATCHPGT.NSP:412-472` | Recalcula tudo inline, com a mesma fórmula |
| `BATCHPGT.NSP:488` | `STORE PAYMENT-V` — **pagamento 2** |

Os dois pagamentos diferem em um ponto decisivo: **`CALCBENF` nunca atribui `NUM-PAYMENT`**, enquanto `BATCHPGT.NSP:475` atribui. O `SIFAP-M-05` e o `SIFAP-M-08` não são dois mistérios — são o mesmo defeito visto de dois ângulos. O pagamento sem número é o fantasma; o que segue para o banco é o do `BATCHPGT`, porque o extrato de `:490-500` usa `#SEQ-PAYMENT`.

O comentário em `BATCHPGT.NSP:363-367` declara a situação: *"THE INLINE CALCULATION BELOW REMAINS ACTIVE PENDING A DECISION FROM BENEFITS COORDINATION — TICKET 6622/2011 OPEN"*. Quinze anos.

### O fator de ajuste do programa é aplicado duas vezes

A Fatia 3 encontrou `CADPROG.NSP:124-130` gravando o valor base já multiplicado por `1 + (FACTOR-ADJUST × 0.347215)`.

O cálculo lê esse mesmo valor base e aplica o fator de novo, agora sem o coeficiente:

```text
COMPUTE #AMT-BENF = #AMT-BENF * (1 + #FACTOR-ADJUST)
```

Fonte: [`CALCBENF.NSN:261`](../../01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN) e [`BATCHPGT.NSP:432`](../../01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP).

São **dois fatores distintos derivados do mesmo campo**, aplicados em momentos diferentes, por programas diferentes, um deles com um coeficiente que ninguém sabe de onde veio.

### A tabela regional não corresponde ao que o índice significa

`CALCBENF.NSN:99-125` carrega 27 fatores rotulados por unidade federativa e os indexa por `COD-REGION`:

```text
IF #COD-REGION >= 1 AND #COD-REGION <= 25
  MOVE #TAB-REGION(#COD-REGION) TO #FACTOR-REGION
```

Mas `BENEFIC.ddm:67` declara `COD-REGION` como **`01-05` ou `99`**. Na prática, apenas os índices 1 a 5 são alcançáveis — e eles contêm os fatores de Acre, Amazonas, Amapá, Pará e Rondônia.

Todas as cinco macrorregiões do país recebem multiplicadores da região Norte. O Sudeste, cujo código é `04`, recebe `1.2800` — o fator rotulado como Pará.

## Escopo

**Dentro:** apuração de elegibilidade, cálculo do benefício mensal, décimo terceiro e abono, descontos, correção retroativa, ciclo de folha e extrato de remessa.

**Fora:** conciliação do retorno bancário e relatórios, que fecham o contexto Pagamento na Fatia 5.

---

## Requisitos

### REQ-PAY-001 — Gerar um único pagamento por beneficiário e período

O sistema DEVE gerar no máximo um pagamento por beneficiário em cada período de referência.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP#L381
- nível: `C` — corrigido; decisão do `SIFAP-M-05`, registrada no [ADR-0008](../../docs/adr/0008-calculo-do-beneficio.md)
- AC-001.1: Dado um ciclo processado, Quando concluído, Então cada beneficiário elegível tem exatamente um pagamento no período.
- AC-001.2: Dada uma tentativa de gerar pagamento para período já processado, Quando executada, Então nenhum registro novo é criado.

> A dupla gravação vem de o cálculo corporativo persistir o pagamento (`CALCBENF.NSN:319`) e o ciclo persistir outro logo depois (`BATCHPGT.NSP:488`). O cálculo deixa de gravar; gravar é responsabilidade do ciclo.

### REQ-PAY-002 — Atribuir número único a todo pagamento

O sistema DEVE atribuir a cada pagamento um número único gerado por sequência do banco de dados.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN#L308-L319
- nível: `C` — corrigido; decisão do `SIFAP-M-08`
- AC-002.1: Dado um pagamento gerado, Quando consultado, Então possui número diferente de zero.
- AC-002.2: Dadas execuções concorrentes, Quando ambas gerarem pagamentos, Então os números são distintos.

> `CALCBENF` grava sem atribuir `NUM-PAYMENT`, sobre um campo que `PAYMENT.ddm:29` declara `U` — único. O `BATCHPGT` usa um contador de memória (`:473`), com a mesma fragilidade que a `REQ-AUD-003` corrigiu na trilha.

### REQ-PAY-003 — Processar apenas beneficiários ativos com documento válido

SE o beneficiário não estiver ativo ou seu CPF for inválido, ENTÃO o sistema NÃO DEVE gerar pagamento e DEVE registrar o motivo.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP#L262-L287
- nível: `P` — preservado
- AC-003.1: Dado um beneficiário suspenso, Quando o ciclo executar, Então nenhum pagamento é gerado para ele.
- AC-003.2: Dado um CPF inválido no cadastro, Quando o ciclo executar, Então o beneficiário é rejeitado com motivo registrado.

> O comentário em `BATCHPGT.NSP:269-272` revela que, até 2011, a folha não validava o CPF lido do cadastro. Treze anos pagando sem verificar documento.

### REQ-PAY-004 — Recusar elegibilidade de programa não ativo

SE o programa social do beneficiário não estiver ativo, ENTÃO o sistema DEVE considerá-lo inelegível.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN#L114-L118
- nível: `P` — preservado
- AC-004.1: Dado um programa encerrado, Quando a elegibilidade for apurada, Então o beneficiário é inelegível.

> É a única implementação dessa regra. A Fatia 3 tornou possível que um programa deixe de estar ativo — antes, `CADPROG.NSP:134` gravava `A` sempre e esta verificação nunca era acionada.

### REQ-PAY-005 — Apurar a situação do beneficiário de forma única

O sistema DEVE aplicar o mesmo critério de situação cadastral na apuração de elegibilidade e no cálculo.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN#L133-L151
- nível: `C` — corrigido; os dois programas divergem
- AC-005.1: Dado um beneficiário com situação fora do domínio, Quando a elegibilidade for apurada, Então ele é inelegível.
- AC-005.2: Dada qualquer situação diferente de ativa, Quando apurada, Então o resultado é o mesmo em todos os pontos de decisão.

> `VALELEG.NSN:133` testa `NE 'A'` e depois compara com `S`, `C`, `D` e `I`: um valor em branco não casa com nenhum e **sai do bloco sem alterar a elegibilidade**. Já `CALCBENF.NSN:180` testa `NE 'A'` e recusa direto. O mesmo beneficiário é elegível para um e não para o outro. A Fatia 2 eliminou a origem do branco; esta unifica o critério.

### REQ-PAY-006 — Verificar a faixa etária do programa

SE a idade do beneficiário estiver fora da faixa etária definida pelo programa, ENTÃO o sistema DEVE considerá-lo inelegível.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN#L153-L169
- nível: `P` — preservado
- AC-006.1: Dada uma idade mínima zero, Quando apurada, Então não há limite inferior.
- AC-006.2: Dada uma idade acima do máximo do programa, Quando apurada, Então o beneficiário é inelegível.

### REQ-PAY-007 — Comparar a renda declarada com o teto do programa

SE a renda familiar declarada superar o teto do programa, ENTÃO o sistema DEVE considerar o beneficiário inelegível.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN#L175-L179
- nível: `PS` — preservado com sinalização; decisão do `SIFAP-M-11`
- AC-007.1: Dada uma renda familiar acima do teto, Quando apurada, Então o beneficiário é inelegível.
- AC-007.2: Dada qualquer apuração de renda, Quando registrada, Então o relatório expõe a diferença em relação à renda per capita.

> O campo do programa chama-se `MAX-PERCAP-INCOME` — teto **per capita** — e o valor comparado é `AMT-FAMILY-INCOME`, a renda **familiar total**. O dicionário oferece `CJ IND-PERCAP-INCOME` e `CI QTY-FAMILY-MEMBERS` para o cálculo correto, ambos sem consumidor. Preservado porque altera quem recebe.

### REQ-PAY-008 — Não conceder elegibilidade automática por região

O sistema NÃO DEVE conceder elegibilidade sem verificação com base no código de região.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN#L120-L128
- nível: `PS` — preservado com sinalização; decisão do `SIFAP-M-13`
- AC-008.1: Dado um beneficiário de região especial, Quando a elegibilidade for apurada, Então o resultado consta do relatório de concessões por essa via.
- AC-008.2: Dada uma concessão por região especial, Quando registrada, Então ela é auditável individualmente.

> `VALELEG.NSN:123` concede elegibilidade total à região `99`, ignorando situação, idade, renda e documentação. Preservado por alterar quem recebe; o que muda é deixar de ser invisível.

### REQ-PAY-009 — Devolver todos os motivos de inelegibilidade

QUANDO um beneficiário for considerado inelegível, o sistema DEVE devolver todos os motivos apurados.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN#L226-L236
- nível: `C` — corrigido; o legado acumula até dez motivos e devolve apenas o primeiro
- AC-009.1: Dado um beneficiário com três impedimentos, Quando apurado, Então os três motivos são retornados.

> `VALELEG.NSN:236` faz `MOVE #REASON(1) TO #PC-MSG`. Quem corrige um impedimento descobre o seguinte na tentativa seguinte, um de cada vez.

### REQ-PAY-010 — Calcular o benefício pela fórmula multiplicativa de cinco fatores

O sistema DEVE calcular o benefício mensal multiplicando o valor base do programa pelos fatores regional, familiar, de renda e etário.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN#L255-L258
- nível: `P` — preservado; decisão do `SIFAP-M-09`
- AC-010.1: Dados os mesmos insumos, Quando calculado, Então o resultado é idêntico ao do legado.
- AC-010.2: Dado um cálculo concluído, Quando consultado, Então cada fator aplicado é recuperável.

> A `RN-013` documenta fórmula aditiva; o código roda a multiplicativa, replicada de forma idêntica em `CALCBENF.NSN:255-258` e `BATCHPGT.NSP:428-431`. Preserva-se a que roda, e cada fator passa a ser registrado para tornar a divergência mensurável.

### REQ-PAY-011 — Obter o fator regional da parametrização do programa

O sistema DEVE obter o multiplicador regional dos parâmetros regionais do programa, e não de tabela embutida no código.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN#L99-L125
- nível: `C` — corrigido; o índice não corresponde ao que a tabela rotula
- AC-011.1: Dado um beneficiário de determinada região, Quando o fator for obtido, Então ele vem do parâmetro regional daquele programa.
- AC-011.2: Dado um programa sem parâmetro regional para a região do beneficiário, Quando o cálculo executar, Então ele falha explicitamente, sem adotar valor padrão silencioso.

> [!WARNING]
> **Esta correção altera valor pago.** A tabela legada é indexada por um código que vale de 1 a 5 e contém fatores rotulados por unidade federativa, de modo que todas as macrorregiões recebem multiplicadores do Norte. Migrar os valores como estão preserva o comportamento; corrigir a correspondência é decisão de negócio. Ver [ADR-0008](../../docs/adr/0008-calculo-do-beneficio.md).

### REQ-PAY-012 — Determinar o fator de renda de forma total

O sistema DEVE determinar um fator de renda para qualquer valor de renda declarada.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP#L412
- nível: `C` — corrigido; decisão do `SIFAP-F4-01`, achado desta leitura
- AC-012.1: Dada uma renda acima do limite da última faixa, Quando calculada, Então o sistema aplica regra definida, sem herdar valor de outro beneficiário.
- AC-012.2: Dado um beneficiário processado após outro, Quando calculado, Então nenhum fator do anterior influencia o resultado.

> A última faixa do legado termina em `9.999,99`. Acima disso, o laço de `DET-INCOME-BAND-BATCH` não encontra faixa e `#FACTOR-INCOME` **mantém o valor do beneficiário anterior**, porque no `BATCHPGT` a variável não é reinicializada a cada iteração. É defeito de integridade: o resultado de um beneficiário depende de quem veio antes dele.

### REQ-PAY-013 — Aplicar o fator de ajuste do programa uma única vez

O sistema DEVE aplicar o fator de ajuste do programa uma única vez sobre o valor base.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN#L261
- nível: `C` — corrigido; hoje o fator é aplicado na gravação do programa e de novo no cálculo
- AC-013.1: Dado um programa com fator de ajuste, Quando o benefício for calculado, Então o fator aparece uma única vez no resultado.
- AC-013.2: Dado o valor base gravado pela Fatia 3, Quando lido pelo cálculo, Então ele não contém fator embutido.

> `CADPROG.NSP:125` multiplica o valor base por `1 + (FACTOR-ADJUST × 0.347215)` antes de gravar, e `CALCBENF.NSN:261` multiplica de novo por `1 + FACTOR-ADJUST`. O `REQ-PRG-004` já corrigiu a gravação; este requisito fecha o outro lado.

### REQ-PAY-014 — Truncar valores monetários em duas casas

O sistema DEVE truncar todo valor monetário em duas casas decimais, sem arredondar.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN#L264-L266
- nível: `P` — preservado
- AC-014.1: Dado o valor 100,999, Quando truncado, Então o resultado é 100,99.
- AC-014.2: Dado qualquer valor calculado, Quando gravado, Então a terceira casa é descartada, e não somada.

> O legado multiplica por 100 movendo para um campo inteiro e divide de volta. O truncamento é consequência do tipo, não de uma decisão explícita — mas é o comportamento que produziu todos os valores pagos até hoje.

### REQ-PAY-015 — Calcular décimo terceiro e abono em dezembro

QUANDO o período de referência for dezembro, o sistema DEVE calcular o décimo terceiro e, ONDE o programa for de assistência, o abono de 15%.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN#L272-L290
- nível: `P` — preservado
- AC-015.1: Dado dezembro, Quando calculado, Então o bruto soma benefício, décimo terceiro e, se couber, abono.
- AC-015.2: Dado um programa que não seja de assistência, Quando calculado em dezembro, Então não há abono.

> O comentário do programa descreve `AMT_13 = AMT_BASE * FACTOR_REGION * (ACTIVE_MONTHS/12)`, e o código usa o fator etário no lugar dos meses ativos. Preserva-se o código; a divergência entra no registro de achados.

### REQ-PAY-016 — Registrar o tipo de pagamento dentro do domínio declarado

O sistema DEVE registrar o tipo de pagamento com valor pertencente ao domínio declarado no dicionário.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm#L74-L75
- nível: `C` — corrigido; o cálculo grava um valor fora do domínio
- AC-016.1: Dado um pagamento de dezembro, Quando gravado, Então seu tipo pertence ao domínio.
- AC-016.2: Dada uma tentativa de gravar tipo fora do domínio, Quando executada, Então a operação é recusada.

> `PAYMENT.ddm:74` declara `N`, `R`, `A` e `C`. `CALCBENF.NSN:273` grava `D` em dezembro — valor que não existe no domínio e que nenhum consumidor sabe interpretar.

### REQ-PAY-017 — Calcular a contribuição social por faixa

O sistema DEVE calcular a contribuição social pela alíquota correspondente à faixa de valor bruto.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCDSCT.NSP#L62-L69
- nível: `P` — preservado; decisão do `SIFAP-M-10`
- AC-017.1: Dado um bruto de R$ 800,00, Quando calculado, Então a alíquota aplicada é a da faixa correspondente.
- AC-017.2: Dado um bruto acima da última faixa, Quando calculado, Então a regra aplicada é definida e registrada.

> Duas contribuições convivem: `CALCDSCT.NSP:62-69` define quatro faixas de 3% a 9%, e `CALCBENF.NSN:344-350` aplica 3% fixo acima de R$ 500,00. O caminho efetivamente executado pela folha é o do `CALCBENF`, porque o `CALCDSCT` não é chamado por nenhum programa do acervo.

### REQ-PAY-018 — Reconhecer todos os tipos de desconto declarados

O sistema DEVE reconhecer todos os tipos de desconto declarados no dicionário.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm#L44
- nível: `C` — corrigido; cinco dos oito tipos são silenciosamente ignorados
- AC-018.1: Dado um desconto de qualquer tipo declarado, Quando processado, Então ele é aplicado ou recusado explicitamente.
- AC-018.2: Dado um tipo desconhecido, Quando encontrado, Então o processamento falha com motivo, sem ignorar em silêncio.

> O dicionário declara `TYPE-DISC` como `A 3` com os valores `IR`, `JD`, `CS`, `PA`, `EM`, `TX`, `OU` e `EX`. O `CALCDSCT` move esse campo para `#TYPE-DISC (A1)` e compara com `J`, `P`, `I`, `S` e `A`. O truncamento faz `JD`, `PA` e `IR` funcionarem por acidente; `CS`, `EM`, `TX`, `OU` e `EX` caem em `NONE → IGNORE`.

### REQ-PAY-019 — Aplicar o teto de desconto sem reduzir o valor judicial

O sistema DEVE limitar os descontos a 30% do valor bruto, sem que o limite reduza desconto de origem judicial.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCDSCT.NSP#L128-L137
- nível: `C` — corrigido; o teto é aplicado sobre o total acumulado
- AC-019.1: Dado um desconto judicial de 50% e um administrativo, Quando calculados, Então o judicial permanece íntegro.
- AC-019.2: Dados descontos não judiciais que somem mais de 30%, Quando calculados, Então eles são limitados a 30%.

> O teto legado corta `#AMT-TOTAL-DISC`, que acumula todos os tipos. Quando um desconto não judicial é processado depois de um judicial, o corte remove parte do judicial — que o próprio programa declara não ter teto.

### REQ-PAY-020 — Recalcular o valor líquido quando os descontos mudarem

QUANDO os descontos de um pagamento forem recalculados, o sistema DEVE recalcular o valor líquido.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCDSCT.NSP#L183-L188
- nível: `C` — corrigido; o legado atualiza o total de descontos e não toca no líquido
- AC-020.1: Dada uma alteração de descontos, Quando gravada, Então o líquido reflete bruto menos descontos.

> `CALCDSCT` atualiza `AMT-DISC-TOTAL` e encerra a transação. `AMT-NET` permanece com o valor calculado antes dos descontos, e é `AMT-NET` que segue para o extrato bancário.

### REQ-PAY-021 — Aplicar índice de correção do período completo

QUANDO uma correção retroativa for aplicada, o sistema DEVE usar o índice acumulado do período entre a competência original e a data da correção.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCCORR.NSP#L229-L239
- nível: `PS` — preservado com sinalização; decisão do `SIFAP-M-12`
- AC-021.1: Dada uma correção, Quando aplicada, Então o índice utilizado e o período coberto constam do registro.
- AC-021.2: Dada a apuração de correções, Quando relatada, Então a diferença em relação ao acumulado real é exposta.

> `CALC-INDEX-ACCUM` localiza o ano na tabela e multiplica por um **único mês**, apesar do nome. Preservado por alterar valor pago; o que muda é registrar o índice aplicado.

### REQ-PAY-022 — Falhar explicitamente quando o índice do período não existir

SE não houver índice cadastrado para o período de uma correção, ENTÃO o sistema DEVE recusar a correção com motivo.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CALCCORR.NSP#L80-L88
- nível: `C` — corrigido; o legado aplica índice neutro em silêncio
- AC-022.1: Dado um pagamento de ano ausente da tabela, Quando a correção executar, Então ela é recusada com motivo registrado.

> A tabela cobre dez anos. Fora deles, `#INDEX-ACCUM` permanece `1.000000` e a correção resulta em diferença zero — indistinguível de "não havia correção devida".

### REQ-PAY-023 — Registrar evento por pagamento e por ciclo

QUANDO um pagamento for gerado, alterado por descontos ou corrigido, o sistema DEVE publicar o evento correspondente, além do evento de conclusão do ciclo.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP#L536-L546
- nível: `C` — corrigido; o legado grava um único evento por ciclo
- AC-023.1: Dado um ciclo com N pagamentos, Quando concluído, Então existem N eventos de pagamento mais um de ciclo.
- AC-023.2: Dado um recálculo de descontos, Quando concluído, Então existe evento correspondente.
- AC-023.3: Dado um pagamento individual, Quando auditado, Então é possível identificar quem o gerou e quando.

> É o `REQ-AUD-010`, implementado desde a Fatia 1 e sem publicador até agora. `CALCDSCT` altera valores financeiros e não inclui a rotina de auditoria; `CALCCORR.NSP:243` inclui.

### REQ-PAY-024 — Gerar o extrato de remessa bancária

QUANDO o ciclo for concluído, o sistema DEVE gerar o extrato de remessa com os pagamentos do período.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP#L490-L500
- nível: `P` — preservado
- AC-024.1: Dado um ciclo concluído, Quando o extrato for gerado, Então ele contém um registro por pagamento.
- AC-024.2: Dado um registro do extrato, Quando lido, Então o valor corresponde ao líquido do pagamento.

### REQ-PAY-025 — Devolver código de encerramento do ciclo

QUANDO o ciclo terminar, o sistema DEVE devolver um código indicando sucesso, existência de rejeições ou ausência de processamento.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP#L548-L566
- nível: `P` — preservado
- AC-025.1: Dado um ciclo sem rejeições, Quando concluído, Então o código indica sucesso.
- AC-025.2: Dado um ciclo que nada gerou, Quando concluído, Então o código o distingue de um ciclo bem-sucedido.

> O legado descreve o código `8` como "período já processado" e o emite sempre que nada foi gerado, por qualquer motivo. A descrição e o comportamento divergem; preserva-se o comportamento.

---

## Fora de escopo

| Item | Razão | Destino |
|---|---|---|
| Conciliação do retorno bancário | Fecha o contexto Pagamento | Fatia 5 |
| Relatórios de pagamento e auditoria | Leem dados que esta fatia produz | Fatia 5 |
| Integração SIAFI (`FA`–`FE`) | Nenhum programa do acervo a implementa | Fora do escopo |
| Estorno (`GG`, `GH`) | Sem programa que o execute no acervo | Fatia 5 |
| Correção da correspondência região/fator | Altera valor pago; decisão de negócio | Ver ADR-0008 |
| Cálculo per capita da renda | Altera quem recebe | Ver `REQ-PAY-007` |

---

## Rastreabilidade

| REQ-ID | Regra do catálogo | Mistério associado | Nível |
|---|---|---|---|
| `REQ-PAY-001` | 125 | `SIFAP-M-05` | `C` |
| `REQ-PAY-002` | 78, 127 | `SIFAP-M-08` | `C` |
| `REQ-PAY-003` | 120, 121 | — | `P` |
| `REQ-PAY-004` | 96, 123 | — | `P` |
| `REQ-PAY-005` | 98 | `SIFAP-M-03` | `C` |
| `REQ-PAY-006` | 99 | — | `P` |
| `REQ-PAY-007` | 70, 100 | `SIFAP-M-11` | `PS` |
| `REQ-PAY-008` | 97 | `SIFAP-M-13` | `PS` |
| `REQ-PAY-009` | 103 | — | `C` |
| `REQ-PAY-010` | 72 | `SIFAP-M-09` | `P` |
| `REQ-PAY-011` | 68 | — | `C` |
| `REQ-PAY-012` | 69 | `SIFAP-F4-01` | `C` |
| `REQ-PAY-013` | 39, 73 | `SIFAP-M-04` | `C` |
| `REQ-PAY-014` | 74 | — | `P` |
| `REQ-PAY-015` | 75, 76 | — | `P` |
| `REQ-PAY-016` | 77 | — | `C` |
| `REQ-PAY-017` | 79, 91 | `SIFAP-M-10` | `P` |
| `REQ-PAY-018` | 87 | — | `C` |
| `REQ-PAY-019` | 90, 92 | — | `C` |
| `REQ-PAY-020` | 94 | — | `C` |
| `REQ-PAY-021` | 84 | `SIFAP-M-12` | `PS` |
| `REQ-PAY-022` | 85 | — | `C` |
| `REQ-PAY-023` | 80, 95, 129 | — | `C` |
| `REQ-PAY-024` | 128 | — | `P` |
| `REQ-PAY-025` | 130 | — | `P` |

**Nove preservações, três com sinalização, treze correções.**

As decisões que alteram quanto uma pessoa recebe — fórmula, contribuição, faixa de renda, região especial, índice de correção, truncamento, décimo terceiro — estão todas em `P` ou `PS`. Nenhuma em `C`.

Duas correções chegam perto da fronteira e ficam registradas com aviso: o `REQ-PAY-011`, que tira o fator regional de uma tabela mal indexada, e o `REQ-PAY-013`, que remove a aplicação dupla do fator de ajuste. As duas mudam o valor calculado, e o [ADR-0008](../../docs/adr/0008-calculo-do-beneficio.md) registra por que são tratadas como defeito de integridade e não como regra de negócio.

**Mistérios que permanecem abertos.** `SIFAP-M-05`, `M-08`, `M-09`, `M-10`, `M-11`, `M-12` e `M-13` continuam sem validação humana em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md).

**Três achados novos desta leitura.**

O `SIFAP-M-05` e o `SIFAP-M-08` são o mesmo defeito: o pagamento que o `CALCBENF` grava é justamente o que fica sem número.

O fator de ajuste do programa é aplicado duas vezes, com coeficientes diferentes, por programas diferentes. Já constava como achado bônus da área Cálculo.

No `BATCHPGT`, uma renda acima da última faixa faz o beneficiário herdar o fator de renda do beneficiário processado imediatamente antes dele. Sem registro anterior no acervo; consta agora como `SIFAP-F4-01` em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md).

---

## Definição de pronto

- [x] Todo requisito usa um padrão EARS com `DEVE`.
- [x] Todo REQ-ID é único e declarado como título.
- [x] Todo REQ-ID tem `source_legacy:` apontando para arquivo existente.
- [x] Todo requisito tem critérios de aceitação em Dado/Quando/Então.
- [x] Cada requisito declara o nível de tratamento do [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md).
- [x] `plan.md` e `tasks.md` gerados.
