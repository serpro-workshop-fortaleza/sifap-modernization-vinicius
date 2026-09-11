# ADR-0009: Tratar a conciliação como máquina de estados do pagamento, não como atualização em massa

| Campo | Valor |
|---|---|
| **Status** | Aceita |
| **Data** | 2026-09-11 |
| **Feature relacionada** | `specs/006-conciliacao-bancaria/` (Fatia 5) |
| **Mistérios tratados** | `SIFAP-M-08`, `SIFAP-M-19`, `SIFAP-F5-01`, `SIFAP-F5-02`, `SIFAP-F5-03` |

---

## Contexto

A conciliação é o único ponto do sistema em que informação **externa** altera o estado de um pagamento. É também o último elo de uma cadeia: calcular, descontar, corrigir, remeter, e só então saber se o dinheiro chegou.

O `BATCHCON` trata esse elo como uma sequência de atualizações independentes. Lê um registro do arquivo do banco, procura o pagamento, e grava a nova situação sem consultar a anterior:

```text
FIND PAYMENT-V WITH NUM-PAYMENT = #NUM-PAYMENT
  MOVE 'P' TO PAYMENT-V.STAT-PAYMENT
  MOVE #DT-PAYMENT-WK TO PAYMENT-V.DT-CREDIT
  UPDATE PAYMENT-V
  END TRANSACTION
END-FIND
```

Fonte: [`BATCHCON.NSP:205-213`](../../01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP).

Dessa escolha decorrem três problemas que a leitura encontrou, e um quarto que a Fatia 4 já havia encontrado por outro caminho.

### O pagamento sem número é inalcançável

A busca é por `NUM-PAYMENT`. Os pagamentos que `CALCBENF.NSN:319` grava não têm esse campo. Não é que a conciliação falhe neles — ela nunca os visita. Permanecem na situação `G` indefinidamente, que é literalmente o impacto que o `SIFAP-M-08` registra.

### O sucesso é contado antes de ser verificado

`ADD 1 TO #QTY-RECONCILED` precede o `DECIDE` que classifica o código de retorno. O ramo `NONE` escreve uma linha no log e não atualiza nada. O total de conciliados inclui registros que nenhum ramo tratou.

### A divergência não deixa rastro no pagamento

Uma diferença de valor acima de um centavo gera evento de auditoria e mantém o pagamento como estava. Listar os divergentes de um período exige varrer 418 milhões de eventos.

### Nada impede reprocessar o mesmo arquivo

O programa não registra qual arquivo consumiu. Como as atualizações são idempotentes por coincidência — a situação final é a mesma —, uma segunda execução passa despercebida, exceto pela trilha, que dobra.

---

## Decisão

**A situação do pagamento passa a ser uma máquina de estados explícita, e a conciliação é a única operação autorizada a aplicar as transições que dependem do banco.**

Quatro consequências diretas:

### 1. A transição é validada contra a situação atual

Um pagamento só aceita confirmação se estiver remetido. Um pagamento cancelado não aceita retorno. Um segundo retorno para um pagamento já confirmado é recusado e registrado. O `REQ-REC-011` carrega essa regra, e ela vive no agregado, não no serviço de conciliação — como as transições de situação do beneficiário na Fatia 2 e do programa social na Fatia 3.

### 2. A divergência é um estado, não um registro de log

`REQ-REC-009`. O pagamento divergente fica marcado como tal, com o valor que o banco informou, preservando a situação anterior. Consultar os divergentes de um período passa a ser uma consulta ao próprio pagamento.

A trilha de auditoria continua recebendo o evento — `BATCHCON` já o gravava, e é o único ponto do acervo que preenche valor anterior e posterior. O que muda é que ela deixa de ser a **única** fonte.

### 3. A identificação aceita dois critérios, em ordem

`REQ-REC-001` busca por número. `REQ-REC-002` busca por CPF e período quando o número não vem no retorno. Essa é a ponte para o histórico migrado, cujos pagamentos sem número o `PaymentMigrationReport` já conta em `withoutNumber`.

A ambiguidade é recusada explicitamente: se o par CPF e período localizar mais de um pagamento — situação possível apenas no histórico migrado, porque o `REQ-PAY-001` a tornou impossível daqui para frente —, nenhum é atualizado.

### 4. O arquivo processado é registrado

`REQ-REC-003`. Cada arquivo de retorno tem identificação persistida, e o reprocessamento é recusado com referência à execução anterior. Isso também é o que torna o `REQ-REC-015` possível: uma execução interrompida pode ser retomada porque se sabe o que já foi consumido.

### Sobre o código de ação da trilha

`SIFAP-M-19` decidido como `C`, conforme já previa a política do Estágio 1. Consulta e conciliação recebem códigos distintos. A migração do histórico separa os dois significados **por período**, e isso funciona porque o dicionário registra a descontinuidade: `AUDIT.ddm:127-134` documenta 178 milhões de eventos `CO` como consulta até 2010, quando a portaria interrompeu o registro de consultas, e 193,8 milhões como conciliação a partir de 2014. Entre 2010 e 2014 não há `CO` a classificar.

---

## Alternativas consideradas

### Preservar a busca apenas por número

Descartada. Preservaria o `SIFAP-M-08` no lugar onde ele causa dano real: o histórico migrado ficaria permanentemente inconciliável, e a decisão sobre os duplicados — que esta fatia precisa tomar — não teria como ser informada por dados bancários.

### Corrigir a tolerância de um centavo

Descartada, e esta é a única decisão desta fatia que chegou perto da fronteira do valor. A tolerância não consta de nenhuma norma do acervo. Removê-la transformaria em pendência um volume desconhecido de pagamentos hoje conciliados; mantê-la sem registro repetiria o problema de não se saber o volume. Fica em `PS`: preservada e contada (`REQ-REC-006`).

### Resolver os pagamentos duplicados do histórico nesta fatia

Adiada, com critério. A Fatia 4 identificou os duplicados e não escolheu qual vale. A conciliação fornece o dado que falta: **o que o banco pagou**. Um duplicado cujo par CPF e período case com um crédito bancário tem um vencedor determinável; um que não case com nenhum não tem.

A decisão sobre os que permanecerem ambíguos é de negócio, e sai do escopo técnico desta fatia. O que esta fatia entrega é a redução do conjunto ambíguo ao que de fato exige decisão humana.

---

## Consequências

### Positivas

- Pagamento divergente e pagamento sem correspondência passam a ser consultáveis sem varrer a trilha.
- O total de conciliados passa a corresponder ao que foi efetivamente conciliado.
- Reprocessar um arquivo deixa de ser possível por acidente.
- O histórico migrado sem número entra no alcance da conciliação.
- A decisão sobre os duplicados passa a ter evidência bancária.

### Negativas

- A máquina de estados recusa transições que hoje passam. Retornos que o legado aplicava silenciosamente sobre pagamentos em situação inesperada passam a virar pendência, e alguém precisa tratá-las.
- Registrar o arquivo processado exige uma tabela nova e uma decisão operacional sobre o que fazer quando um arquivo legítimo precisa mesmo ser reprocessado.
- A busca por CPF e período é mais cara que a busca por número. Mitigada pelo índice `idx_payment_cpf_period`, criado na Fatia 4.

### Neutras

- O volume de pendências ao final de um ciclo passa a ser maior que o atual, não porque mais coisas dão errado, e sim porque o que dava errado em silêncio passa a ser contado.

---

## Reversibilidade

| Decisão | Como reverter |
|---|---|
| Transições validadas | Relaxar a máquina de estados no agregado; ponto único |
| Divergência como estado | A trilha continua recebendo o evento; o estado é adicional |
| Busca por CPF e período | Desativar o segundo critério; o primeiro é o do legado |
| Registro do arquivo | Remover a verificação; a tabela permanece como histórico |
| Códigos de ação distintos | Reverter exige nova migração do histórico; é a menos reversível |
