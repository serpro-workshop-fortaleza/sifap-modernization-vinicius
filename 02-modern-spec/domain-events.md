# Catálogo de eventos de domínio — SIFAP 2.0

> **Trilha:** [Kit do Time](../README.md) › [Estágio 2](README.md) › **Eventos de domínio**

**Contrato de comunicação entre contextos delimitados.** Define os eventos que cada contexto publica e como a trilha de auditoria os consome.

| Campo | Valor |
|---|---|
| **Estágio** | Estágio 2 — Especificação |
| **Data** | 2026-09-10 |
| **Consumidor principal** | Contexto Auditoria — [`specs/002-trilha-de-auditoria/`](../specs/002-trilha-de-auditoria/spec.md) |
| **Entrada** | [`bounded-contexts.md`](bounded-contexts.md), [`dependency-map.md`](../01-archaeology/dependency-map.md) |

---

## Por que eventos, e não chamada direta

No legado, cada programa monta o registro de auditoria e chama `PERFORM WRITE-AUDIT`. O acoplamento é direto e depende da disciplina de quem escreve o programa.

O resultado está documentado no catálogo, regras 80 e 95: **`CALCBENF` e `CALCDSCT` alteram dados financeiros e não incluem a rotina de auditoria.** A criação do pagamento, evento primário do sistema, é o único que não deixa rastro.

Com evento de domínio, o contexto de origem apenas publica o fato. Garantir o registro passa a ser responsabilidade da auditoria — não da lembrança de cada autor.

---

## Convenções

| Regra | Justificativa |
|---|---|
| Nome no passado, indicando fato consumado | Evento descreve o que ocorreu, não o que se deseja |
| `record` imutável, sem entidade JPA no payload | Impede que o consumidor navegue pelo modelo do publicador |
| Publicado dentro da transação de origem | Preserva o comportamento legado: `BACKOUT` desfaz dado e auditoria juntos |
| Carrega sempre `Actor` | Requisito `REQ-AUD-005`; o legado nunca registra o perfil |
| Alterações carregam `Map<String, Change>` | Requisito `REQ-AUD-006`; a estrutura existe no DDM desde 2005 e nunca foi preenchida |

```java
public record Actor(String id, ActorProfile profile, ActorType type) { }
public record Change(String before, String after) { }
```

`ActorType` distingue operador humano de processo automático, o que o legado resolve gravando `*INIT-USER` apenas quando a ação é de lote.

---

## Contexto: Cadastro de Beneficiário

| Evento | Quando ocorre | Payload adicional | Origem no legado |
|---|---|---|---|
| `BeneficiaryRegistered` | Inclusão de beneficiário | CPF, programa, situação inicial | `CADBENEF.NSP:295` |
| `BeneficiaryUpdated` | Alteração de dados cadastrais | CPF, `changes` | `CADBENEF.NSP:318` |
| `BeneficiaryStatusChanged` | Mudança de situação cadastral | CPF, situação anterior e nova, motivo | `CADBENEF.NSP:250-252` |
| `DependentAdded` | Inclusão de dependente | CPF do titular, identificador do dependente | `CADDEPEN.NSP:203` |
| `BeneficiaryQueried` | Consulta a dado pessoal | CPF consultado, finalidade | `CONSBENF.NSP:167-179` |

**Por que `BeneficiaryStatusChanged` é evento próprio.** O `SIFAP-M-01` está em nível `PS`: a suspensão automática acima de 75 anos é preservada, mas cada ocorrência precisa ser registrada. Um evento específico torna essa exigência verificável, em vez de escondê-la dentro de uma alteração genérica.

O legado não tem esse evento — a mudança de situação é indistinguível de qualquer outra alteração de campo.

---

## Contexto: Catálogo de Programas Sociais

| Evento | Quando ocorre | Payload adicional | Origem no legado |
|---|---|---|---|
| `SocialProgramRegistered` | Inclusão de programa social | Código, tipo, valor base | `CADPROG.NSP:139` |

**Um único evento.** O `CADPROG` só implementa inclusão e consulta (`:84-87`); não existe alteração nem encerramento de programa social no legado.

O `SIFAP-M-... ` correspondente registra a lacuna: `SOCPROG.ddm:37` prevê situação `INATIVO` e `ENCERRADO`, e nenhum programa as atribui. Se a Fatia 3 decidir implementar o encerramento, o evento `SocialProgramClosed` entra aqui.

---

## Contexto: Pagamento

| Evento | Quando ocorre | Payload adicional | Origem no legado |
|---|---|---|---|
| `PaymentGenerated` | Pagamento gerado no ciclo | CPF, período, valores bruto e líquido | `BATCHPGT.NSP:488` |
| `PaymentDiscountsApplied` | Descontos calculados | Identificador do pagamento, total, `changes` | `CALCDSCT.NSP:186` |
| `PaymentCorrected` | Correção retroativa aplicada | Identificador, valor original e corrigido | `CALCCORR.NSP:208` |
| `PaymentReconciled` | Retorno bancário conciliado | Identificador, código de retorno, nova situação | `BATCHCON.NSP:211` |
| `PaymentReconciliationDiverged` | Divergência de valor detectada | Identificador, valor do sistema e do banco | `BATCHCON.NSP:200` |
| `PayrollCycleCompleted` | Ciclo mensal concluído | Período, quantidade gerada, totais | `BATCHPGT.NSP:545` |

**`PaymentGenerated` e `PayrollCycleCompleted` coexistem.** O `REQ-AUD-010` exige um evento por operação **e** um de conclusão do ciclo. Hoje o legado grava apenas o segundo: 3,8 milhões de pagamentos produzem um único registro de auditoria, e nenhum pagamento individual é rastreável.

**`PaymentDiscountsApplied` e `PaymentCorrected` são os eventos que o legado não tem.** `CALCDSCT` e `CALCCORR` alteram valores financeiros sem qualquer registro. São a correção direta das regras 80 e 95.

---

## Mapeamento para ação de auditoria

O `REQ-AUD-008` exige códigos de ação não ambíguos. O legado colapsa três significados em `CO`.

| Evento | Ação registrada | Tabela de destino |
|---|---|---|
| `BeneficiaryRegistered` | `INCLUSAO` | `audit_change_event` |
| `BeneficiaryUpdated` | `ALTERACAO` | `audit_change_event` |
| `BeneficiaryStatusChanged` | `ALTERACAO` | `audit_change_event` |
| `DependentAdded` | `ALTERACAO` | `audit_change_event` |
| `SocialProgramRegistered` | `INCLUSAO` | `audit_change_event` |
| `PaymentGenerated` | `INCLUSAO` | `audit_change_event` |
| `PaymentDiscountsApplied` | `ALTERACAO` | `audit_change_event` |
| `PaymentCorrected` | `ALTERACAO` | `audit_change_event` |
| `PaymentReconciled` | `CONCILIACAO` | `audit_change_event` |
| `PaymentReconciliationDiverged` | `CONCILIACAO` | `audit_change_event` |
| `PayrollCycleCompleted` | `PROCESSAMENTO` | `audit_change_event` |
| `BeneficiaryQueried` | `CONSULTA` | `audit_access_event` |

**`CONSULTA` e `CONCILIACAO` são ações distintas.** No legado, ambas gravam `CO`, e o `RELAUDIT.NSP:170-172` traduz o código como "reconciliação" — de modo que toda consulta a dado pessoal aparece no relatório de auditoria como conciliação bancária.

Único evento roteado para `audit_access_event`, por decisão do [`plan.md`](../specs/002-trilha-de-auditoria/plan.md): retenção configurável e independente da retenção legal de dez anos.

---

## O que não é evento de domínio

| Item | Por quê | Tratamento |
|---|---|---|
| Validação de CPF ou NIS | Função pura, sem efeito no domínio | Chamada direta ao kernel |
| Leitura de programa social pelo cálculo | Consulta operacional, não acesso a dado pessoal | Interface `SocialProgramQuery`, sem evento |
| Erro de execução | Não é fato de negócio | Log da aplicação |
| Login e logout | 25 milhões de registros no legado, gravados por código ausente do acervo | Fora do escopo de modernização |

> Consulta a programa social **não** gera evento de acesso. `SOCPROG` tem cerca de 45 registros de parametrização e nenhum dado pessoal. Auditar essa leitura repetiria o problema de volume que motivou a `PORT. CGTI 213/2010`.

---

## Rastreabilidade

| Evento | Requisito atendido | Nível |
|---|---|---|
| Todos os eventos de alteração | `REQ-AUD-001` | `P` |
| `BeneficiaryUpdated`, `PaymentDiscountsApplied`, `PaymentCorrected` | `REQ-AUD-006` | `C` |
| `BeneficiaryQueried` | `REQ-AUD-007` | `C` |
| `PaymentReconciled` contra `BeneficiaryQueried` | `REQ-AUD-008` | `C` |
| `PayrollCycleCompleted` | `REQ-AUD-009` | `P` |
| `PaymentGenerated` | `REQ-AUD-010` | `C` |
| `BeneficiaryStatusChanged` | Sinalização exigida pelo `SIFAP-M-01` | `PS` |

---

## Estabilidade do contrato

Este catálogo é **provisório para as fatias 2 a 5**. Os eventos de Cadastro, Catálogo e Pagamento serão confirmados quando cada fatia for especificada.

O que **não** muda: a forma do evento, o `Actor` obrigatório, a publicação dentro da transação e o roteamento para uma das duas tabelas. É isso que a Fatia 1 implementa e precisa estar certo.

Acrescentar um evento depois é barato: o consumidor já existe. Mudar a forma do evento depois é caro, porque todos os publicadores precisam ser tocados.

---

## Definição de pronto

- [x] Todo contexto publicador tem seus eventos listados.
- [x] Todo evento tem origem no legado com `arquivo:linha`, ou justificativa quando é novo.
- [x] Mapeamento para ação de auditoria definido, sem código ambíguo.
- [x] Roteamento entre as duas tabelas de auditoria definido.
- [x] O que não é evento está registrado com justificativa.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [Bounded contexts](bounded-contexts.md)<br/><sub>Decomposição do sistema.</sub> | [spec 002](../specs/002-trilha-de-auditoria/spec.md)<br/><sub>Trilha de auditoria.</sub> |

<sub>[Voltar ao índice do kit](../README.md)</sub>
