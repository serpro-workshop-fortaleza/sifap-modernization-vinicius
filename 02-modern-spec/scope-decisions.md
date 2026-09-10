# Decisões de escopo — Estágio 2

> **Trilha:** [Kit do Time](../README.md) › [Estágio 2](README.md) › **Decisões de escopo**

**Registre as decisões de escopo tomadas durante o Estágio 2: o que foi selecionado, o que foi adiado e quais questões permanecem em aberto.**

| Campo | Valor |
|---|---|
| **Público-alvo** | Dupla 2 durante o Estágio 2; Duplas 3 e 4 durante o handoff H2 |
| **Finalidade** | Apoiar a conversa do estágio; não substitui os artefatos formais do Spec-Kit |
| **Feature relacionada** | `specs/<NNN>-<feature>/` |

> [!NOTE]
> Os entregáveis formais permanecem em `specs/<NNN>-<feature>/spec.md`, `plan.md` e `tasks.md`. Não registre requisitos EARS completos aqui. Este arquivo registra somente decisões de escopo e questões em aberto.

---

**Data**: 2026-09-10
**Fatia em especificação**: 1 — Fundações transversais
**Features formais**: [`specs/001-validacao-de-documentos/`](../specs/001-validacao-de-documentos/spec.md), [`specs/002-trilha-de-auditoria/`](../specs/002-trilha-de-auditoria/spec.md)

---

## Decisões de escopo

### Estruturais

| Decisão | Evidência ou justificativa | Impacto nos artefatos formais |
|---|---|---|
| Migrar o sistema completo, em cinco fatias ordenadas por dependência | `BATCHPGT` lê `BENEFIC` e `SOCPROG` para escrever `PAYMENT`; 175 regras não cabem em uma especificação | Seção 4 do [`discovery-report.md`](../01-archaeology/discovery-report.md) |
| Fatia de migração não é contexto delimitado | Fatia é unidade de trabalho ordenada por dependência; contexto é fronteira de modelo | [`bounded-contexts.md`](bounded-contexts.md) |
| Quatro contextos delimitados e um kernel compartilhado | Avaliação por coesão, acoplamento e frequência de mudança | [`bounded-contexts.md`](bounded-contexts.md) |
| Preservar comportamento do código, não a regra documentada | O documento de 2012 declara-se não validado; sete de oito integrantes da equipe original saíram | [ADR-0003](../docs/adr/0003-preservacao-de-comportamento.md) |

### Rejeições registradas

| Hipótese rejeitada | Motivo | Destino |
|---|---|---|
| Fundações transversais como contexto único | Agrupa capacidades sem relação de domínio, com ciclos de mudança divergentes | Dividida em kernel de validação e contexto de auditoria |
| Conciliação Bancária como contexto separado | Escreve `PAYMENT`, a mesma tabela que a Folha escreve; propriedade compartilhada de dados é o antipadrão que a decomposição evita | Absorvida pelo contexto Pagamento |

> A frequência de mudança divergente da conciliação é real e fica como ponto de reavaliação. Se ela passar a mudar em ritmo próprio, o caminho é extrair submódulo com evento de domínio — nunca compartilhar tabela.

### Correções deliberadas ao comportamento legado

Oito correções na Fatia 1, todas de integridade ou de controle. **Nenhuma altera valor de benefício**, que é o critério do [ADR-0003](../docs/adr/0003-preservacao-de-comportamento.md).

| Correção | Requisito | Legado divergente |
|---|---|---|
| CPF de dígitos repetidos é inválido | `REQ-DOC-002` | `CADBENEF.NSP:344-413` não verifica |
| Prefixo especial não anula outras validações | `REQ-DOC-008` | `VALDOCS.NSP:226-241` zera todos os erros |
| Sem documento de teste em produção | `REQ-DOC-009` | `VALBENEF.NSN:229-245` aceita prefixo `000` |
| Numeração de auditoria por sequência do banco | `REQ-AUD-003` | `CCAUDIT.NSC:64-71` lê o maior e incrementa em memória |
| Instante com precisão de milissegundos | `REQ-AUD-004` | `CCAUDIT.NSC:74-77` descarta o décimo de segundo |
| Registro de autor e perfil | `REQ-AUD-005` | Campo existe no dicionário e nunca é gravado |
| Registro de valores anterior e posterior | `REQ-AUD-006` | Estrutura existe desde 2005 e nunca foi preenchida |
| Evento por operação, não por ciclo | `REQ-AUD-010` | `BATCHPGT.NSP:536-546` grava um evento para 3,8 milhões de pagamentos |

---

## Adiamentos

| Item | Motivo | Destino |
|---|---|---|
| `REQ-DOC-010` — sinalização de documento inválido na carga | Dependia do processo de carga | **Resolvido:** absorvido pelo `REQ-BEN-020` |
| `REQ-AUD-011` — exibir exclusões no relatório | Regra pertence à auditoria; a tela pertence a relatórios | Fatia 5 |
| Validação de RG | Legado valida apenas comprimento mínimo | Fatia 2 — mantido fora do escopo da spec 003 |
| Transições válidas entre situações cadastrais | Sem fonte normativa; nenhum programa do acervo altera situação | Depende de validação humana; ver [ADR-0006](../docs/adr/0006-situacao-cadastral-do-beneficiario.md) |
| Exclusão de dependente | Não existe no `CADDEPEN` | Fatia futura |
| Migração dos FNR 154, 155 e 156 | Três arquivos de auditoria histórica sem DDM publicado | Fatia 5 |
| Relatórios de pagamento e auditoria | Leem dados que fatias anteriores produzem | Fatia 5 |
| Conciliação bancária | Depende de pagamentos gerados | Fatia 5 |

---

## Marcados como greenfield

| Item | Requisito | Justificativa |
|---|---|---|
| Sinalização de documento inválido na carga inicial | `REQ-DOC-010` | O legado não possui processo de carga; o requisito decorre da correção do [ADR-0005](../docs/adr/0005-rotina-unica-validacao-cpf.md) e protege contra exclusão indevida de beneficiário |
| Migração sinalizada de registro inválido | `REQ-BEN-020` | Absorve o `REQ-DOC-010` e o estende às demais correções da Fatia 2 |

Dois `[GREENFIELD]` em 44 requisitos, e o segundo é a continuação do primeiro. Todos os demais têm origem em membro Natural ou DDM real.

---

## Fora do escopo de modernização

| Item | Motivo |
|---|---|
| Eventos de login e logout | 25 milhões de registros gravados por código ausente do acervo |
| Consulta a base externa de CPF | Não existe no legado |
| Integração com SIAFI, retorno da CAIXA e cruzamento com CadÚnico | Citados na documentação; nenhum programa do acervo os implementa |
| Hiperdescritor `H1` | Rotina em Assembler ligada ao núcleo do Adabas, fora do acervo |

---

## Questões em aberto

Os 20 mistérios canônicos permanecem **sem validação humana**. A política do [ADR-0003](../docs/adr/0003-preservacao-de-comportamento.md) define como proceder até que ela venha; nenhum bloqueia o Estágio 2.

### Afetam as fatias já especificadas

| Questão | Fonte consultada | Próxima pessoa responsável |
|---|---|---|
| `M-01` — por que beneficiários com mais de 75 anos são suspensos automaticamente? | `CADBENEF.NSP:250-251` | SENARC |
| `M-02` — qual é o limite válido de dependentes: 3, 5, 6 ou 10? | `CADDEPEN.NSP:117` | SENARC |
| `M-03` — por que a alteração grava a situação cadastral em branco? | `CADBENEF.NSP:314` | SUPDE/DESIF |
| `M-14` — que norma criou os oito prefixos de CPF que zeram todos os erros? | `VALDOCS.NSP:226-241` | DEFIS |
| `M-15` — por que CPF iniciado em `000` é válido como documento de teste? | `VALBENEF.NSN:229-245` | SUPDE/DESIF |
| `M-16` — qual das cinco rotinas de CPF é a correta? | `CCVALCPF.NSC:32-37` | SUPDE/DESIF |
| `M-17` — qual norma prevalece sobre auditar consultas? | `CCAUDIT.NSC:45-49` | CGTI/MDAS |
| `M-18` — por que o relatório de auditoria omite as exclusões? | `RELAUDIT.NSP:128-134` | DEFIS |
| `M-19` — `CO` significa consulta ou conciliação? | `AUDIT.ddm:41` | SUPDE/DESIF |

### Bloqueiam decisão futura

| Questão | Fonte consultada | Próxima pessoa responsável |
|---|---|---|
| `M-09` — qual fórmula de cálculo está vigente? | `CALCBENF.NSN:255-258` | SENARC |
| `M-10` — qual das duas contribuições sociais é a vigente? | `CALCBENF.NSN:356-366` | SENARC |
| `M-11` — a faixa de renda usa renda familiar ou per capita? | `CALCBENF.NSN:174` | SENARC |

> Estes três definem valores pagos. A política de preservação permite especificar a Fatia 4 sem eles, mas a decisão de manter ou corrigir o comportamento continua pendente.

---

## Limitação registrada

> [!WARNING]
> **Não há ambiente legado disponível para caracterização por execução.** Os vetores de teste são derivados da leitura do código, não da captura de entrada e saída reais. Isso vale como premissa, não como prova, e deve ser reconfirmado antes da carga inicial se o acesso for obtido.

Descoberta durante a elaboração do [`plan.md`](../specs/001-validacao-de-documentos/plan.md) da feature 001. Afeta todas as fatias que dependem de teste de caracterização.

---

## Estado do Estágio 2

| Artefato | Situação |
|---|---|
| [`bounded-contexts.md`](bounded-contexts.md) | 4 contextos + 1 kernel |
| [`domain-events.md`](domain-events.md) | 14 eventos; Cadastro e Catálogo confirmados |
| [ADR-0003](../docs/adr/0003-preservacao-de-comportamento.md), [0004](../docs/adr/0004-mapeamento-dependentes-jpa.md), [0005](../docs/adr/0005-rotina-unica-validacao-cpf.md), [0006](../docs/adr/0006-situacao-cadastral-do-beneficiario.md), [0007](../docs/adr/0007-parametrizacao-do-programa-social.md) | Aceitas |
| [`specs/001-validacao-de-documentos/`](../specs/001-validacao-de-documentos/spec.md) | **Implementada** — 10 requisitos, 10 tarefas |
| [`specs/002-trilha-de-auditoria/`](../specs/002-trilha-de-auditoria/spec.md) | **Implementada** — 13 requisitos, 13 tarefas |
| [`specs/003-cadastro-de-beneficiario/`](../specs/003-cadastro-de-beneficiario/spec.md) | **Implementada** — 21 requisitos, 16 tarefas |
| [`specs/004-catalogo-de-programas-sociais/`](../specs/004-catalogo-de-programas-sociais/spec.md) | Especificada — 15 requisitos, 16 tarefas |
| Fatias 4 e 5 | Não especificadas |

**Fatias 1 e 2 implementadas.** 171 testes, 92% de cobertura de linha.

**Fatia 3 pronta para implementação.** 15 requisitos, 16 tarefas, nenhum `[GREENFIELD]`, dez correções de nível `C`.

> A Fatia 3 é a de maior proporção de correções, e a razão é que quase todo achado dela é **ausência**: validação que não existe, operação que não existe, campo que ninguém preenche. Ausência não se preserva.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [Guia do Estágio 2](GUIDE.md)<br/><sub>Especificação moderna passo a passo.</sub> | [Template de ADR](ADR-TEMPLATE.md)<br/><sub>Registre a decisão de escopo como uma ADR.</sub> |

<sub>[Voltar ao índice do kit](../README.md)</sub>
