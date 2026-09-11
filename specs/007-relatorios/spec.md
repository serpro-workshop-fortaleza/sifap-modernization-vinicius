# Especificação — Relatórios

> **Fatia de migração:** 5 — Conciliação e Relatórios
> **Destino arquitetural:** contexto delimitado (`report`)
> **Dados sob responsabilidade:** nenhum — o contexto apenas lê

| Campo | Valor |
|---|---|
| **Estágio** | Estágio 2 — Especificação |
| **Data** | 2026-09-11 |
| **ADRs aplicáveis** | [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md), [ADR-0010](../../docs/adr/0010-relatorios-como-leitura.md) |
| **Entrada** | [`business-rules-catalog.md`](../../01-archaeology/business-rules-catalog.md), regras 143-153, 159, 165 e 167-169 |
| **Legado de origem** | `BATCHREL`, `RELPGT`, `RELAUDIT` |

---

## Contexto

Relatório não decide nada. Ele mostra. Por isso esta é a única fatia do projeto em que **nenhum requisito altera o que uma pessoa recebe** — e, ainda assim, é onde está o maior número de correções, porque mostrar errado é o defeito mais fácil de não notar.

A leitura dos três programas encontrou quatro coisas.

### O agrupamento por região nunca esteve correto

O `BATCHREL` converte o código de região do beneficiário em número e o classifica por faixas:

```text
COMPUTE #COD-REGION = VAL(BENEFICIARY-V.COD-REGION)
IF #COD-REGION >= 1 AND #COD-REGION <= 5
  MOVE 1 TO #IDX-REGION      /* NORTH */
ELSE IF #COD-REGION >= 6 AND #COD-REGION <= 10
  MOVE 2 TO #IDX-REGION      /* NORTHEAST */
...
```

Fonte: [`BATCHREL.NSP:150-170`](../../01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP).

O domínio real de `COD-REGION`, confirmado pela Fatia 3 e pela restrição `ck_region_code`, é `01` a `05` e `99`. **Todos os códigos válidos caem na primeira faixa** e são somados como Norte. O código `99` cai no `ELSE` final e é somado como Centro-Oeste. Beneficiário sem cadastro encontrado fica com zero e também vai para o Centro-Oeste.

O relatório consolidado por região, produzido mensalmente desde 1999 e distribuído à SENARC, mostra a folha inteira concentrada em duas regiões. As mudanças de `20/04/2006` e `09/10/2006` ajustaram subtotais e quebras sobre essa base.

### O mesmo relatório usa dois critérios de arredondamento

O total geral acumula o valor arredondado; os totais de desconto e líquido acumulam o valor gravado:

```text
COMPUTE #AMT-ROUND = PAYMENT-V.AMT-GROSS + 0.005
COMPUTE #AMT-TEMP = #AMT-ROUND * 100
COMPUTE #AMT-ROUND = #AMT-TEMP / 100
ADD #AMT-ROUND TO #TOT-REGION-GROSS(#IDX-REGION)
ADD PAYMENT-V.AMT-DISC-TOTAL TO #TOT-REGION-DISC(#IDX-REGION)
ADD PAYMENT-V.AMT-NET TO #TOT-REGION-NET(#IDX-REGION)
```

Fonte: [`BATCHREL.NSP:174-182`](../../01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP).

O `SIFAP-M-07` pergunta por que o relatório arredonda e o cálculo trunca. A resposta é mais estreita do que a pergunta: o relatório arredonda **apenas o bruto**. Dentro da mesma linha impressa, bruto menos desconto não dá líquido.

### O subtotal por programa depende de uma ordenação que não existe

O `RELPGT` detecta a quebra comparando com o valor anterior:

```text
IF PAYMENT-V.COD-PROGRAM NE #PROG-PREV AND #PROG-PREV NE ' '
  PERFORM PRINT-SUBTOTAL
```

Fonte: [`RELPGT.NSP:141-148`](../../01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP).

A leitura é `READ PAYMENT-V BY YEAR-MONTH-REF` — ordenada por período, não por programa. Dentro de um período os programas se alternam livremente, e a quebra dispara a cada troca. Um programa com pagamentos intercalados recebe vários subtotais parciais, nenhum deles correspondendo ao seu total.

O comentário em `:140` diz: *"MANUAL BREAK - RETAINED SINCE 1997 - TICKET 2210/2004"*.

Há um segundo efeito no mesmo programa: o total geral está dentro de `AT END OF DATA`, que não executa quando o laço termina por `ESCAPE BOTTOM` — o caminho tomado sempre que existem pagamentos posteriores ao período final. O relatório de um período que não seja o último sai sem total geral.

### O relatório de auditoria esconde exatamente o que auditoria significa

```text
IF AUDIT-V.COD-ACTION = 'EX'
  ADD 1 TO #QTY-FILTERED
  ESCAPE TOP
END-IF
```

Fonte: [`RELAUDIT.NSP:128-134`](../../01-archaeology/legacy-sifap/natural-programs/RELAUDIT.NSP).

O filtro é incondicional e anterior aos filtros que a pessoa escolheu. Não há parâmetro que o desligue. O próprio dicionário documenta o contorno em `AUDIT.ddm:138-140`: *"CAUTION - PROGRAM RELAUDIT.NSN FILTERS 'EX' ACTIONS FROM DISPLAY. TO VIEW DELETIONS, QUERY DIRECTLY THROUGH ADABAS ONLINE (SYSAOS)"*.

O relatório também não exibe `NUM-CPF-AFFECTED` em nenhuma das duas saídas. A trilha sabe quem foi afetado; o relatório não conta. E a saída para tela omite a descrição do evento que a saída para impressora mostra — a mesma consulta produz dois conteúdos diferentes conforme o destino.

---

## Requisitos

### REQ-REP-001 — Consolidar a folha do período por região, programa e situação

O sistema DEVE produzir o consolidado do período com totais por região, por programa e por situação.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP#L133-L212
- nível: `P` — preservado; os três agrupamentos permanecem
- AC-001.1: Dado um período com pagamentos, Quando consolidado, Então os totais de quantidade e valor são apresentados por agrupamento.
- AC-001.2: Dado um período sem pagamentos, Quando consolidado, Então o relatório informa a ausência em vez de apresentar zeros.

### REQ-REP-002 — Agrupar por região pelo código de região do beneficiário

O sistema DEVE agrupar os totais regionais pelo código de região registrado no beneficiário.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP#L150-L170
- nível: `C` — corrigido; a classificação por faixas numéricas não corresponde ao domínio
- AC-002.1: Dado um beneficiário com código de região válido, Quando consolidado, Então o pagamento é somado na região correspondente ao código.
- AC-002.2: Dado um beneficiário sem código de região, Quando consolidado, Então o pagamento é somado a um grupo próprio de não classificados, e não a uma região real.

> Correção de apresentação, não de valor: o total geral não muda, apenas a distribuição entre as linhas.

### REQ-REP-003 — Arredondar o valor bruto no consolidado

O sistema DEVE arredondar o valor bruto ao consolidá-lo.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP#L174-L177
- nível: `P` — preservado; decisão do `SIFAP-M-07`
- AC-003.1: Dado um conjunto de pagamentos, Quando consolidado, Então o bruto é arredondado como no legado.
- AC-003.2: Dado o consolidado, Quando apresentado, Então o relatório declara que o total arredondado difere da soma dos valores truncados da base.

### REQ-REP-004 — Aplicar o mesmo critério de arredondamento a todos os totais

O sistema DEVE aplicar o mesmo critério de arredondamento ao bruto, ao desconto e ao líquido do consolidado.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP#L178-L182
- nível: `C` — corrigido; hoje só o bruto é arredondado
- AC-004.1: Dado o consolidado de um agrupamento, Quando apresentado, Então bruto menos desconto é igual ao líquido apresentado.

### REQ-REP-005 — Não classificar situação desconhecida como situação válida

SE a situação de um pagamento não pertencer ao domínio, ENTÃO o sistema NÃO DEVE classificá-la como uma situação conhecida.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP#L196-L198
- nível: `C` — corrigido; o `NONE` do legado classifica como gerada
- AC-005.1: Dada uma situação fora do domínio, Quando consolidada, Então entra em um grupo de não classificados.
- AC-005.2: Dada a existência de não classificados, Quando o relatório for produzido, Então a quantidade é informada.

> `BATCHREL` classifica o desconhecido como `GENERATED` e `RELPGT` o classifica como `OTHER`. Dois relatórios do mesmo job descrevem o mesmo pagamento de formas diferentes.

### REQ-REP-006 — Detalhar os pagamentos do período com totais por programa

O sistema DEVE produzir o detalhamento dos pagamentos de um intervalo de períodos, com subtotal por programa e total geral.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP#L120-L232
- nível: `P` — preservado
- AC-006.1: Dado um intervalo de períodos, Quando detalhado, Então cada pagamento aparece com período, titular, valores, situação e tipo.
- AC-006.2: Dado um filtro por programa, Quando aplicado, Então apenas os pagamentos daquele programa aparecem.

### REQ-REP-007 — Totalizar por programa independentemente da ordem de leitura

O sistema DEVE produzir um único subtotal por programa, qualquer que seja a ordem em que os pagamentos forem lidos.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP#L141-L148
- nível: `C` — corrigido; a quebra manual pressupõe ordenação por programa
- AC-007.1: Dado um período com programas intercalados, Quando detalhado, Então cada programa recebe exatamente um subtotal.
- AC-007.2: Dado o conjunto de subtotais, Quando somados, Então o resultado é igual ao total geral.

### REQ-REP-008 — Apresentar o total geral em qualquer condição de término

O sistema DEVE apresentar o total geral do relatório detalhado independentemente de como a leitura terminar.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP#L224-L230
- nível: `C` — corrigido; o total depende de `AT END OF DATA`, que o `ESCAPE BOTTOM` impede
- AC-008.1: Dado um período que não seja o último da base, Quando detalhado, Então o total geral é apresentado.

### REQ-REP-009 — Mascarar o documento do titular nos relatórios

O sistema DEVE mascarar o CPF do titular em todo relatório impresso ou exportado.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP#L164-L167
- nível: `C` — corrigido; a máscara atual oculta três dos onze dígitos
- AC-009.1: Dado um relatório com CPF, Quando produzido, Então o documento aparece mascarado conforme a regra do `REQ-BEN-017`.
- AC-009.2: Dado um relatório mascarado, Quando lido junto ao nome e à unidade federativa, Então o documento não é reconstituível.

> A máscara de `RELPGT` oculta apenas o prefixo e expõe oito dígitos ao lado do nome completo e da UF, em relatório físico distribuído ao centro de impressão. Reidentificação trivial.

### REQ-REP-010 — Exibir os eventos de exclusão no relatório de auditoria

O sistema DEVE incluir os eventos de exclusão no relatório de auditoria.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELAUDIT.NSP#L128-L134
- nível: `C` — corrigido; decisão do `SIFAP-M-18`; realiza o `REQ-AUD-011`
- AC-010.1: Dado um período com exclusões, Quando o relatório for gerado sem filtro, Então as exclusões aparecem.
- AC-010.2: Dado um filtro por tipo de ação, Quando aplicado, Então a exclusão só é omitida por escolha explícita de quem consulta.

### REQ-REP-011 — Identificar o titular afetado no relatório de auditoria

O sistema DEVE apresentar o titular afetado em cada evento do relatório de auditoria.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm#L57
- nível: `C` — corrigido; o campo existe na trilha e não aparece em nenhuma saída
- AC-011.1: Dado um evento com titular afetado, Quando apresentado, Então o documento mascarado do titular aparece.
- AC-011.2: Dado um evento sem titular, Quando apresentado, Então a coluna fica vazia sem sugerir ausência de dado.

### REQ-REP-012 — Produzir o mesmo conteúdo em qualquer meio de saída

O sistema DEVE apresentar o mesmo conjunto de informações independentemente do meio de saída escolhido.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELAUDIT.NSP#L196-L211
- nível: `C` — corrigido; a saída para tela omite a descrição, o subtotal diário e o histograma
- AC-012.1: Dada a mesma consulta, Quando produzida em meios diferentes, Então o conteúdo apresentado é o mesmo.

### REQ-REP-013 — Sinalizar o pagamento sem beneficiário correspondente

SE um pagamento não tiver beneficiário correspondente no cadastro, ENTÃO o sistema DEVE sinalizá-lo no relatório.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP#L152-L160
- nível: `C` — corrigido; o legado imprime a linha com o nome em branco
- AC-013.1: Dado um pagamento órfão, Quando detalhado, Então a linha o identifica como sem cadastro correspondente.
- AC-013.2: Dado o fim do relatório, Quando produzido, Então a quantidade de órfãos é informada.

### REQ-REP-014 — Ler os valores gravados sem recalculá-los

O sistema NÃO DEVE recalcular valores de pagamento ao produzir relatórios.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP#L174
- nível: `P` — preservado; relatório é leitura
- AC-014.1: Dado um relatório de um período, Quando produzido duas vezes sobre a mesma base, Então os dois resultados são idênticos.
- AC-014.2: Dado um relatório, Quando produzido, Então nenhum registro de pagamento é alterado.

### REQ-REP-015 — Preservar a cópia arquivada do consolidado

QUANDO o consolidado for produzido, o sistema DEVE gerar uma cópia arquivável do resultado.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP#L204-L210
- nível: `P` — preservado; retenção de 10 anos pela Lei 8.159, art. 14
- AC-015.1: Dado um consolidado produzido, Quando arquivado, Então os campos da cópia são separáveis sem ambiguidade.

> O legado monta a linha com `COMPRESS` sem delimitador: os campos ficam concatenados e a separação depende de conhecer as larguras. A cópia é preservada; o formato ilegível, não.

### REQ-REP-016 — Restringir o relatório de auditoria a um intervalo explícito

O sistema DEVE exigir um intervalo de datas para o relatório de auditoria.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELAUDIT.NSP#L104-L109
- nível: `C` — corrigido; o legado assume 1997 como início quando a data não é informada
- AC-016.1: Dada uma consulta sem data inicial, Quando submetida, Então o sistema recusa em vez de varrer a trilha inteira.
- AC-016.2: Dado um intervalo superior ao limite configurado, Quando submetido, Então o sistema recusa e informa o limite.

> Sobre 418 milhões de registros e 311 GB, o padrão de `19970101` transforma um relatório sem parâmetro em varredura total da trilha. A regra 169 registra que o dicionário manda usar o superdescritor `S2` em consultas pesadas, e `RELAUDIT.NSP:111` lê por `DT-EVENT`.

---

## Fora desta especificação

| Item | Motivo |
|---|---|
| Impressão em mainframe e formulário contínuo | O destino não tem impressora lógica; a saída é documento exportável |
| Paginação de 66 linhas | Restrição de dispositivo, não regra de negócio |
| Histograma de eventos por dia | Absorvido pelo `REQ-REP-012`: mesma informação em qualquer meio |
| Relatório de conciliação | Apoia-se nos dados da [especificação 006](../006-conciliacao-bancaria/spec.md) |

---

## Rastreabilidade

| REQ-ID | Regra do catálogo | Mistério associado | Nível |
|---|---|---|---|
| `REQ-REP-001` | 147, 149 | — | `P` |
| `REQ-REP-002` | 147 | `SIFAP-F5-04` | `C` |
| `REQ-REP-003` | 148 | `SIFAP-M-07` | `P` |
| `REQ-REP-004` | 148 | — | `C` |
| `REQ-REP-005` | 149, 159 | — | `C` |
| `REQ-REP-006` | 153 | — | `P` |
| `REQ-REP-007` | 152 | `SIFAP-F5-05` | `C` |
| `REQ-REP-008` | 153 | — | `C` |
| `REQ-REP-009` | 151 | — | `C` |
| `REQ-REP-010` | 143, 167 | `SIFAP-M-18` | `C` |
| `REQ-REP-011` | 144 | — | `C` |
| `REQ-REP-012` | 146 | — | `C` |
| `REQ-REP-013` | 153 | — | `C` |
| `REQ-REP-014` | 148 | — | `P` |
| `REQ-REP-015` | 150 | — | `P` |
| `REQ-REP-016` | 145, 169 | — | `C` |

**Cinco preservações, onze correções, nenhuma sinalização.**

Não há nível `PS` nesta especificação, e a ausência é significativa: `PS` existe para preservar um comportamento que altera quem recebe o quê, expondo o efeito. Relatório não altera nada. Ou a apresentação está correta, ou está errada.

Nenhuma das onze correções muda um valor gravado. Todas mudam o que se vê: a região certa, o total que fecha, o subtotal único, a exclusão visível, o documento protegido.

**Mistérios que permanecem abertos.** `SIFAP-M-07` e `SIFAP-M-18` continuam sem validação humana em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md).

**Dois achados desta leitura.**

O agrupamento regional do consolidado classifica por faixas numéricas que não correspondem ao domínio do campo: toda a folha aparece concentrada em duas regiões desde 1999. Registrado como `SIFAP-F5-04`.

O subtotal por programa do relatório detalhado pressupõe uma ordenação por programa que a leitura, ordenada por período, não produz. Registrado como `SIFAP-F5-05`.
