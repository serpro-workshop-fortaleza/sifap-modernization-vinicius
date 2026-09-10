# ADR-0004: Mapear o grupo periódico de dependentes como entidade própria

| Campo | Valor |
|---|---|
| **Status** | Aceita |
| **Data** | 2026-09-10 |
| **Feature relacionada** | `specs/003-cadastro-de-beneficiario/` (Fatia 2) |

---

## Contexto

O arquivo `BENEFIC` armazena os dependentes como grupo periódico dentro do registro do beneficiário:

```text
 P 1 DA GRP-DEPEND                        (1:10) PERIODIC GROUP
   2 DB CPF-DEPEND               A   11  N   CPF OR 00000000000
   2 DC NAME-DEPEND              A   60  N
   2 DD DT-BIRTH-DEPEND          N    8  N   YYYYMMDD
   2 DE RELATION                 A    2  F
   2 DF STAT-DEPEND              A    1  F   A=ACTIVE I=INACTIVE D=DISCONNECTED
```

Fonte: [`BENEFIC.ddm:85-93`](../../01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm).

A leitura do Estágio 1 revelou três problemas nessa estrutura:

- **`CADDEPEN.NSP:194-198` nunca grava `STAT-DEPEND`.** Dependentes entram com situação em branco, fora do domínio fixo declarado (regra 31).
- **`QTY-DEPEND` está descolado da realidade.** O DDM o descreve como *"ACTIVE DEPENDENT COUNT"*, mas o programa o incrementa sem marcar o dependente como ativo (`BENEFIC.ddm:81`).
- **O índice de gravação é a própria contagem** (`CADDEPEN.NSP:192-193`), sem procurar ocorrências vagas. Dependentes removidos deixariam buracos nunca reaproveitados.

Além disso, a FDT registra máximo de 10 ocorrências e o código aplica limite efetivo de 6, enquanto a `RN-004` afirma 3 (`SIFAP-M-02`).

---

## Opções considerada

### Opção 1: `@ElementCollection` com `@Embeddable`

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Traduz literalmente a estrutura do legado; o dependente permanece parte do agregado beneficiário; simples de mapear |
| **Desvantagens** | Dependente não tem identidade própria, o que impede referenciá-lo em auditoria; alteração de um dependente reescreve a coleção inteira; dificulta consulta por CPF de dependente |

### Opção 2: Coluna `JSONB`

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Preserva a natureza de "documento aninhado"; leitura em uma única linha, sem junção |
| **Desvantagens** | Perde restrição de domínio no banco, justamente o defeito que se quer corrigir; consulta por dependente exige operador JSON; a regra de limite máximo fica sem apoio do schema |

### Opção 3: `@OneToMany` com entidade `Dependent` própria

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Dependente ganha identidade estável, referenciável em auditoria; `STAT-DEPEND` vira coluna com restrição de domínio; a contagem de ativos passa a ser derivada, não armazenada; consulta por CPF de dependente é natural |
| **Desvantagens** | Diverge da forma física do legado; exige junção na leitura; a carga inicial precisa expandir o grupo periódico em linhas |

---

## Decisão

**Adotada a Opção 3.** Dependente vira entidade própria com `@OneToMany` a partir de `Beneficiary`, com `cascade` e `orphanRemoval`.

Três consequências de projeto decorrem diretamente:

1. **`STAT-DEPEND` passa a ser obrigatório**, com domínio `ATIVO`/`INATIVO`/`DESLIGADO` restrito no schema. Isso corrige a regra 31 sem alterar valor de benefício, portanto é nível `C` pelo [ADR-0003](0003-preservacao-de-comportamento.md).
2. **A contagem de dependentes ativos é derivada**, nunca armazenada. Elimina a divergência estrutural entre `QTY-DEPEND` e a situação real.
3. **O limite máximo é validado na aplicação**, não pela dimensão de um vetor. O valor adotado é o comportamento efetivo do legado, 6, conforme nível `PS` do `SIFAP-M-02` — replicado e exposto em relatório.

O limite físico de 10 do Adabas deixa de existir como restrição técnica. Passa a ser regra de negócio explícita, sujeita a revisão quando o `SIFAP-M-02` for validado.

---

## Consequências

### Positivas

- Dependente passa a ser auditável individualmente, o que o legado não permite.
- A situação do dependente ganha restrição no banco, tornando impossível repetir o defeito da regra 31.
- A contagem de ativos deixa de poder divergir do conteúdo, por construção.
- Alterar o limite de dependentes vira mudança de configuração, não de schema.

### Negativas

- A carga inicial precisa expandir até 10 ocorrências por beneficiário em linhas, sobre 4,2 milhões de registros.
- Registros legados com situação em branco não têm valor correspondente no novo domínio; a carga precisa de decisão explícita sobre eles, registrada como pendência de negócio.
- A leitura do beneficiário com dependentes passa a exigir junção, com custo em consultas de alto volume como a folha mensal.

---

## Requisitos relacionados

- `REQ-BEN-*` — cadastro de dependentes, na Fatia 2
- `SIFAP-M-02` — limite de dependentes, aguardando validação
- Regra 31 do catálogo — dependentes gravados sem situação

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [ADR-0003](0003-preservacao-de-comportamento.md)<br/><sub>Política de preservação.</sub> | [ADR-0005](0005-rotina-unica-validacao-cpf.md)<br/><sub>Validação de CPF.</sub> |
