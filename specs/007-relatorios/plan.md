# Plano técnico — Relatórios

> **Especificação:** [`spec.md`](spec.md) · **Tarefas:** [`tasks.md`](tasks.md)

| Campo | Valor |
|---|---|
| **Fatia de migração** | 5 — Conciliação e Relatórios |
| **Contexto** | `report` |
| **Requisitos cobertos** | `REQ-REP-001` a `REQ-REP-016` |
| **Volume de referência** | 180 milhões de pagamentos, 418 milhões de eventos de auditoria, 311 GB |

---

## Decisões de projeto

### O contexto não tem escrita, e isso é verificável

`report` não tem entidade JPA, não tem repositório de agregado, não tem serviço transacional de escrita. A ausência é a garantia do `REQ-REP-014`: se o relatório diverge da base, a divergência está na apresentação, nunca no cálculo.

```text
report/
├── ConsolidatedReport.java         # projeção pública
├── DetailedPaymentReport.java
├── AuditReport.java
├── ReportQuery.java                # interface pública
└── internal/
    ├── ConsolidatedReportAssembler.java
    ├── DetailedReportAssembler.java
    ├── AuditReportAssembler.java
    └── ReportController.java
```

A regra ArchUnit correspondente é direta: nenhuma classe de `report` é anotada com `@Entity`, e nenhuma depende de `jakarta.persistence`.

### A agregação pertence a quem é dono do dado

O consolidado precisa somar milhões de pagamentos por região, programa e situação. Fazer isso pela `PaymentQuery`, que devolve uma projeção por vez, seria inviável.

Duas saídas erradas foram descartadas. Dar ao `report` um repositório próprio sobre as tabelas de `payment` quebraria a fronteira sem dizer que a quebrou. Trazer todos os pagamentos para memória e agregar em Java trocaria um problema de consulta por um de heap.

**Decisão:** cada contexto expõe a agregação do seu próprio dado, e `report` compõe.

```java
// no contexto payment, interface pública
public interface PaymentStatistics {
    List<PeriodTotals> totalsByRegion(String referencePeriod);
    List<PeriodTotals> totalsByProgram(String referencePeriod);
    List<PeriodTotals> totalsByStatus(String referencePeriod);
}
```

A soma acontece no banco, dentro do contexto que é dono da tabela. O `report` recebe linhas já agregadas e as formata. É também o que mantém o `REQ-REP-014` verdadeiro por construção: não há o que recalcular quando o número já chega pronto.

### O agrupamento regional deixa de ser faixa numérica

O defeito do `SIFAP-F5-04` é uma classificação por faixas que não corresponde ao domínio:

```text
IF #COD-REGION >= 1 AND #COD-REGION <= 5    /* NORTH */
```

O domínio real, fixado pela restrição `ck_region_code` da Fatia 3, é `01` a `05` e `99`. Todos os códigos válidos caem na primeira faixa.

**Decisão:** o agrupamento usa o código de região diretamente, sem conversão numérica intermediária. Pagamento cujo beneficiário não tenha região classificável entra em um grupo próprio de não classificados (`AC-002.2`), nunca somado a uma região real.

O total geral não muda. O que muda é a distribuição entre as linhas — e é por isso que a consequência negativa registrada no [ADR-0010](../../docs/adr/0010-relatorios-como-leitura.md) é de comunicação, não de número.

### O arredondamento é um tipo, não uma expressão repetida

`REQ-REP-003` preserva o arredondamento do consolidado; `REQ-REP-004` exige que ele valha para todos os totais da mesma linha. A Fatia 4 já tem `MonetaryScale.truncate()` para o cálculo. O relatório recebe o par:

```java
public final class ReportScale {
    public static BigDecimal round(BigDecimal value);   // REQ-REP-003
}
```

Dois critérios convivem no sistema por decisão explícita: cálculo trunca, consolidado arredonda. Cada um em um tipo, cada um com um requisito, nenhum como expressão solta no meio de um laço.

O legado tem o critério aplicado a uma única variável dentro do `READ`, e é exatamente por isso que bruto menos desconto não dá líquido em `BATCHREL.NSP:174-182`.

### O subtotal não depende da ordem de leitura

`REQ-REP-007`. A quebra manual de `RELPGT.NSP:141-148` pressupõe ordenação por programa sobre uma leitura ordenada por período.

**Decisão:** a agregação é feita por consulta com `GROUP BY`, não por quebra de controle durante a varredura. O subtotal por programa passa a ser independente da ordem porque deixa de depender dela.

O total geral vem da mesma consulta, o que resolve o `REQ-REP-008` sem tratamento especial: não há `AT END OF DATA` que possa deixar de executar.

### A máscara já existe e não será reescrita

`REQ-REP-009`. `Cpf.mask()` está no kernel desde a Fatia 2, atende o `REQ-BEN-017` e é o único ponto de mascaramento do sistema.

O acervo tem três máscaras diferentes — duas em `CONSBENF` e uma em `RELPGT` —, nenhuma em copycode. A de `RELPGT.NSP:164-167` oculta o prefixo e expõe oito dígitos ao lado do nome completo e da UF.

### O relatório de auditoria consulta pelo que a Fatia 1 construiu

`AuditQuery` já oferece consulta por entidade, por titular e por execução, e a trilha já está particionada por período com índice adequado.

`REQ-REP-016` exige intervalo explícito e recusa consulta sem data inicial. A regra 169 do catálogo registra que o dicionário manda usar o superdescritor `S2` em consultas pesadas e que `RELAUDIT.NSP:111` lê por `DT-EVENT` — fazendo sobre 311 GB exatamente o que a instrução desaconselha.

`REQ-REP-010` é a realização do `REQ-AUD-011`, especificado na Fatia 1 e sem implementação até agora porque não havia relatório onde implementá-lo. O filtro de exclusões vira parâmetro de quem consulta.

### O documento arquivável substitui o formulário contínuo

O destino não tem impressora lógica nem página de 66 linhas. `REQ-REP-015` preserva a cópia arquivável exigida pela retenção de dez anos da Lei 8.159, art. 14, com uma mudança: formato com campos separáveis.

O legado monta a linha com `COMPRESS` sem delimitador, e a separação depende de conhecer as larguras. Dez anos de arquivo em formato que exige o programa original para ser lido.

---

## Modelo de dados

Nenhum. É a propriedade que define este contexto.

As projeções são consultas de agregação nos contextos donos dos dados:

| Consulta | Contexto dono | Requisito |
|---|---|---|
| Totais por região, programa e situação | `payment` | `REQ-REP-001`, `002`, `005` |
| Detalhamento por período e programa | `payment` | `REQ-REP-006`, `007`, `008` |
| Eventos por intervalo e filtros | `audit` | `REQ-REP-010`, `011`, `016` |
| Região e nome do titular | `beneficiary` | `REQ-REP-002`, `013` |

---

## Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Totais regionais do primeiro relatório novo não batem com o último antigo | Desconfiança de quem recebe o documento | Comunicação prévia; o total geral não muda, só a distribuição |
| Agregação sobre 180 milhões de linhas | Relatório inviável | `GROUP BY` sobre a partição do período, com índice; a partição limita o conjunto |
| Consulta de auditoria sem limite | Varredura de 311 GB | Intervalo obrigatório e limite configurável (`REQ-REP-016`) |
| Pagamento órfão em volume alto | Relatório poluído | Sinalização por linha e contagem no rodapé, sem interromper |

---

## Fora deste plano

| Item | Destino |
|---|---|
| Impressão em mainframe, paginação de 66 linhas | Restrição de dispositivo, não regra |
| Recálculo de consolidados históricos | Documentos com valor legal; não são reproduzidos |
| Exportação para formatos de planilha | Fatia própria, se houver demanda |
| Interface de visualização | Fatia de frontend |

---

## Definição de pronto

- [x] Estratégia de agregação decidida, com as duas alternativas erradas registradas.
- [x] Ausência de modelo de dados justificada como garantia, não como lacuna.
- [x] Reaproveitamento do kernel e da trilha identificado.
- [x] Riscos identificados com mitigação.
- [x] Tarefas geradas em [`tasks.md`](tasks.md).
