# Plano técnico — Validação de Documentos

> **Especificação:** [`spec.md`](spec.md) · **Tarefas:** [`tasks.md`](tasks.md)

| Campo | Valor |
|---|---|
| **Fatia de migração** | 1 — Fundações transversais |
| **Destino** | Kernel compartilhado `shared/document` |
| **Requisitos cobertos** | `REQ-DOC-001` a `REQ-DOC-009` |
| **Adiado** | `REQ-DOC-010` — depende do processo de carga, que pertence à Fatia 2 |

---

## Decisões de projeto

### Por que kernel, e não módulo de domínio

A validação de documentos não possui agregado, não persiste dados e não tem linguagem de domínio própria. É função pura. Colocá-la como módulo de domínio criaria um contexto sem dados sob responsabilidade, o que o [mapa de contextos](../../02-modern-spec/bounded-contexts.md) rejeita explicitamente.

Consequência prática: `shared/document` não tem `Repository`, não tem `@Transactional` e não expõe endpoint REST. É consumido por chamada direta.

### Estrutura de pacote

```text
backend/src/main/java/br/gov/sifap/shared/document/
├── DocumentValidator.java        # interface pública do kernel
├── Cpf.java                      # value object
├── Nis.java                      # value object
├── ValidationResult.java         # record de retorno
├── ValidationFailure.java        # enum de motivos
└── internal/
    ├── Modulo11Validator.java    # cálculo compartilhado por CPF e NIS
    ├── CpfValidatorImpl.java
    └── NisValidatorImpl.java
```

Apenas os cinco tipos do nível superior são públicos. O pacote `internal` não é importado por nenhum outro módulo.

### Contrato de retorno

O legado devolve código numérico e mensagem por PDA (`PDAVALID.NSA:31-40`). O equivalente idiomático é um resultado tipado:

```java
public record ValidationResult(boolean valid, ValidationFailure failure) {
    public static ValidationResult ok() { ... }
    public static ValidationResult fail(ValidationFailure reason) { ... }
}
```

`ValidationFailure` é enum com os motivos que `REQ-DOC-004` exige distinguir: `NAO_INFORMADO`, `CARACTERE_INVALIDO`, `DIGITOS_REPETIDOS`, `DIGITO_VERIFICADOR_INVALIDO`.

**Por que enum e não código numérico.** O contrato legado declara o código `1003` para dígitos repetidos e nunca o emite, devolvendo `1001` nos dois casos. Um enum torna o conjunto de motivos exaustivo e verificável em compilação, o que impede repetir a lacuna.

### Value objects em vez de `String`

`Cpf` e `Nis` são criados apenas por fábrica que valida. Isso elimina, por construção, o antipadrão central do legado: chamar a validação e descartar o resultado (`CADBENEF.NSP:161` contra `:164`).

```java
Cpf.of("12345678909")        // lança IllegalArgumentException se inválido
Cpf.tryParse("12345678909")  // devolve Optional<Cpf>
```

Um método que recebe `Cpf` tem a garantia de que o documento é válido. Não existe caminho para um CPF não validado circular no sistema.

### Cálculo de módulo 11 compartilhado

CPF e NIS usam o mesmo algoritmo com pesos diferentes. `Modulo11Validator` recebe os pesos e devolve o dígito. Evita as duplicações que o legado acumulou.

| Documento | Pesos | Fonte |
|---|---|---|
| CPF, 1º dígito | 10 a 2 | `CCVALCPF.NSC:92-105` |
| CPF, 2º dígito | 11 a 2 | `CCVALCPF.NSC:111-125` |
| NIS | 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 | `SUBVALNI.NSN:43-44` |

---

## Modelo de dados

**Nenhum.** Este módulo não cria tabela, não gera migração e não toca no banco.

A ausência de persistência é o que permite entregar a Fatia 1 antes do Cadastro: não há dependência de schema.

---

## Estratégia de testes

### Testes de caracterização

O [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md) determina verificação por teste de caracterização. Aqui existe uma limitação que precisa ficar registrada:

> [!WARNING]
> **Não há ambiente legado disponível para capturar comportamento por execução.** Os vetores de teste são derivados da leitura do código, não de execução real do SIFAP. Isso é mais fraco do que caracterização verdadeira e vale como premissa, não como prova.

Consequência: se o acesso ao ambiente for obtido, os vetores devem ser reconfirmados antes da carga inicial.

### Vetores mínimos

| Caso | Entrada | Esperado | Requisito |
|---|---|---|---|
| Válido | CPF com verificadores corretos | válido | `REQ-DOC-003` |
| Dígitos repetidos | `11111111111` | inválido, `DIGITOS_REPETIDOS` | `REQ-DOC-002` |
| Zeros | `00000000000` | inválido, `NAO_INFORMADO` | `REQ-DOC-004` |
| Não numérico | `1234567890A` | inválido, `CARACTERE_INVALIDO` | `REQ-DOC-001` |
| Primeiro DV errado | 10º dígito alterado | inválido, `DIGITO_VERIFICADOR_INVALIDO` | `REQ-DOC-003` |
| Segundo DV errado | 11º dígito alterado | inválido, `DIGITO_VERIFICADOR_INVALIDO` | `REQ-DOC-003` |
| Prefixo especial inválido | CPF `999...` com DV errado | inválido | `REQ-DOC-008` |
| NIS válido | NIS com verificador correto | válido | `REQ-DOC-005` |
| NIS inválido | verificador alterado | inválido | `REQ-DOC-005` |

**Teste de divergência deliberada.** Os requisitos de nível `C` exigem um teste que documenta a diferença em relação ao legado:

```java
// REQ-DOC-002: divergência deliberada contra CADBENEF.NSP:344-413,
// que aceita dígitos repetidos por não implementar a verificação
@Test
void deve_recusar_cpf_com_digitos_repetidos_ainda_que_o_legado_aceite() { ... }
```

### Ferramentas

JUnit 5 com `@ParameterizedTest` para os vetores. Sem Mockito: não há colaborador a simular. Sem Testcontainers: não há banco.

---

## Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Vetores derivados de leitura, não de execução | Premissa pode estar errada em caso de borda | Registrado acima; reconfirmar se houver acesso ao ambiente |
| Volume de CPFs que se tornam inválidos é desconhecido | Pode inviabilizar a regra na carga | `REQ-DOC-010` exige medição antes de aplicar; a decisão é de negócio |
| `SIFAP-M-16` pode ser validado contra a decisão | Retrabalho na rotina unificada | Componente único e sem estado; troca localizada, por design |
| Aritmética de resto do legado pode não ser truncamento | Vetores de DV podem divergir | Pendência de semântica registrada em `mysteries-found.md`; afeta `REQ-DOC-003` |

---

## Fora deste plano

| Item | Destino |
|---|---|
| `REQ-DOC-010` — sinalização na carga | Fatia 2, junto do processo de carga do Cadastro |
| Validação de RG | Fatia 2, contexto Cadastro |
| Endpoint REST de validação | Não previsto; kernel é consumido internamente |

---

## Definição de pronto

- [x] Estrutura de pacote definida e alinhada ao mapa de contextos.
- [x] Contrato de retorno especificado com motivos exaustivos.
- [x] Estratégia de testes definida, com limitação de caracterização registrada.
- [x] Riscos identificados com mitigação.
- [x] Tarefas geradas em [`tasks.md`](tasks.md).
