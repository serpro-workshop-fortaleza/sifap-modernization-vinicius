# Tarefas — Relatórios

> **Especificação:** [`spec.md`](spec.md) · **Plano:** [`plan.md`](plan.md)

**Ordem de execução.** Cada tarefa entrega teste e implementação juntos, conforme a regra do repositório: testes durante a implementação, nunca depois.

| Campo | Valor |
|---|---|
| **Fatia** | 5 — Conciliação e Relatórios |
| **Módulo** | `backend/src/main/java/br/gov/sifap/report/` |
| **Pré-requisito** | Especificação 006 concluída: situação de conciliação disponível |

---

## T-600 — Agregação no contexto dono do pagamento

`PaymentStatistics` como interface pública de `payment`, com agregação por região, programa e situação.

- **Requisitos:** `REQ-REP-001`, `REQ-REP-014`
- **Testes:** os totais correspondem à soma dos pagamentos do período; período vazio devolve lista vazia, não zeros
- **Verificação:** a soma acontece no banco; nenhuma lista de pagamentos atravessa a fronteira
- **Depende de:** Fatia 4

> Trazer os pagamentos para memória e somar em Java trocaria um problema de consulta por um de heap. A agregação pertence a quem é dono da tabela.

---

## T-601 — Escala do relatório

`ReportScale.round()` ao lado do `MonetaryScale.truncate()` que a Fatia 4 já tem.

- **Requisitos:** `REQ-REP-003`, `REQ-REP-004`
- **Testes:** o arredondamento reproduz o do legado; bruto menos desconto é igual ao líquido apresentado
- **Verificação:** nenhuma expressão de arredondamento fora do tipo
- **Depende de:** T-600

> Dois critérios convivem por decisão: cálculo trunca, consolidado arredonda. O legado aplica o dele a uma única variável dentro do laço, e é por isso que a linha impressa não fecha.

---

## T-602 — Agrupamento por região

Agregação pelo código de região do beneficiário, com grupo próprio para não classificados.

- **Requisitos:** `REQ-REP-002`
- **Testes:** cada código de região soma na sua região; beneficiário sem região vai para não classificados; o total geral é igual ao da soma sem agrupamento
- **Verificação:** nenhuma conversão numérica intermediária do código de região
- **Depende de:** T-601

> [!WARNING]
> `SIFAP-F5-04`. As faixas `1-5`, `6-10`, `11-15`, `16-20` não correspondem ao domínio `01`-`05` e `99`. Desde 1999 o relatório distribuído à SENARC mostra a folha concentrada em duas regiões.

---

## T-603 — Situação fora do domínio

Grupo de não classificados na consolidação por situação.

- **Requisitos:** `REQ-REP-005`
- **Testes:** situação fora do domínio não é somada a uma situação válida; a quantidade de não classificados é informada
- **Verificação:** nenhum ramo padrão atribui situação conhecida
- **Depende de:** T-602

> `BATCHREL` classifica o desconhecido como gerado e `RELPGT` como outro. Dois relatórios do mesmo job descrevem o mesmo pagamento de formas diferentes.

---

## T-604 — Relatório consolidado

`ConsolidatedReportAssembler` compondo as três agregações.

- **Requisitos:** `REQ-REP-001`, `REQ-REP-003`, `REQ-REP-004`
- **Testes:** consolidado de um período com dados conhecidos; período sem pagamentos informa ausência; a nota de divergência contra a base truncada aparece
- **Verificação:** o montador não conhece repositório
- **Depende de:** T-603

---

## T-605 — Cópia arquivável do consolidado

Documento de retenção com campos separáveis.

- **Requisitos:** `REQ-REP-015`
- **Testes:** os campos da cópia são separáveis sem conhecer larguras; o conteúdo corresponde ao relatório apresentado
- **Verificação:** o formato não depende do programa que o gerou para ser lido
- **Depende de:** T-604

> Retenção de dez anos pela Lei 8.159, art. 14. O legado usa `COMPRESS` sem delimitador: dez anos de arquivo que exigem o programa original.

---

## T-606 — Detalhamento por período e programa

`DetailedReportAssembler` com subtotal por programa vindo de agregação.

- **Requisitos:** `REQ-REP-006`, `REQ-REP-007`, `REQ-REP-008`
- **Testes:** programas intercalados recebem um subtotal cada; a soma dos subtotais é igual ao total geral; o total geral aparece quando existem pagamentos após o período consultado
- **Verificação:** nenhuma quebra de controle durante varredura
- **Depende de:** T-604

> `SIFAP-F5-05`. A quebra manual pressupõe ordenação por programa; a leitura é por período. E o total geral vive em `AT END OF DATA`, que o `ESCAPE BOTTOM` impede de executar.

---

## T-607 — Documento do titular mascarado

Uso de `Cpf.mask()` do kernel em todo relatório.

- **Requisitos:** `REQ-REP-009`
- **Testes:** o documento aparece mascarado conforme o `REQ-BEN-017`; o documento não é reconstituível a partir da linha impressa
- **Verificação:** nenhuma máscara própria no contexto de relatório
- **Depende de:** T-606

> Três máscaras no acervo, nenhuma em copycode. A de `RELPGT.NSP:164-167` oculta o prefixo e expõe oito dígitos ao lado do nome e da UF, em documento distribuído ao centro de impressão.

---

## T-608 — Pagamento sem beneficiário correspondente

Sinalização por linha e contagem no rodapé.

- **Requisitos:** `REQ-REP-013`
- **Testes:** pagamento órfão é identificado como tal; a quantidade de órfãos é informada; o relatório não é interrompido
- **Verificação:** o órfão não aparece com nome em branco
- **Depende de:** T-607

---

## T-609 — Relatório de auditoria com exclusões

`AuditReportAssembler` sobre a `AuditQuery` da Fatia 1, sem filtro implícito.

- **Requisitos:** `REQ-REP-010`
- **Testes:** exclusões aparecem quando não há filtro; exclusões só somem por escolha explícita; nenhum filtro é aplicado antes dos escolhidos
- **Verificação:** não existe condição de exclusão fora dos parâmetros recebidos
- **Depende de:** T-604

> Realiza o `REQ-AUD-011`, especificado na Fatia 1 e sem implementação até agora porque não havia relatório onde implementá-lo. O dicionário documenta o contorno em `AUDIT.ddm:138-140`: consultar pelo Adabas Online.

---

## T-610 — Titular afetado no relatório de auditoria

Exibição do titular, mascarado, em cada evento.

- **Requisitos:** `REQ-REP-011`
- **Testes:** evento com titular exibe o documento mascarado; evento sem titular deixa a coluna vazia sem sugerir ausência de dado
- **Verificação:** o campo vem da projeção da trilha, não de consulta ao cadastro
- **Depende de:** T-609

> `AUDIT.ddm:57` declara o campo. Nenhuma das duas saídas do `RELAUDIT` o exibe: a trilha sabe quem foi afetado e o relatório não conta.

---

## T-611 — Intervalo obrigatório na consulta da trilha

Recusa de consulta sem data inicial e limite configurável de intervalo.

- **Requisitos:** `REQ-REP-016`
- **Testes:** consulta sem data inicial é recusada; intervalo acima do limite é recusado com o limite informado
- **Verificação:** não existe data inicial padrão no código
- **Depende de:** T-609

> O padrão de `19970101` transforma um relatório sem parâmetro em varredura de 418 milhões de registros e 311 GB.

---

## T-612 — Conteúdo independente do meio de saída

Montagem única, formatação por adaptador.

- **Requisitos:** `REQ-REP-012`
- **Testes:** a mesma consulta produz o mesmo conteúdo em meios diferentes; subtotal diário e distribuição por dia aparecem em qualquer meio
- **Verificação:** nenhuma decisão de conteúdo depende do formato de saída
- **Depende de:** T-610, T-611

> No `RELAUDIT`, a descrição do evento, o subtotal diário e o histograma existem só para quem imprime. A mesma consulta produz dois conteúdos.

---

## T-613 — Interface pública e API

`ReportQuery` e `ReportController` em `/api/v1/reports`.

- **Requisitos:** `REQ-REP-001`, `REQ-REP-006`, `REQ-REP-010`
- **Testes:** os três relatórios respondem; período inválido é recusado na borda; o acesso é registrado
- **Verificação:** os paths seguem `/api/v1/{resource}`
- **Depende de:** T-612

---

## T-614 — Divergências deliberadas do legado

Testes que falham contra o SIFAP original, reunidos em uma classe.

- **Requisitos:** as onze correções desta especificação
- **Testes:** um por correção, cada um citando `arquivo:linha` da origem
- **Verificação:** nenhuma correção altera valor gravado
- **Depende de:** T-613

---

## T-615 — Fronteira do contexto de leitura

Regras ArchUnit para `report`.

- **Requisitos:** decisão de projeto do [`plan.md`](plan.md)
- **Testes:** nenhuma classe de `report` é `@Entity`; nenhuma depende de `jakarta.persistence`; nenhuma alcança pacote interno de outro contexto
- **Verificação:** a suíte de arquitetura continua verde
- **Depende de:** T-614

> A ausência de escrita é a garantia do `REQ-REP-014`. Uma regra que a torne verificável impede que ela se perca na próxima alteração.

---

## Resumo

| Tarefa | Requisitos | Depende de |
|---|---|---|
| T-600 | `REQ-REP-001`, `014` | Fatia 4 |
| T-601 | `REQ-REP-003`, `004` | T-600 |
| T-602 | `REQ-REP-002` | T-601 |
| T-603 | `REQ-REP-005` | T-602 |
| T-604 | `REQ-REP-001`, `003`, `004` | T-603 |
| T-605 | `REQ-REP-015` | T-604 |
| T-606 | `REQ-REP-006`, `007`, `008` | T-604 |
| T-607 | `REQ-REP-009` | T-606 |
| T-608 | `REQ-REP-013` | T-607 |
| T-609 | `REQ-REP-010` | T-604 |
| T-610 | `REQ-REP-011` | T-609 |
| T-611 | `REQ-REP-016` | T-609 |
| T-612 | `REQ-REP-012` | T-610, T-611 |
| T-613 | `REQ-REP-001`, `006`, `010` | T-612 |
| T-614 | correções | T-613 |
| T-615 | fronteira | T-614 |
