# ADR-0008: Preservar o cálculo do benefício e corrigir apenas os defeitos de integridade

| Campo | Valor |
|---|---|
| **Status** | Aceita |
| **Data** | 2026-09-11 |
| **Feature relacionada** | `specs/005-processamento-de-folha/` (Fatia 4) |
| **Mistérios tratados** | `SIFAP-M-05`, `M-08`, `M-09`, `M-10`, `M-11`, `M-12`, `M-13` |

---

## Contexto

Esta fatia decide quanto 4,2 milhões de pessoas recebem por mês. É o ponto do projeto em que o [ADR-0003](0003-preservacao-de-comportamento.md) deixa de ser uma diretriz confortável e passa a ser uma restrição dura.

A leitura dos cinco programas encontrou defeitos de três naturezas distintas, e tratá-los da mesma forma seria um erro.

### Natureza 1: o cálculo produz um número que a norma não confirma

A `RN-013` documenta fórmula aditiva. O código roda multiplicativa:

```text
COMPUTE #AMT-BENF = #AMT-BASE * #FACTOR-REGION * #FACTOR-FAMILY
                     * #FACTOR-INCOME * #FACTOR-AGE
```

Fonte: [`CALCBENF.NSN:255-258`](../../01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN).

O mesmo vale para a contribuição social (`SIFAP-M-10`), a base de renda (`SIFAP-M-11`), o índice de correção (`SIFAP-M-12`) e a dispensa por região (`SIFAP-M-13`).

Nenhuma dessas questões é respondível pelo código. O legado diz o que acontece; só quem tem autoridade sobre a norma diz o que deveria acontecer.

### Natureza 2: o sistema grava dado que não fecha consigo mesmo

O ciclo grava **dois** pagamentos por beneficiário: `CALCBENF.NSN:319` persiste um e `BATCHPGT.NSP:488` persiste outro. O primeiro nunca recebe `NUM-PAYMENT`, sobre um campo que `PAYMENT.ddm:29` declara único.

O `CALCDSCT` atualiza o total de descontos e não recalcula o líquido, de modo que `AMT-NET` — o valor que segue para o banco — fica defasado em relação a `AMT-DISC-TOTAL`.

O tipo de pagamento gravado em dezembro é `D`, valor que `PAYMENT.ddm:74` não declara.

### Natureza 3: o resultado de um beneficiário depende de outro

No `BATCHPGT`, uma renda acima da última faixa faz o laço de `DET-INCOME-BAND-BATCH` terminar sem atribuir `#FACTOR-INCOME`. A variável mantém o valor do beneficiário processado imediatamente antes.

Dois beneficiários com exatamente os mesmos dados recebem valores diferentes conforme a ordem de leitura do arquivo.

---

## Decisão

**Os três tipos recebem tratamentos diferentes.**

### Natureza 1 — preservada

Toda regra que produz um valor sem defeito interno é replicada exatamente, em nível `P` ou `PS`:

| Regra | Nível | Requisito |
|---|---|---|
| Fórmula multiplicativa de cinco fatores | `P` | `REQ-PAY-010` |
| Contribuição de 3% acima de R$ 500,00 | `P` | `REQ-PAY-017` |
| Truncamento em duas casas | `P` | `REQ-PAY-014` |
| Décimo terceiro pelo fator etário | `P` | `REQ-PAY-015` |
| Renda familiar contra teto per capita | `PS` | `REQ-PAY-007` |
| Dispensa de elegibilidade por região especial | `PS` | `REQ-PAY-008` |
| Índice de correção de mês único | `PS` | `REQ-PAY-021` |

O que muda nos `PS` é a visibilidade: cada aplicação passa a ser registrada e contável. Hoje ninguém sabe quantos beneficiários recebem por dispensa regional, nem qual seria a diferença se a renda per capita fosse usada.

### Natureza 2 — corrigida

Defeito de integridade não se preserva. Os três critérios do ADR-0003 estão presentes:

| Defeito | Critério violado | Requisito |
|---|---|---|
| Dois pagamentos por ciclo | duplica registro | `REQ-PAY-001` |
| Pagamento sem número | impede rastreabilidade | `REQ-PAY-002` |
| Tipo de pagamento `D` | grava fora do domínio declarado | `REQ-PAY-016` |
| Líquido não recalculado | grava dado que não fecha | `REQ-PAY-020` |
| Cinco tipos de desconto ignorados | descarta dado sem registro | `REQ-PAY-018` |
| Teto reduzindo desconto judicial | contraria a própria regra do programa | `REQ-PAY-019` |

### Natureza 3 — corrigida

O vazamento de fator entre beneficiários (`REQ-PAY-012`) é o defeito mais grave encontrado no projeto, porque quebra a propriedade mais básica de um cálculo: **os mesmos insumos produzem o mesmo resultado**.

---

## Os dois casos de fronteira

Duas correções alteram o valor calculado. Elas merecem tratamento explícito porque a regra geral seria preservá-las.

### Aplicação dupla do fator de ajuste

`CADPROG.NSP:125` grava o valor base já multiplicado por `1 + (FACTOR-ADJUST × 0.347215)`. `CALCBENF.NSN:261` lê esse valor e multiplica de novo por `1 + FACTOR-ADJUST`.

São dois fatores diferentes, derivados do mesmo campo, aplicados por programas diferentes. Um deles usa um coeficiente cuja origem ninguém conhece.

**Decisão: corrigir.** O `REQ-PRG-004` já removeu a aplicação na gravação; o `REQ-PAY-013` mantém a aplicação no cálculo. O fator passa a incidir uma vez.

**Por que isto é integridade, e não regra:** o campo `AMT-BASE-INDIVIDUAL` é declarado no dicionário como *"monthly base amount per person"*. Gravar nele um valor já ajustado é gravar fora do domínio declarado — o primeiro critério do ADR-0003. A dupla aplicação é consequência, não causa.

> [!WARNING]
> **O valor calculado muda para programas com fator de ajuste diferente de zero.** O `REQ-PRG-015` mede quantos são antes de qualquer decisão de carga.

### Fator regional de tabela mal indexada

`CALCBENF.NSN:99-125` carrega 27 fatores rotulados por unidade federativa e os indexa por `COD-REGION`, que `BENEFIC.ddm:67` declara como `01`–`05` ou `99`.

Só os índices 1 a 5 são alcançáveis, e eles contêm Acre, Amazonas, Amapá, Pará e Rondônia. Todas as macrorregiões recebem multiplicadores do Norte.

**Decisão: mover o fator para a parametrização, preservando os valores.** O `REQ-PAY-011` obriga o cálculo a ler `regional_parameter`, criado pela Fatia 3. A carga preenche esses parâmetros com **exatamente os fatores que a tabela legada produz hoje** — `01 → 1,3500`, `02 → 1,3200`, `03 → 1,3000`, `04 → 1,2800`, `05 → 1,3100`.

**O valor pago não muda.** O que muda é que o fator passa a ser um dado visível, alterável por quem tem autoridade, em vez de uma constante compilada cujo rótulo contradiz o uso.

Corrigir a correspondência entre região e fator é decisão de negócio, e fica registrada como pendente.

---

## Consequências

### Positivas

- O ciclo passa a gerar um pagamento por beneficiário, com número único de sequência do banco.
- O resultado deixa de depender da ordem de processamento.
- Cada fator aplicado é registrado, o que torna a divergência com a `RN-013` mensurável em vez de discutível.
- Os cinco tipos de desconto hoje ignorados passam a ser tratados ou recusados explicitamente.
- A folha ganha um evento por pagamento, o que a `REQ-AUD-010` exige desde a Fatia 1 e nenhum publicador entregava.

### Negativas

- Programas com fator de ajuste diferente de zero terão valor calculado diferente do atual.
- Beneficiários com renda acima da última faixa terão valor diferente, porque hoje o valor deles é acidental.
- A quantidade de pagamentos cai pela metade na primeira folha após a migração, e isso precisa ser explicado antes de acontecer.

### Riscos

| Risco | Mitigação |
|---|---|
| A validação humana do `M-09` confirma a fórmula aditiva | A fórmula fica isolada em um componente próprio, com cada fator registrado; trocar é localizado |
| Alguém interpreta a queda no número de pagamentos como falha | Documentado aqui e verificado por teste: um pagamento por beneficiário é o comportamento correto |
| A correção do fator de ajuste é vista como alteração de benefício | É consequência de uma correção de integridade na Fatia 3; o `REQ-PRG-015` mede o alcance antes |
| Não há ambiente legado para caracterização | Limitação registrada desde a Fatia 1; pesa mais aqui do que em qualquer outro ponto |

> [!WARNING]
> **Nenhum pagamento existente é recalculado.** Esta decisão vale para ciclos novos. Reprocessar histórico com regras diferentes das vigentes à época produziria valores que nunca foram devidos.

---

## O que esta decisão não faz

**Não escolhe entre fórmula multiplicativa e aditiva.** Replica a que roda.

**Não corrige a correspondência entre código de região e fator regional.** Move o dado para onde ele pode ser corrigido, e registra que a correção é de negócio.

**Não troca renda familiar por per capita.** Preserva e mede.

**Não decide o destino dos pagamentos duplicados já gravados.** São cerca de metade dos 180 milhões de registros, e o que fazer com eles é decisão da Fatia 5, junto da conciliação.

---

## Reversibilidade

Média. As correções de integridade são estruturais e não se pretende revertê-las.

As preservações estão isoladas em componentes próprios — um por fator — justamente para que a validação humana, quando vier, altere um ponto e não o cálculo inteiro.

O irreversível é o que for pago. É por isso que nada aqui recalcula histórico.

Os sete mistérios permanecem **aguardando validação humana** em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md).

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [ADR-0007](0007-parametrizacao-do-programa-social.md)<br/><sub>Parametrização do programa social.</sub> | [spec 005](../../specs/005-processamento-de-folha/spec.md)<br/><sub>Processamento de Folha.</sub> |
