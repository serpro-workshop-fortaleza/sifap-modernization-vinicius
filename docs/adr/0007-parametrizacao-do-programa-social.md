# ADR-0007: Tratar a parametrização do programa social como dado, e não como código

| Campo | Valor |
|---|---|
| **Status** | Aceita |
| **Data** | 2026-09-10 |
| **Feature relacionada** | `specs/004-catalogo-de-programas-sociais/` (Fatia 3) |
| **Mistério tratado** | `SIFAP-M-04` |

---

## Contexto

O `SOCPROG` existe para parametrizar o cálculo do benefício. É um arquivo pequeno — 45 registros — cuja única função é dizer ao sistema quanto cada programa paga e a quem.

A leitura do Estágio 2 encontrou o contrário disso.

### A parametrização mora no código

| Grandeza | Onde o dicionário a declara | Onde o sistema a lê |
|---|---|---|
| Faixas de renda com fator | `SOCPROG.ddm:69-75`, grupo periódico de 5 | `CALCBENF.NSN:129-137`, cinco `MOVE` no fonte |
| Multiplicador regional | `SOCPROG.ddm:86-93`, grupo periódico de 6 | `CALCBENF.NSN:99-125`, 27 `MOVE` no fonte |
| Fator de correção especial | `SOCPROG.ddm:47`, campo `BG FACTOR-K` | em lugar nenhum |

Uma varredura por `FACTOR-K` em todo o acervo devolve três ocorrências, todas dentro do `CADPROG` e todas referentes à variável local `#FACTOR-K`. **O campo do banco nunca é lido nem escrito.**

O dicionário, porém, o protege:

```text
* NOTE: FACTOR-K USED IN CALCULATIONS - DO NOT CHANGE WITHOUT
*       AUTHORIZATION FROM BENEFITS COORDINATION (SENARC)
```

E registra sua origem:

```text
   1 BG FACTOR-K   P  5,4  N   SPECIAL CORRECTION FACTOR
*                              >>> UNDOCUMENTED <<<
*                              INSERTED AUG/2008 BY ADILSON
*                              "FULFILLS SENARC REQUEST"
*                              NO FURTHER DETAILS IN THE TICKET
```

Um campo criado para atender a um pedido da coordenação de benefícios, protegido por aviso de autorização, vazio há dezoito anos.

### A constante que ninguém explica

O `CADPROG` calcula um fator próprio, com outro nome e outra finalidade:

```text
COMPUTE #FACTOR-K = 1.00 + (#FACTOR-ADJUST * 0.347215)
COMPUTE #AMT-CALC = #AMT-BASE * #FACTOR-K
```

Fonte: [`CADPROG.NSP:124-125`](../../01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP).

O `0.347215` é o `SIFAP-M-04`. Não tem origem em nenhuma fonte do acervo: nem no cabeçalho do programa, nem no dicionário, nem no levantamento de 2012. O ticket citado na alteração de 05/07/2003 não consta.

E o resultado, `#AMT-CALC`, é gravado **no campo de valor base** (`:130`). Quem consulta o programa vê um número que ninguém digitou.

---

## Opções consideradas

### Opção 1: Preservar tudo como está

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Fidelidade máxima; nenhuma diferença de valor a explicar |
| **Desvantagens** | Mantém a parametrização no código, de modo que um reajuste exige recompilação; mantém o valor base gravado como número derivado, sem meio de recuperar o informado; mantém um campo protegido por aviso normativo permanentemente vazio |

### Opção 2: Migrar a parametrização para dado e recalcular os valores base

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Corrige o valor base gravado, devolvendo o número originalmente informado |
| **Desvantagens** | **Impossível.** Recuperar o valor informado exige dividir pelo fator vigente na data da inclusão, e nem o fator histórico nem a data da alteração estão registrados. Além disso, alterar o valor base altera o benefício pago |

### Opção 3: Migrar a parametrização para dado, preservando o coeficiente e os valores existentes

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Faixas e parâmetros regionais passam a viver onde o dicionário sempre previu; o coeficiente vira parâmetro nomeado com data de vigência; inclusões novas gravam o valor informado; nenhum valor existente é alterado |
| **Desvantagens** | Convivem dois conjuntos de registros — os migrados, com valor ajustado, e os novos, com valor informado; a distinção precisa ficar visível |

---

## Decisão

**Adotada a opção 3.**

1. **O coeficiente `0.347215` é preservado**, como parâmetro nomeado com data de vigência e origem declarada como desconhecida. O `REQ-PRG-005` exige que o fator derivado reproduza o resultado do legado.

2. **O valor base passa a ser gravado como informado** (`REQ-PRG-004`). O fator de ajuste continua a existir como campo próprio, aplicado pelo cálculo, e não pela gravação.

3. **Nenhum valor existente é recalculado.** A carga sinaliza os registros cujo valor base foi gravado ajustado (`REQ-PRG-015`), e a decisão sobre eles é de negócio.

4. **Faixas de cálculo e parâmetros regionais passam a ser dado** (`REQ-PRG-010`, `REQ-PRG-011`), na estrutura que o dicionário declara desde 1997 e 2002.

5. **O campo `FACTOR-K` não é modelado.** Não há consumidor, não há valor, não há documentação. Modelá-lo seria preservar a intenção de alguém em 2008 sem saber qual era.

### Por que isto não viola o ADR-0003

O [ADR-0003](0003-preservacao-de-comportamento.md) determina preservar o comportamento observável e só corrigir defeito de integridade.

**O valor pago não muda.** O coeficiente é o mesmo, o fator de ajuste é o mesmo, e os registros existentes mantêm o valor que têm hoje. O que muda é onde o número mora e o que a tela de consulta mostra para inclusões novas.

Gravar em um campo chamado "valor base" um número que não é o valor base é defeito de integridade pelo primeiro critério do ADR-0003: grava dado fora do domínio declarado. O dicionário descreve `BA AMT-BASE-INDIVIDUAL` como *"monthly base amount per person"*, não como "valor base multiplicado por um fator".

---

## Consequências

### Positivas

- Um reajuste anual passa a ser alteração de dado, e não recompilação. O campo `BE PCT-ANNUAL-ADJUST` existe no dicionário desde 1997 e nunca teve como ser usado.
- A tabela regional deixa de ser 27 posições rotuladas por unidade federativa indexadas por um código que vale de 1 a 5.
- O coeficiente sai de dentro de um `COMPUTE` e passa a ser visível, versionado e questionável.
- Consultar um programa passa a mostrar o valor que foi informado.

### Negativas

- Registros migrados e registros novos convivem com semânticas diferentes no campo de valor base. O `REQ-PRG-015` torna a diferença visível; ela não desaparece.
- As faixas e os parâmetros regionais nascem vazios, porque a origem está vazia. Preenchê-los com os valores hoje codificados no `CALCBENF` é decisão da Fatia 4, que é quem os consome.
- O `SIFAP-M-04` permanece aberto. Preservar um coeficiente sem origem conhecida é a única alternativa a inventar uma.

### Riscos

| Risco | Mitigação |
|---|---|
| A Fatia 4 decide não usar a parametrização em dado | A estrutura fica vazia e sem custo; a decisão é dela, e o `REQ-PRG-011` já registra que a correção do índice não é desta fatia |
| Validação humana do `M-04` revela outro coeficiente | Parâmetro nomeado com data de vigência; trocar é alterar um registro, não recompilar |
| Alguém interpreta o valor base migrado como valor informado | `REQ-PRG-015` sinaliza cada ocorrência na carga |

> [!WARNING]
> **Nenhum valor base existente é recalculado pela migração.** Reverter o ajuste exigiria conhecer o coeficiente vigente na data de cada inclusão, e essa data não é registrada. Alterar valor base altera benefício pago.

---

## Reversibilidade

Alta. O coeficiente é um registro; as faixas e os parâmetros regionais são tabelas que nascem vazias e não afetam o cálculo enquanto a Fatia 4 não as consumir.

O único ponto irreversível é a carga, e ela foi desenhada para não decidir nada: sinaliza e preserva.

O `SIFAP-M-04` permanece **aguardando validação humana** em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md). Esta decisão define como proceder até que ela venha.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [ADR-0006](0006-situacao-cadastral-do-beneficiario.md)<br/><sub>Situação cadastral do beneficiário.</sub> | [spec 004](../../specs/004-catalogo-de-programas-sociais/spec.md)<br/><sub>Catálogo de Programas Sociais.</sub> |
