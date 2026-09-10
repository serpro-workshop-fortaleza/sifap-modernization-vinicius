# Especificação — Validação de Documentos

> **Fatia de migração:** 1 — Fundações transversais
> **Destino arquitetural:** kernel compartilhado (`shared/document`)
> **Contexto delimitado:** nenhum. É função pura, sem estado e sem persistência.

| Campo | Valor |
|---|---|
| **Estágio** | Estágio 2 — Especificação |
| **Data** | 2026-09-10 |
| **ADRs aplicáveis** | [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md), [ADR-0005](../../docs/adr/0005-rotina-unica-validacao-cpf.md) |
| **Entrada** | [`business-rules-catalog.md`](../../01-archaeology/business-rules-catalog.md), regras 48-56 e 105-113 |

---

## Contexto

O SIFAP legado tem cinco implementações de validação de CPF com quatro comportamentos distintos, e o próprio código documenta a divergência como ticket aberto desde 2011 (`CCVALCPF.NSC:32-37`). O [ADR-0005](../../docs/adr/0005-rotina-unica-validacao-cpf.md) decidiu unificar na variante normativa.

Esta especificação define o comportamento único de validação de CPF e NIS que todos os módulos do SIFAP 2.0 consomem.

## Escopo

**Dentro:** validação sintática e de dígito verificador de CPF e NIS; contrato de retorno.

**Fora:** verificação de existência do documento em base externa (Receita Federal, CadÚnico); vínculo entre documento e pessoa; regras de quando um documento é obrigatório, que pertencem a cada contexto consumidor.

---

## Requisitos

### REQ-DOC-001 — Recusar CPF não numérico

SE o CPF informado contiver qualquer caractere não numérico, ENTÃO o sistema DEVE recusá-lo como inválido.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CCVALCPF.NSC#L44-L48
- nível: `P` — preservado do comportamento legado
- AC-001.1: Dado o CPF `1234567890A`, Quando validado, Então o resultado é inválido com motivo "caractere não numérico".
- AC-001.2: Dado o CPF `12345678909`, Quando validado, Então a verificação sintática passa.

### REQ-DOC-002 — Recusar CPF com todos os dígitos iguais

SE todos os onze dígitos do CPF forem iguais entre si, ENTÃO o sistema DEVE recusá-lo como inválido.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CCVALCPF.NSC#L79-L90
- nível: `C` — corrigido; a cópia de `CADBENEF.NSP#L344-L413` não faz esta verificação
- AC-002.1: Dado o CPF `11111111111`, Quando validado, Então o resultado é inválido, ainda que o cálculo de módulo 11 seja satisfeito.
- AC-002.2: Dado o CPF `00000000000`, Quando validado, Então o resultado é inválido, sem exceção de prefixo.

> Sequências de dígitos repetidos satisfazem o cálculo de módulo 11. A rejeição precisa ser explícita, e é por isso que a rotina normativa a recebeu em 2005.

### REQ-DOC-003 — Validar CPF por dois dígitos verificadores de módulo 11

O sistema DEVE validar o CPF calculando dois dígitos verificadores por módulo 11, com pesos decrescentes a partir de 10 para o primeiro dígito e a partir de 11 para o segundo, atribuindo zero quando o resto for menor que dois.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CCVALCPF.NSC#L92-L128
- nível: `P` — preservado
- AC-003.1: Dado um CPF cujo décimo dígito difira do primeiro verificador calculado, Quando validado, Então o resultado é inválido.
- AC-003.2: Dado um CPF cujo décimo primeiro dígito difira do segundo verificador calculado, Quando validado, Então o resultado é inválido.
- AC-003.3: Dado um CPF com ambos os verificadores corretos, Quando validado, Então o resultado é válido.

### REQ-DOC-004 — Recusar CPF ausente

SE o CPF informado estiver em branco ou for composto apenas por zeros, ENTÃO o sistema DEVE recusá-lo com motivo distinto de dígito verificador inválido.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/SUBVALCP.NSN#L56-L60
- nível: `P` — preservado
- AC-004.1: Dado o CPF em branco, Quando validado, Então o motivo retornado é "documento não informado".
- AC-004.2: Dado o CPF `00000000000`, Quando validado, Então o motivo retornado é "documento não informado", e não "dígito verificador inválido".

> O legado declara o código `1003` para dígitos repetidos e nunca o emite, devolvendo `1001` nos dois casos (`PDAVALID.NSA:31-40`). O sistema novo distingue os motivos, conforme a intenção registrada no contrato.

### REQ-DOC-005 — Validar NIS por módulo 11 com pesos fixos

O sistema DEVE validar o NIS calculando um dígito verificador por módulo 11 sobre os dez primeiros dígitos, com os pesos 3, 2, 9, 8, 7, 6, 5, 4, 3 e 2, atribuindo zero quando o resto for menor que dois.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/SUBVALNI.NSN#L102-L150
- nível: `P` — preservado
- AC-005.1: Dado um NIS cujo décimo primeiro dígito difira do verificador calculado, Quando validado, Então o resultado é inválido.
- AC-005.2: Dado um NIS com verificador correto, Quando validado, Então o resultado é válido.

### REQ-DOC-006 — Recusar NIS ausente ou não numérico

SE o NIS informado estiver em branco, for composto apenas por zeros ou contiver caractere não numérico, ENTÃO o sistema DEVE recusá-lo como inválido.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/SUBVALNI.NSN#L64-L74
- nível: `P` — preservado
- AC-006.1: Dado o NIS `00000000000`, Quando validado, Então o resultado é inválido com motivo "documento não informado".
- AC-006.2: Dado o NIS `1234567890X`, Quando validado, Então o resultado é inválido com motivo "caractere não numérico".

### REQ-DOC-007 — Devolver resultado sem efeito colateral

O sistema DEVE devolver o resultado da validação como valor, sem gravar dados, sem escrever em tela e sem alterar estado.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/PDAVALID.NSA#L8-L10
- nível: `P` — preservado; o legado já declara `DOES NOT INPUT OR WRITE - RETURNS EVERYTHING VIA PDA`
- AC-007.1: Dado qualquer documento, Quando validado, Então nenhuma linha é gravada em nenhuma tabela.
- AC-007.2: Dado um documento inválido, Quando validado, Então o resultado indica o motivo específico da recusa.

### REQ-DOC-008 — Não permitir que prefixo de documento anule outras validações

SE um documento possuir prefixo classificado como especial, ENTÃO o sistema DEVE aplicar as demais validações normalmente e NÃO DEVE descartar erros já detectados.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALDOCS.NSP#L226-L241
- nível: `C` — corrigido; o legado zera todos os erros acumulados, inclusive os de outros documentos
- AC-008.1: Dado um CPF de prefixo `999` com dígito verificador incorreto, Quando validado, Então o resultado é inválido.
- AC-008.2: Dado um conjunto com CPF de prefixo especial e RG inválido, Quando validado, Então o erro de RG permanece registrado.

### REQ-DOC-009 — Não reconhecer documento de teste em produção

O sistema NÃO DEVE tratar nenhum valor de CPF como documento de teste válido.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/VALBENEF.NSN#L229-L245
- nível: `C` — corrigido; o legado aceita dígitos repetidos quando iniciados em `000`
- AC-009.1: Dado o CPF `00000000000`, Quando validado em ambiente de produção, Então o resultado é inválido.
- AC-009.2: Dado o CPF `00011111111`, Quando validado, Então a validação segue as regras gerais, sem exceção de prefixo.

### REQ-DOC-010 — Registrar ocorrências de documento recusado na carga inicial

QUANDO a carga inicial encontrar um documento que se torne inválido pela regra unificada, o sistema DEVE migrar o registro sinalizado e incluí-lo em relatório de ocorrências.

- source_legacy: "[GREENFIELD] o legado não possui processo de carga; requisito decorre da correção adotada no ADR-0005 e protege contra exclusão indevida de beneficiário"
- nível: `C` — decorrente da correção
- AC-010.1: Dado um beneficiário com CPF de dígitos repetidos, Quando a carga executar, Então o registro é migrado com marcação de documento pendente de revisão.
- AC-010.2: Dado o fim da carga, Quando o relatório for gerado, Então ele contém a contagem e a lista de CPFs sinalizados.

> [!WARNING]
> Nenhum beneficiário pode ser descartado pela carga. Decidir o destino de um registro com documento inválido é competência de negócio, não da migração.

---

## Fora de escopo

| Item | Razão | Destino |
|---|---|---|
| Validação de RG | O legado valida apenas comprimento mínimo (`VALDOCS.NSP:206-223`), sem dígito nem UF emissora | Fatia 2, contexto Cadastro |
| Indicador de documento especial | Previsto no contrato e nunca implementado (`SIFAP-M-14`, nível `C`) | Não será implementado; ver ADR-0005 |
| Consulta a base externa de CPF | Não existe no legado | Fora do escopo de modernização |
| Regra de obrigatoriedade de documento | Pertence a cada contexto consumidor, não ao kernel | Fatias 2 e 4 |

---

## Rastreabilidade

| REQ-ID | Regra do catálogo | Mistério associado | Nível |
|---|---|---|---|
| `REQ-DOC-001` | 48 | — | `P` |
| `REQ-DOC-002` | 49, 51 | `SIFAP-M-16` | `C` |
| `REQ-DOC-003` | 50 | — | `P` |
| `REQ-DOC-004` | 52, 162 | `SIFAP-M-16` | `P` |
| `REQ-DOC-005` | 54 | — | `P` |
| `REQ-DOC-006` | 55 | — | `P` |
| `REQ-DOC-007` | 52, 55 | — | `P` |
| `REQ-DOC-008` | 111 | `SIFAP-M-14` | `C` |
| `REQ-DOC-009` | 105 | `SIFAP-M-15` | `C` |
| `REQ-DOC-010` | — | `SIFAP-M-16` | `C` |

**Mistérios que permanecem abertos.** `SIFAP-M-14`, `SIFAP-M-15` e `SIFAP-M-16` continuam sem validação humana em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md). Esta especificação define como proceder até que a validação venha; o [ADR-0005](../../docs/adr/0005-rotina-unica-validacao-cpf.md) é reversível.

---

## Definição de pronto

- [x] Todo requisito usa um padrão EARS com `DEVE`.
- [x] Todo REQ-ID é único e declarado como título.
- [x] Todo REQ-ID tem `source_legacy:` apontando para arquivo existente ou `[GREENFIELD]` justificado.
- [x] Todo requisito tem critérios de aceitação em Dado/Quando/Então.
- [x] Cada requisito declara o nível de tratamento do [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md).
- [ ] `plan.md` e `tasks.md` gerados.
