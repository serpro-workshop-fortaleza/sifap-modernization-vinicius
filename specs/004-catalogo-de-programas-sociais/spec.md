# Especificação — Catálogo de Programas Sociais

> **Fatia de migração:** 3 — Catálogo
> **Destino arquitetural:** contexto delimitado (`socialprogram`)
> **Dados sob responsabilidade:** `SOCPROG` (FNR 151) — ~45 registros de parametrização

| Campo | Valor |
|---|---|
| **Estágio** | Estágio 2 — Especificação |
| **Data** | 2026-09-10 |
| **ADRs aplicáveis** | [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md), [ADR-0007](../../docs/adr/0007-parametrizacao-do-programa-social.md) |
| **Entrada** | [`business-rules-catalog.md`](../../01-archaeology/business-rules-catalog.md), regras 36-47 |
| **Legado de origem** | `CADPROG` |

---

## Contexto

O `SOCPROG` é o menor arquivo do sistema e o mais desproporcional: **45 registros parametrizam 4,2 milhões de beneficiários**, cerca de 93 mil pessoas por programa. Um erro aqui não afeta um beneficiário — afeta um programa inteiro.

A leitura encontrou duas coisas que organizam esta especificação.

### A parametrização existe no banco e o sistema a ignora

O dicionário declara, desde 1997 e 2002, a estrutura completa de cálculo:

| Estrutura | Declarada em | Quem lê |
|---|---|---|
| `DA GRP-CALC-BAND` — 5 faixas de renda com fator e adicional | `SOCPROG.ddm:69` | **ninguém** |
| `FA GRP-REGIONAL-PARAM` — 6 parâmetros regionais com multiplicador | `SOCPROG.ddm:86` | **ninguém** |
| `EA TYPE-DISC-APPLIC` — 8 tipos de desconto aplicáveis | `SOCPROG.ddm:78` | **ninguém** |
| `BG FACTOR-K` — fator de correção especial | `SOCPROG.ddm:47` | **ninguém** |

Enquanto isso, `CALCBENF.NSN:99-137` carrega as mesmas grandezas por `MOVE`, uma a uma, no código-fonte: 27 fatores regionais e 5 fatores de faixa.

O `FACTOR-K` é o caso mais expressivo. O dicionário o protege com um aviso — *"USED IN CALCULATIONS — DO NOT CHANGE WITHOUT AUTHORIZATION FROM BENEFITS COORDINATION (SENARC)"* — e uma varredura por todo o acervo devolve apenas três ocorrências do nome, todas dentro do `CADPROG`, todas referentes a uma variável local. **O campo está vazio desde 2008 e nenhum programa o lê.**

### Não existe alteração de programa social

O `CADPROG` implementa duas operações: incluir e consultar (`CADPROG.NSP:80-83`). Não há alteração, não há inativação, não há encerramento.

Consequência direta: um reajuste anual — que é a razão de existir de um catálogo de parametrização — não tem caminho no sistema. O dicionário prevê `I` e `E` para a situação (`SOCPROG.ddm:37`) e `CADPROG.NSP:134` grava `A` sempre, sem exceção.

## Escopo

**Dentro:** identidade do programa, dados nucleares, situação, parâmetros de elegibilidade consumidos pelo cálculo, faixas de cálculo, parâmetros regionais, consulta e carga.

**Fora:** o algoritmo de cálculo, que pertence à Fatia 4; os tipos de desconto aplicáveis, cujo consumidor é o `CALCDSCT`, da Fatia 4.

---

## Requisitos

### REQ-PRG-001 — Identificar o programa por código único

O sistema DEVE identificar cada programa social por um código único, recusando inclusão cujo código já exista.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm#L28
- nível: `P` — preservado; o dicionário declara `AA COD-PROGRAM A 4 U`
- AC-001.1: Dado um código já cadastrado, Quando uma inclusão for solicitada, Então a operação é recusada.
- AC-001.2: Dado um código inexistente, Quando consultado, Então o resultado indica ausência de registro.

### REQ-PRG-002 — Validar os parâmetros na inclusão

SE algum parâmetro obrigatório do programa estiver ausente ou fora do domínio, ENTÃO o sistema DEVE recusar a inclusão.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP#L96-L106
- nível: `C` — corrigido; o legado grava tudo que a tela devolve, sem uma única verificação
- AC-002.1: Dado um programa sem nome, Quando incluído, Então a operação é recusada.
- AC-002.2: Dado um tipo fora de `A`, `T` e `P`, Quando incluído, Então a operação é recusada.
- AC-002.3: Dado um valor base negativo ou zero, Quando incluído, Então a operação é recusada.

> A ausência de validação aqui vale por 93 mil beneficiários. É a razão de este ser o primeiro requisito de nível `C` da fatia.

### REQ-PRG-003 — Recusar faixa etária incoerente

SE a idade mínima for maior que a idade máxima e ambas forem diferentes de zero, ENTÃO o sistema DEVE recusar a operação.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm#L57
- nível: `C` — corrigido; o legado grava a faixa sem verificar a ordem
- AC-003.1: Dada a faixa de 65 a 18 anos, Quando gravada, Então a operação é recusada.
- AC-003.2: Dada a idade mínima zero, Quando gravada, Então ela é aceita como ausência de limite.

> Zero significa "sem limite", conforme o dicionário. Uma faixa invertida gravada torna **todos** os candidatos inelegíveis, e `VALELEG.NSN:153-169` não tem como distinguir isso de uma regra deliberada.

### REQ-PRG-004 — Preservar o valor base informado

O sistema DEVE gravar como valor base do programa exatamente o valor informado.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP#L125-L130
- nível: `C` — corrigido; o legado grava o valor já multiplicado pelo fator
- AC-004.1: Dado o valor base de R$ 600,00, Quando gravado, Então o valor armazenado é R$ 600,00.
- AC-004.2: Dado o valor base gravado, Quando consultado, Então ele coincide com o informado na inclusão.

> `CADPROG.NSP:125` calcula `#AMT-CALC` e `:130` grava esse resultado no campo de valor base. Quem consulta o programa vê um número que ninguém digitou, e o fator fica aplicado duas vezes quando o cálculo o aplica de novo.

### REQ-PRG-005 — Manter o fator de ajuste como parâmetro nomeado

O sistema DEVE manter o coeficiente do fator de ajuste como parâmetro nomeado e configurável, e não como constante embutida.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP#L124
- nível: `P` — preservado; decisão do `SIFAP-M-04`, registrada no [ADR-0007](../../docs/adr/0007-parametrizacao-do-programa-social.md)
- AC-005.1: Dado o coeficiente vigente, Quando um programa for incluído, Então o fator derivado reproduz o resultado do legado.
- AC-005.2: Dado o coeficiente, Quando consultado, Então sua origem e sua data de vigência estão registradas.

> A constante `0.347215` não tem origem documentada em nenhuma fonte do acervo. O valor é preservado; o que muda é deixar de estar escondido dentro de um `COMPUTE`.

### REQ-PRG-006 — Permitir alteração de programa social

O sistema DEVE permitir alterar nome, valor base, parâmetros de elegibilidade e fator de ajuste de um programa existente.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP#L80-L83
- nível: `C` — corrigido; o legado implementa apenas inclusão e consulta
- AC-006.1: Dado um programa existente, Quando seu valor base for alterado, Então a alteração é gravada.
- AC-006.2: Dada uma alteração, Quando concluída, Então o evento registra os campos modificados com valor anterior e posterior.

> Um catálogo de parametrização sem alteração não comporta reajuste anual, que é a operação que dá razão à sua existência. O campo `BE PCT-ANNUAL-ADJUST` está no dicionário desde 1997 e nunca teve como ser usado.

### REQ-PRG-007 — Permitir inativar e encerrar programa

O sistema DEVE permitir alterar a situação do programa para inativo ou encerrado, com motivo registrado.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm#L37
- nível: `C` — corrigido; `CADPROG.NSP:134` grava `A` sempre
- AC-007.1: Dado um programa ativo, Quando encerrado, Então sua situação passa a encerrada e a data de encerramento é registrada.
- AC-007.2: Dado um programa inativo, Quando a elegibilidade for apurada, Então nenhum beneficiário é considerado elegível por ele.

> O dicionário declara `I` e `E` desde 1997 e nenhum programa os atribui. `VALELEG.NSN:114-118` implementa a recusa por programa inativo — uma regra que hoje nunca é acionada, porque a situação nunca deixa de ser `A`.

### REQ-PRG-008 — Manter a situação inicial ativa

QUANDO um programa social for incluído, o sistema DEVE atribuir a situação ativa.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP#L134
- nível: `P` — preservado
- AC-008.1: Dado um programa incluído, Quando consultado, Então sua situação é ativa.

### REQ-PRG-009 — Expor os parâmetros consumidos pelo cálculo

O sistema DEVE expor, por código de programa, o tipo, o valor base, o fator de ajuste, a situação, o código de elegibilidade, a faixa etária e o teto de renda.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN#L106-L111
- nível: `P` — preservado; é o conjunto que `VALELEG` e `CALCBENF` leem hoje
- AC-009.1: Dado um código de programa, Quando consultado pelo cálculo, Então todos esses campos são retornados.
- AC-009.2: Dado um código inexistente, Quando consultado, Então o resultado indica ausência, sem lançar erro.

### REQ-PRG-010 — Manter as faixas de cálculo como dado

O sistema DEVE manter as faixas de cálculo do programa, com limite inferior, limite superior, fator multiplicador e valor adicional.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm#L69
- nível: `C` — corrigido; a estrutura existe no dicionário e nenhum programa a mantém ou a lê
- AC-010.1: Dado um programa com faixas informadas, Quando consultado, Então as faixas são retornadas em ordem de renda.
- AC-010.2: Dadas duas faixas que se sobrepõem, Quando gravadas, Então a operação é recusada.

> `CALCBENF.NSN:129-137` carrega cinco fatores de faixa por `MOVE` no fonte. A estrutura equivalente está no dicionário desde 1997, vazia.

### REQ-PRG-011 — Manter os parâmetros regionais como dado

O sistema DEVE manter, por programa e por região, o multiplicador regional, o complemento fixo e o indicador de vigência.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm#L86
- nível: `C` — corrigido; a estrutura existe e nenhum programa a lê
- AC-011.1: Dado um programa com parâmetros regionais informados, Quando consultado, Então eles são retornados por código de região.
- AC-011.2: Dado um código de região fora do domínio, Quando gravado, Então a operação é recusada.

> `CALCBENF.NSN:99-125` carrega 27 fatores rotulados por unidade federativa e os indexa por um código de região que vale de 1 a 5. Os rótulos não correspondem ao que o índice significa, e a estrutura correta está no dicionário sem uso. A correção do índice pertence à Fatia 4; o que esta fatia entrega é o lugar certo para o dado morar.

### REQ-PRG-012 — Consultar programas por situação

O sistema DEVE permitir consultar programas por situação e por tipo.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm#L104-L105
- nível: `P` — preservado; o dicionário declara o superdescritor `S2 SUPER-TYPE-STAT`
- AC-012.1: Dada a consulta por situação ativa, Quando executada, Então retorna apenas os programas ativos.

### REQ-PRG-013 — Registrar autor e instante de cada operação

QUANDO um programa social for criado ou alterado, o sistema DEVE registrar o autor e o instante da operação.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm#L95-L96
- nível: `C` — corrigido; os campos existem no dicionário e nenhum programa os preenche
- AC-013.1: Dado um programa incluído, Quando consultado, Então constam o autor e o instante da criação.

### REQ-PRG-014 — Registrar toda operação na trilha de auditoria

QUANDO um programa social for criado, alterado ou tiver a situação modificada, o sistema DEVE publicar o evento correspondente.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP#L141-L147
- nível: `P` — preservado para a inclusão; estendido às operações que o `REQ-PRG-006` e o `REQ-PRG-007` criam
- AC-014.1: Dado um programa incluído, Quando a operação concluir, Então existe evento de inclusão na trilha.
- AC-014.2: Dado um evento de programa social, Quando consultado, Então ele não carrega CPF afetado.

> `CADPROG.NSP:144` faz `RESET #AUD-CPF`: o evento não tem sujeito pessoal, por não ser sobre pessoa. O enquadramento é preservado.

### REQ-PRG-015 — Inventariar a parametrização preenchida na carga

QUANDO a carga inicial executar, o sistema DEVE inventariar quais campos de parametrização estão preenchidos na origem.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm#L47
- nível: `C` — corrigido; nenhuma fonte do acervo permite saber o que há nos 45 registros
- AC-015.1: Dado o fim da carga, Quando o relatório for gerado, Então ele informa quantos programas têm faixas de cálculo preenchidas.
- AC-015.2: Dado o fim da carga, Quando o relatório for gerado, Então ele informa quantos programas têm fator de correção especial preenchido.
- AC-015.3: Dado um programa cujo valor base esteja ajustado pelo fator, Quando migrado, Então a ocorrência é sinalizada para revisão.

> [!WARNING]
> O `REQ-PRG-004` corrige a gravação do valor ajustado, mas os registros existentes **já foram gravados assim**. Reverter o cálculo exigiria conhecer o valor original, que não está em lugar nenhum. A carga sinaliza; a decisão é de negócio.

---

## Fora de escopo

| Item | Razão | Destino |
|---|---|---|
| Algoritmo de cálculo do benefício | Pertence ao contexto Pagamento | Fatia 4 |
| Tipos de desconto aplicáveis (`EA`) | Consumidor é o `CALCDSCT` | Fatia 4 |
| Correção do índice da tabela regional | Defeito do cálculo, não do catálogo | Fatia 4 |
| Significado do código de elegibilidade | Ticket 4471/2012 ausente do acervo | Preservado como texto; interpretação na Fatia 4 |
| Campos sem consumidor no acervo | `ACRONYM`, `RESPONSIBLE-AGENCY`, `CREATION-LAW`, `AMT-BASE-FAMILY`, `AMT-CEILING-BENEF`, `AMT-FLOOR-BENEF` | Preservados na carga, sem interface de manutenção |
| Indicadores de condicionalidade (`CD`–`CI`) | Nenhum programa os lê; a condicionalidade não é verificada em lugar algum | Fatia futura |
| Exclusão de programa social | Não existe no legado | Não será implementada |

---

## Rastreabilidade

| REQ-ID | Regra do catálogo | Mistério associado | Nível |
|---|---|---|---|
| `REQ-PRG-001` | 38 | — | `P` |
| `REQ-PRG-002` | 44 | — | `C` |
| `REQ-PRG-003` | 44 | — | `C` |
| `REQ-PRG-004` | 40 | `SIFAP-M-04` | `C` |
| `REQ-PRG-005` | 39 | `SIFAP-M-04` | `P` |
| `REQ-PRG-006` | 36 | — | `C` |
| `REQ-PRG-007` | 42 | — | `C` |
| `REQ-PRG-008` | 42 | — | `P` |
| `REQ-PRG-009` | 37, 96 | — | `P` |
| `REQ-PRG-010` | 45 | — | `C` |
| `REQ-PRG-011` | 68 | — | `C` |
| `REQ-PRG-012` | — | — | `P` |
| `REQ-PRG-013` | — | — | `C` |
| `REQ-PRG-014` | 43 | — | `P` |
| `REQ-PRG-015` | 41 | `SIFAP-M-04` | `C` |

**Cinco preservações, dez correções, nenhuma sinalização.** A proporção é a mais alta das três fatias, e tem explicação: quase todo achado aqui é **ausência** — validação que não existe, operação que não existe, campo que ninguém preenche. Ausência não se preserva.

Nenhuma correção altera valor de benefício. O `REQ-PRG-004` chega perto — corrige o valor gravado —, mas preserva o coeficiente e o resultado do cálculo, conforme o `REQ-PRG-005` e o [ADR-0007](../../docs/adr/0007-parametrizacao-do-programa-social.md).

**Mistério que permanece aberto.** O `SIFAP-M-04` continua sem validação humana em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md).

**Dois achados novos desta leitura.**

O campo `BG FACTOR-K` não é lido nem escrito por nenhum programa do acervo, apesar do aviso de autorização SENARC no dicionário. A varredura devolve três ocorrências do nome, todas de uma variável local do `CADPROG`.

As faixas de cálculo e os parâmetros regionais existem no dicionário desde 1997 e 2002, vazios, enquanto `CALCBENF.NSN:99-137` carrega as mesmas grandezas por `MOVE` no código-fonte.

---

## Definição de pronto

- [x] Todo requisito usa um padrão EARS com `DEVE`.
- [x] Todo REQ-ID é único e declarado como título.
- [x] Todo REQ-ID tem `source_legacy:` apontando para arquivo existente.
- [x] Todo requisito tem critérios de aceitação em Dado/Quando/Então.
- [x] Cada requisito declara o nível de tratamento do [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md).
- [x] `plan.md` e `tasks.md` gerados.
