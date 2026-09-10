# ADR-0005: Adotar rotina única de validação de CPF

| Campo | Valor |
|---|---|
| **Status** | Aceita |
| **Data** | 2026-09-10 |
| **Feature relacionada** | `specs/001-validacao-de-documentos/` (Fatia 1) |

---

## Contexto

O SIFAP contém **cinco implementações de validação de CPF com quatro comportamentos distintos**. O mesmo documento é aceito por um módulo e recusado por outro.

| Rotina | Local | Rejeita 11 dígitos iguais | Particularidade |
|---|---|---|---|
| `VALID-CPF-STANDARD` | `CCVALCPF.NSC:39-130` | Sim | Rotina padrão por norma `NT-SUPDE-014` |
| `SUBVALCP` | `SUBVALCP.NSN:44-94` | Sim, por delegação | Recusa `00000000000` com código `1002` |
| `VALID-CPF` | `CADBENEF.NSP:344-413` | **Não** | Aceita `111.111.111-11` |
| `VALID-CPF-COMPLETE` | `VALBENEF.NSN:196-281` | Sim, **exceto prefixo `000`** | Aceita `00000000000` como teste governamental |
| `VALID-CPF-DOC` | `VALDOCS.NSP:137-204` | **Não** | Somado ao desvio de oito prefixos |

O cabeçalho da rotina padrão documenta o problema desde 2011:

> `INLINE COPIES OF THIS ROUTINE EXIST IN OTHER SIFAP MODULES WITH DIFFERENT BEHAVIOR. THE COPIES HAVE NOT BEEN REPLACED. TICKET 6620/2011 - OPEN.`

Fonte: [`CCVALCPF.NSC:32-37`](../../01-archaeology/legacy-sifap/natural-programs/CCVALCPF.NSC).

Agravante estrutural: `CADBENEF.NSP:161` chama `SUBVALCP`, que delega à rotina correta, **e descarta o retorno** na linha `:164`, usando a própria cópia permissiva. O sistema calcula a resposta certa e a joga fora.

Dois desvios adicionais ampliam o conjunto de documentos aceitos: `VALDOCS.NSP:226-241` marca como válido qualquer CPF com um de oito prefixos e zera todos os erros já acumulados; `VALBENEF.NSN:229-245` aceita dígitos repetidos quando começam por `000`.

---

## Opções consideradas

### Opção 1: Preservar as cinco implementações

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Nenhum CPF hoje aceito passa a ser recusado; carga inicial sem rejeições; fidelidade máxima ao comportamento atual |
| **Desvantagens** | Perpetua um ticket aberto há 14 anos; o sistema novo nasceria com cinco definições de "CPF válido"; impossível especificar o comportamento sem descrever qual módulo está chamando |

### Opção 2: Unificar na variante mais permissiva

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Elimina a divergência sem rejeitar nenhum registro existente; carga inicial trivial |
| **Desvantagens** | Consolida o comportamento mais frouxo como regra oficial; contradiz a norma interna vigente; mantém aceitos documentos que o próprio órgão classificou como inválidos em 2005 |

### Opção 3: Unificar na variante normativa (`CCVALCPF`)

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Adota a versão designada por norma; é a mais restritiva e a única corrigida em 2011 para usar resto explícito; fecha o ticket 6620/2011; produz uma definição única de CPF válido |
| **Desvantagens** | CPFs hoje aceitos passam a ser inválidos; exige tratamento explícito na carga inicial; diverge do comportamento legado em pontos verificáveis |

---

## Decisão

**Adotada a Opção 3.** O sistema novo tem uma única rotina de validação de CPF, equivalente ao `CCVALCPF`, aplicada em todos os pontos de entrada.

Três razões sustentam a escolha da variante normativa em vez da permissiva:

1. **É a norma vigente.** `NT-SUPDE-014` designa esta rotina como padrão; as cópias são desvios não autorizados, não alternativas legítimas.
2. **É a única mantida.** Recebeu a rejeição de dígitos repetidos em 2005 e a correção do cálculo de resto em 2011. As cópias não receberam nenhuma das duas.
3. **A permissividade não é regra, é ausência de regra.** `CADBENEF` não decidiu aceitar `111.111.111-11`; ele apenas nunca implementou a verificação que o padrão tem.

Esta é uma correção de nível `C` pelo [ADR-0003](0003-preservacao-de-comportamento.md): elimina um defeito de controle e **não altera valor de benefício**.

Os desvios de `VALDOCS` e `VALBENEF` não são replicados. Prefixo especial de documento deixa de anular outras validações, e não existe exceção de teste governamental em produção.

---

## Consequências

### Positivas

- Uma única definição de "CPF válido" em todo o sistema, especificável em um requisito.
- Fecha o ticket 6620/2011 e a divergência que o próprio legado documenta.
- Um desvio de validação passa a ser mudança em um componente, não em cinco cópias.
- Remove o antipadrão de chamar a rotina corporativa e descartar o retorno.

### Negativas

- **CPFs hoje aceitos passam a ser recusados.** O conjunto inclui documentos de dígitos repetidos aceitos por `CADBENEF.NSP:344-413` e documentos de prefixo especial aceitos por `VALDOCS.NSP:226-241`.
- A carga inicial precisa quantificar esse conjunto antes de aplicar a regra.
- O volume é desconhecido, e há razão para supô-lo relevante: até 2011 a folha não validava o CPF lido do arquivo (`BATCHPGT.NSP:270-272`) e o validador cadastral rodava sem receber dados (`VALBENEF.NSN:11-13`) — treze anos de cadastro sem validação efetiva.

### Regra obrigatória para a carga inicial

> [!WARNING]
> Registros com CPF que se torne inválido **devem ser migrados com sinalização, nunca descartados**. Decidir o destino de um beneficiário é competência de negócio, não da migração. A carga produz relatório de ocorrências para a SENARC e a DEFIS decidirem.

---

## Requisitos relacionados

- `REQ-DOC-001` a `REQ-DOC-006` — validação de CPF, na Fatia 1
- `SIFAP-M-16` — qual rotina é a correta, aguardando validação humana
- `SIFAP-M-15` — CPF de dígitos repetidos iniciado em `000`
- `SIFAP-M-14` — prefixos especiais que anulam erros

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [ADR-0004](0004-mapeamento-dependentes-jpa.md)<br/><sub>Dependentes em JPA.</sub> | [spec 001](../../specs/001-validacao-de-documentos/spec.md)<br/><sub>Requisitos EARS da Fatia 1.</sub> |
