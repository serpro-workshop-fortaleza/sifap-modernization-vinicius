# Especificação — Trilha de Auditoria

> **Fatia de migração:** 1 — Fundações transversais
> **Destino arquitetural:** contexto delimitado (`audit`)
> **Dados sob responsabilidade:** `AUDIT` (FNR 153) e os três arquivos históricos sem DDM

| Campo | Valor |
|---|---|
| **Estágio** | Estágio 2 — Especificação |
| **Data** | 2026-09-10 |
| **ADRs aplicáveis** | [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md) |
| **Entrada** | [`business-rules-catalog.md`](../../01-archaeology/business-rules-catalog.md), regras 57-63 e 164-170 |

---

## Contexto

A trilha de auditoria do SIFAP existe por obrigação normativa (`IN-TCU 63/2010`) e é o maior conjunto de dados do sistema: **417.884.120 registros e 311 GB**, distribuídos em quatro arquivos Adabas, três dos quais sem dicionário publicado.

A leitura do Estágio 1 encontrou uma trilha que registra **que** algo mudou, mas quase nunca **o que** mudou:

- Os grupos de valores anterior e posterior existem no dicionário desde 2005 e nenhum programa os preenche (`AUDIT.ddm:61-69`).
- O perfil do usuário é declarado e nunca gravado (`CCAUDIT.NSC:50-52`, ticket 7742 aberto).
- Dois programas que alteram dados financeiros não incluem a rotina de auditoria.
- O relatório oficial oculta os eventos de exclusão (`RELAUDIT.NSP:128-134`).

Este contexto é o primeiro da migração porque todo módulo que escreve depende dele.

## Escopo

**Dentro:** registro de eventos de alteração e de acesso; numeração; retenção; consulta e relatório.

**Fora:** autenticação e autorização, que produzem os eventos mas não pertencem a este contexto; migração dos arquivos históricos, tratada na Fatia 5.

---

## Requisitos

### REQ-AUD-001 — Registrar evento em toda alteração de dado

QUANDO um dado de negócio for criado, alterado ou excluído, o sistema DEVE registrar um evento na trilha de auditoria.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CCAUDIT.NSC#L8-L11
- nível: `P` — preservado; o legado já declara a obrigação
- AC-001.1: Dado um beneficiário incluído, Quando a operação concluir, Então existe um evento com ação de inclusão.
- AC-001.2: Dado um pagamento gerado, Quando a operação concluir, Então existe um evento correspondente.

> No legado, garantir o registro depende da disciplina de cada autor incluir o copycode — e `CALCBENF` e `CALCDSCT` não incluem. Com evento de domínio, o registro deixa de ser opcional.

### REQ-AUD-002 — Impedir alteração e exclusão de evento

O sistema NÃO DEVE permitir alteração nem exclusão de evento de auditoria já registrado.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm#L14-L18
- nível: `P` — preservado; o dicionário declara `IMMUTABLE RECORD - UPDATE/DELETE NOT ALLOWED`
- AC-002.1: Dada uma tentativa de atualizar um evento, Quando executada, Então a operação é recusada.
- AC-002.2: Dada uma tentativa de excluir um evento, Quando executada, Então a operação é recusada.

### REQ-AUD-003 — Atribuir número único sem risco de colisão

O sistema DEVE atribuir a cada evento um número único gerado por sequência do banco de dados.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CCAUDIT.NSC#L64-L71
- nível: `C` — corrigido; o legado lê o maior número existente e incrementa em memória
- AC-003.1: Dadas duas sessões concorrentes, Quando ambas registrarem eventos, Então os números atribuídos são distintos.
- AC-003.2: Dado um evento registrado, Quando consultado, Então seu número não se repete em nenhum outro evento.

> A técnica legada lê a semente uma única vez por execução e incrementa localmente. Duas sessões simultâneas produzem os mesmos números, sobre uma chave declarada como única.

### REQ-AUD-004 — Registrar o instante do evento com precisão de milissegundos

O sistema DEVE registrar data e hora de cada evento com precisão de milissegundos.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CCAUDIT.NSC#L74-L80
- nível: `C` — corrigido; o legado descarta o décimo de segundo para caber em campo de seis dígitos
- AC-004.1: Dados dois eventos no mesmo segundo, Quando ordenados por instante, Então a ordem entre eles é determinística.

> A perda de precisão é consequência do formato do campo, não decisão de negócio. Combinada com a numeração da `REQ-AUD-003`, tornava impossível ordenar eventos do mesmo segundo.

### REQ-AUD-005 — Registrar autor e perfil de acesso

O sistema DEVE registrar, em cada evento, o identificador do autor e o perfil sob o qual a operação foi executada.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm#L74-L76
- nível: `C` — corrigido; o campo de perfil existe no dicionário e nenhum programa o preenche
- AC-005.1: Dado um evento registrado, Quando consultado, Então contém autor e perfil.
- AC-005.2: Dada uma operação de sistema sem usuário interativo, Quando registrada, Então o autor identifica o processo de origem.

> Sem perfil, a exigência da `RN-009` — alteração de CPF requer autorização de supervisor — não é auditável. A trilha registra quem agiu, nunca com qual permissão.

### REQ-AUD-006 — Registrar valores anterior e posterior em alterações

QUANDO um evento for de alteração, o sistema DEVE registrar o nome de cada campo modificado com seu valor anterior e posterior.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm#L61-L69
- nível: `C` — corrigido; a estrutura existe desde 2005 e nunca foi preenchida
- AC-006.1: Dada uma alteração de renda familiar, Quando registrada, Então o evento contém o campo, o valor anterior e o novo.
- AC-006.2: Dado um evento de inclusão, Quando registrado, Então não há valor anterior.

### REQ-AUD-007 — Registrar acesso a dado pessoal

QUANDO um dado pessoal de beneficiário for consultado, o sistema DEVE registrar um evento de acesso.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CONSBENF.NSP#L167-L179
- nível: `C` — corrigido; decisão do `SIFAP-M-17`, com retenção configurável por tipo de ação
- AC-007.1: Dada uma consulta por CPF, Quando executada, Então existe evento de acesso com o CPF consultado.
- AC-007.2: Dada a configuração de retenção para eventos de acesso, Quando o prazo expirar, Então os eventos são expurgados sem afetar eventos de alteração.

> Duas normas internas conflitam: a `PORT. CGTI 213/2010` proíbe por volume; a `IN-TCU 63/2010` exige rastreio. A retenção diferenciada atende às duas — registra o acesso e limita o crescimento. O `SIFAP-M-17` permanece aberto.

### REQ-AUD-008 — Usar código de ação não ambíguo

O sistema DEVE identificar cada tipo de ação por um código exclusivo, sem reaproveitar o mesmo código para ações distintas.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm#L38-L49
- nível: `C` — corrigido; decisão do `SIFAP-M-19`
- AC-008.1: Dado um evento de consulta e um de conciliação, Quando registrados, Então recebem códigos distintos.
- AC-008.2: Dado um evento migrado do legado, Quando o código de origem for ambíguo, Então a desambiguação usa o período de origem e fica registrada.

> No legado, `CO` significa consulta no dicionário, é gravado como consulta pelo `CONSBENF` desde 2016 e responde por 193,8 milhões de registros de conciliação entre 2014 e 2018. Três significados no mesmo campo.

### REQ-AUD-009 — Registrar contexto de execução em eventos de processamento

ONDE o evento tiver origem em processamento automático, o sistema DEVE registrar o identificador da execução e sua situação.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CCAUDIT.NSC#L92-L96
- nível: `P` — preservado
- AC-009.1: Dado um ciclo de folha, Quando concluído, Então o evento contém o identificador da execução.

### REQ-AUD-010 — Registrar evento por operação, não por ciclo

QUANDO um processamento em lote alterar dados, o sistema DEVE registrar um evento por operação, além do evento de conclusão do ciclo.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP#L536-L546
- nível: `C` — corrigido; o legado grava um único evento para todo o ciclo
- AC-010.1: Dado um ciclo que gerou N pagamentos, Quando concluído, Então existem N eventos de pagamento mais um de ciclo.
- AC-010.2: Dado um pagamento específico, Quando auditado, Então é possível identificar quem o gerou e quando.

> Cerca de 3,8 milhões de pagamentos por ciclo produzem hoje um único registro de auditoria. Nenhum pagamento individual é rastreável.

### REQ-AUD-011 — Exibir eventos de exclusão no relatório

O sistema DEVE incluir os eventos de exclusão no relatório de auditoria.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/RELAUDIT.NSP#L128-L134
- nível: `C` — corrigido; decisão do `SIFAP-M-18`
- AC-011.1: Dado um período com eventos de exclusão, Quando o relatório for gerado sem filtro, Então as exclusões aparecem.
- AC-011.2: Dado um filtro por tipo de ação, Quando aplicado, Então a exclusão só é omitida por escolha explícita de quem consulta.

> O relatório atual descarta a categoria mais relevante para auditoria. O dicionário documenta o contorno: consultar diretamente pelo Adabas Online.

### REQ-AUD-012 — Consultar a trilha por entidade e período

O sistema DEVE permitir consultar eventos por tipo de entidade, identificador da entidade e intervalo de datas.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm#L102-L104
- nível: `P` — preservado; o dicionário instrui a usar o superdescritor de entidade e data em consultas pesadas
- AC-012.1: Dado um CPF e um período, Quando consultados, Então retorna os eventos daquele beneficiário no intervalo.
- AC-012.2: Dada uma consulta sobre volume de produção, Quando executada, Então usa índice, não varredura sequencial.

### REQ-AUD-013 — Reter eventos pelo prazo legal

O sistema DEVE reter cada evento de alteração por no mínimo dez anos.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm#L16-L17
- nível: `P` — preservado; `Lei 8159, art. 14`
- AC-013.1: Dado um evento de alteração com menos de dez anos, Quando o expurgo executar, Então o evento é preservado.

---

## Fora de escopo

| Item | Razão | Destino |
|---|---|---|
| Migração dos arquivos históricos | FNR 154, 155 e 156 não têm DDM; escopo pendente de decisão | Fatia 5 |
| Autenticação e autorização | Produzem eventos mas não pertencem a este contexto | Fatia futura |
| Eventos de login e logout | 25 milhões de registros gravados por código ausente do acervo | Fora do escopo de modernização |
| Relatório de distribuição diária | Recurso de apoio, sem obrigação normativa | Fatia 5 |

---

## Rastreabilidade

| REQ-ID | Regra do catálogo | Mistério associado | Nível |
|---|---|---|---|
| `REQ-AUD-001` | 18, 33, 43, 63 | — | `P` |
| `REQ-AUD-002` | 169 | — | `P` |
| `REQ-AUD-003` | 57 | — | `C` |
| `REQ-AUD-004` | 58, 59 | — | `C` |
| `REQ-AUD-005` | 62, 170 | — | `C` |
| `REQ-AUD-006` | 170 | — | `C` |
| `REQ-AUD-007` | 61, 139 | `SIFAP-M-17` | `C` |
| `REQ-AUD-008` | 144, 165, 166 | `SIFAP-M-19` | `C` |
| `REQ-AUD-009` | 60 | — | `P` |
| `REQ-AUD-010` | 80, 95, 129 | — | `C` |
| `REQ-AUD-011` | 143, 167 | `SIFAP-M-18` | `C` |
| `REQ-AUD-012` | 169 | — | `P` |
| `REQ-AUD-013` | 169 | — | `P` |

**Oito correções, cinco preservações.** A proporção é alta e tem explicação: quase todo achado deste módulo é defeito de rastreabilidade, não regra de negócio. Nenhuma das correções altera valor de benefício, conforme o critério do [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md).

**Mistérios que permanecem abertos.** `SIFAP-M-17`, `SIFAP-M-18` e `SIFAP-M-19` continuam sem validação humana em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md).

---

## Definição de pronto

- [x] Todo requisito usa um padrão EARS com `DEVE`.
- [x] Todo REQ-ID é único e declarado como título.
- [x] Todo REQ-ID tem `source_legacy:` apontando para arquivo existente.
- [x] Todo requisito tem critérios de aceitação em Dado/Quando/Então.
- [x] Cada requisito declara o nível de tratamento do [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md).
- [ ] `plan.md` e `tasks.md` gerados.
