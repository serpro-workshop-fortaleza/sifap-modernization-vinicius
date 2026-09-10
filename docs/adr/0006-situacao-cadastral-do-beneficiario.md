# ADR-0006: Tornar a situação cadastral obrigatória e imutável em alterações de dados

| Campo | Valor |
|---|---|
| **Status** | Aceita |
| **Data** | 2026-09-10 |
| **Feature relacionada** | `specs/003-cadastro-de-beneficiario/` (Fatia 2) |
| **Mistério tratado** | `SIFAP-M-03` |

---

## Contexto

O `CADBENEF` grava a situação cadastral em branco em toda alteração de beneficiário com 75 anos ou menos. A cadeia que produz o efeito é curta e está inteiramente visível no fonte:

1. A tela de captura não possui campo de situação cadastral ([`CADBENEF.NSP:117-135`](../../01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP)).
2. `#STATUS` só recebe valor em dois pontos: `:246`, quando a operação é inclusão, e `:251`, quando a idade supera 75 anos.
3. O ramo de alteração grava `#STATUS` sobre o registro em `:314`, sem lê-lo antes.

Numa alteração de endereço de um beneficiário de 40 anos, `#STATUS` nunca foi atribuído nesta execução. O valor gravado é o conteúdo residual da variável — em branco.

### Por que isso não é apenas um campo sujo

A situação cadastral é descritor (`D`) e compõe dois superdescritores do arquivo: `S2 SUPER-UF-STAT` e `S3 SUPER-PROG-STAT` ([`BENEFIC.ddm:147-152`](../../01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm)). Qualquer consulta gerencial por situação omite os registros em branco.

O efeito grave, porém, está no cálculo. A verificação de elegibilidade tem esta forma:

```text
IF #STATUS-BENEF NE 'A'
  IF #STATUS-BENEF = 'S'      ... inelegível
  ELSE IF 'C' OR 'D'          ... inelegível
  ELSE IF 'I'                 ... inelegível
```

Fonte: [`VALELEG.NSN:133-151`](../../01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN).

Uma situação em branco entra no bloco externo, não casa com nenhum dos três testes internos e **sai sem alterar `#ELIGIBLE`**. O beneficiário é tratado como elegível.

Ou seja: um beneficiário suspenso que sofra qualquer alteração cadastral perde a marca de suspensão e volta a ser elegível ao pagamento. O caminho é `CADBENEF.NSP:314` → `BENEFIC.CE` → `VALELEG.NSN:133`.

Não é possível medir quantos registros estão nessa condição sem acesso à base.

---

## Opções consideradas

### Opção 1: Preservar o comportamento

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Fidelidade máxima ao legado; nenhuma diferença de comportamento a explicar |
| **Desvantagens** | Grava valor fora do domínio fixo declarado no dicionário; reativa beneficiários suspensos de forma silenciosa; mantém a trilha de auditoria sem meio de distinguir mudança deliberada de efeito colateral |

### Opção 2: Situação editável na alteração cadastral

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Resolve o branco; a interface passa a expor o campo que o legado esconde |
| **Desvantagens** | Cria uma capacidade que o legado não tem — mudar situação por digitação, sem motivo registrado; a `RN-009` já exige autorização de supervisor para alterar CPF, e não há norma equivalente para situação; transforma um defeito em funcionalidade não solicitada |

### Opção 3: Situação obrigatória, com domínio fechado, alterada apenas por operação própria

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Elimina o valor fora de domínio na origem; alteração cadastral deixa de ter efeito sobre situação, o que é o comportamento pretendido; cada mudança carrega motivo e vira evento (`REQ-BEN-006`); o defeito de reativação silenciosa desaparece por construção |
| **Desvantagens** | Exige decidir o que fazer com os registros que já estão em branco na carga; a operação de mudança de situação não existe no acervo e precisa ser desenhada |

---

## Decisão

**Adotada a opção 3.**

A situação cadastral é obrigatória, restrita ao domínio `A`, `S`, `C`, `I` e `D` declarado em `BENEFIC.ddm:74`, e **não é afetada por alteração de dados cadastrais**. Mudança de situação é operação própria, que exige motivo e publica `BeneficiaryStatusChanged`.

Enquadra-se no critério de correção do [ADR-0003](0003-preservacao-de-comportamento.md) por dois dos três testes: grava dado fora do domínio declarado e impede rastreabilidade — a trilha registra "alteração cadastral" onde houve, de fato, reativação de um beneficiário suspenso.

### O que esta decisão não faz

**Não define quais transições entre situações são válidas.** Se um beneficiário cancelado pode voltar a ativo, ou se um desligado é terminal, são perguntas de norma, não de código. O legado não responde: nenhum programa do acervo altera situação, exceto a suspensão automática por idade.

Registrar o motivo de cada mudança é o que permite responder essa pergunta depois, com dados. Inventar a máquina de estados agora seria criar regra sem fonte — o que o `@architect` rejeita.

### Situação inicial

Preservada do legado: inclusão atribui `A` (`CADBENEF.NSP:246`, regra 11, confirmada pelo dicionário).

### Suspensão automática por idade

Preservada, com sinalização, conforme `REQ-BEN-006` e a decisão `PS` do `SIFAP-M-01`. Passa a ser uma mudança de situação como outra qualquer: com motivo, com evento e com valor anterior registrado.

---

## Consequências

### Positivas

- Nenhum registro novo entra na base com situação fora do domínio.
- A reativação silenciosa de beneficiários suspensos deixa de ser possível.
- A trilha passa a distinguir mudança de situação de alteração de cadastro, o que o legado não faz.
- Consultas por situação deixam de ter uma população invisível.

### Negativas

- A carga inicial precisa tratar registros com situação ausente. O `REQ-BEN-020` determina migrar sinalizado, nunca descartar, e o `REQ-BEN-021` exige inventariar os valores encontrados antes de qualquer decisão.
- A quantidade de registros afetados é desconhecida e pode ser alta: o defeito existe desde 1997 e atinge toda alteração de beneficiário com 75 anos ou menos.
- A operação de mudança de situação é comportamento novo. Ela não amplia o que o sistema faz — o legado já muda situação, pela via automática — mas a torna explícita e auditável.

### Riscos

| Risco | Mitigação |
|---|---|
| Volume de registros em branco inviabiliza a carga | `REQ-BEN-021` mede antes de decidir; a decisão sobre o destino desses registros é de negócio |
| Atribuir situação a registros em branco muda quem é elegível | Nenhuma atribuição automática. O registro migra sinalizado e a situação permanece pendente até decisão explícita |
| A ausência de máquina de estados permite transições incoerentes | Todo motivo é registrado; a regra pode ser derivada dos dados depois, com evidência |

> [!WARNING]
> **Nenhum registro em branco recebe situação atribuída pela migração.** Decidir se um beneficiário cuja marca de suspensão foi apagada em 2014 está hoje ativo ou suspenso é competência de negócio, e a resposta afeta pagamento.

---

## Reversibilidade

Alta para a parte estrutural, baixa para a parte de dados.

Voltar a permitir situação em branco é uma mudança de restrição, localizada. Já a carga, uma vez executada, terá classificado milhões de registros — e reverter exige reprocessar. É por isso que o `REQ-BEN-021` exige medir antes, e o `REQ-BEN-020` proíbe atribuição automática.

O `SIFAP-M-03` permanece **aberto** em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md). Esta decisão define como proceder enquanto a validação humana não vem.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [ADR-0005](0005-rotina-unica-validacao-cpf.md)<br/><sub>Rotina única de validação de CPF.</sub> | [spec 003](../../specs/003-cadastro-de-beneficiario/spec.md)<br/><sub>Cadastro de Beneficiário.</sub> |
