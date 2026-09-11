# ADR-0010: Relatórios são projeções de leitura, e apresentação errada é defeito

| Campo | Valor |
|---|---|
| **Status** | Aceita |
| **Data** | 2026-09-11 |
| **Feature relacionada** | `specs/007-relatorios/` (Fatia 5) |
| **Mistérios tratados** | `SIFAP-M-07`, `SIFAP-M-18`, `SIFAP-F5-04`, `SIFAP-F5-05` |

---

## Contexto

O [ADR-0003](0003-preservacao-de-comportamento.md) estabeleceu a regra que governa o projeto: preservar o comportamento observável, corrigir apenas defeitos de integridade. A distinção entre as duas categorias tem sido, até aqui, a pergunta *"isto altera quanto alguém recebe?"*.

Relatório não altera nada. Aplicar a mesma pergunta a esta fatia daria sempre a mesma resposta — não — e a regra perderia poder de discriminação justamente onde ela é mais necessária.

A leitura dos três programas encontrou defeitos que ninguém classificaria como regra de negócio:

- O consolidado agrupa por região usando faixas numéricas que não correspondem ao domínio do campo. Desde 1999, o relatório distribuído à SENARC mostra a folha concentrada em duas regiões.
- Dentro da mesma linha impressa, bruto menos desconto não dá líquido, porque apenas o bruto é arredondado.
- O subtotal por programa pressupõe uma ordenação por programa que a leitura, ordenada por período, não produz.
- O total geral do relatório detalhado desaparece sempre que existem pagamentos posteriores ao período consultado.
- O relatório de auditoria filtra as exclusões incondicionalmente, e o próprio dicionário documenta o contorno: consultar pelo Adabas Online.

Nenhum desses é regra. Todos são erro de apresentação. Mas a regra do ADR-0003, aplicada literalmente, não os alcança.

---

## Decisão

**Para relatórios, o critério de preservação passa a ser a *intenção declarada* da apresentação, e não o resultado que ela produz.**

Quando o programa declara que agrupa por região e não agrupa, isso é defeito, não comportamento. Quando declara um subtotal por programa e produz vários parciais, isso é defeito. A pergunta deixa de ser *"isto altera um valor?"* e passa a ser *"isto apresenta o que diz apresentar?"*.

Três consequências.

### 1. Não existe nível `PS` nesta fatia

`PS` — preservar com sinalização — existe para comportamento que altera quem recebe o quê. Serve quando é preciso manter o efeito e tornar sua incidência contável, porque a decisão de mudá-lo não é técnica.

Relatório não tem esse problema. Ou a apresentação corresponde ao que declara, ou não corresponde. A ausência de `PS` nas dezesseis linhas do `REQ-REP` é resultado do critério, não descuido.

### 2. O arredondamento do consolidado permanece

`SIFAP-M-07`, nível `P`. É a exceção que confirma o critério: o `BATCHREL` declara um consolidado arredondado e produz um consolidado arredondado. A divergência contra a base truncada é consequência conhecida da própria intenção, e há dez anos de relatórios arquivados construídos sobre ela — retenção de dez anos pela Lei 8.159, art. 14.

O que muda é que o critério passa a valer para **todos** os totais da mesma linha (`REQ-REP-004`), e que o relatório declara a divergência em vez de deixá-la implícita (`AC-003.2`).

### 3. O relatório não recalcula

`REQ-REP-014`. A projeção lê o que a folha gravou. Isso já valia de fato no legado e passa a valer por construção: o contexto `report` não tem escrita, não tem repositório de agregado e não conhece o calculador.

É o que permite responder com segurança a uma pergunta que o legado não responde: se o relatório diverge da base, a divergência está na apresentação, nunca no cálculo.

### Sobre a omissão das exclusões

`SIFAP-M-18` decidido como `C`, conforme a política do Estágio 1 já previa. O `REQ-REP-010` realiza o `REQ-AUD-011`, especificado na Fatia 1 e sem implementação até agora porque não havia relatório onde implementá-lo.

O filtro não desaparece: vira opção de quem consulta. A diferença entre esconder por padrão e permitir esconder por escolha é a diferença entre um relatório de auditoria e um relatório que se parece com um.

---

## Alternativas consideradas

### Tratar todo defeito de apresentação como comportamento a preservar

Descartada. Levaria a reproduzir deliberadamente o agrupamento regional incorreto, e a única justificativa possível seria *"é assim que sempre foi"* — que é exatamente o raciocínio que o ADR-0003 existe para disciplinar, não para autorizar.

### Reproduzir o agrupamento incorreto e sinalizar

Descartada. Seria `PS` aplicado a algo que não altera valor. Produziria um relatório que mostra a distribuição errada acompanhada de uma nota dizendo que está errada — pior que qualquer das duas alternativas puras.

### Recalcular os consolidados históricos

Fora de escopo. Os relatórios arquivados são documentos produzidos em uma data, sob um critério vigente. Reproduzi-los com o critério novo criaria dois documentos incompatíveis para o mesmo período, e o arquivado tem valor legal.

O `REQ-REP-015` preserva a cópia arquivável; o que muda é o formato, hoje ilegível porque `COMPRESS` concatena os campos sem delimitador.

---

## Consequências

### Positivas

- O consolidado regional passa a corresponder ao que declara, pela primeira vez desde 1999.
- Bruto menos desconto passa a dar líquido dentro da mesma linha.
- As exclusões aparecem no relatório de auditoria, e o contorno pelo Adabas Online deixa de ser necessário.
- O documento do titular deixa de ser reconstituível a partir do relatório impresso.
- Tela e documento exportado passam a mostrar o mesmo conteúdo.

### Negativas

- Os totais por região do primeiro relatório novo **não** baterão com os do último relatório antigo. A diferença é a correção, mas quem recebe o documento precisa ser avisado antes, não depois.
- A exigência de intervalo explícito no relatório de auditoria (`REQ-REP-016`) recusa consultas que hoje funcionam — funcionam varrendo 311 GB, mas funcionam.

### Neutras

- A quantidade de linhas classificadas como "não classificado" passa a ser visível. Não é um problema novo; é um problema que estava somado a uma região real.

---

## Reversibilidade

| Decisão | Como reverter |
|---|---|
| Agrupamento por código de região | Trocar a função de agrupamento; ponto único na projeção |
| Arredondamento uniforme | Voltar a arredondar apenas o bruto |
| Exclusões visíveis | Reativar o filtro padrão; é configuração |
| Máscara do documento | Já centralizada no kernel desde a Fatia 2 |
| Intervalo obrigatório | Restabelecer o padrão de 1997 |

Todas as reversões são locais. É a propriedade que decorre de o contexto `report` não escrever nada: nenhuma decisão desta fatia deixa resíduo em dado gravado.
