# Glossário do SIFAP Legado

> **Trilha:** [Kit do Time](../README.md) › [Estágio 1](README.md) › **Glossário**

**Artefato preenchido pelo time durante o Estágio 1.** Uma tabela com todos os termos, abreviações e siglas encontrados no código Natural/Adabas — a base da linguagem ubíqua para o Estágio 2.

| Campo | Valor |
|---|---|
| **Público-alvo** | Todas as duplas — cada dupla contribui com os termos dos seus programas |
| **Pré-requisitos** | Abrir os arquivos `.NSN` e `.ddm` atribuídos |
| **Estágio** | Estágio 1 — Arqueologia |
| **Resultado esperado** | 30 termos ou mais, com programa de origem e status CONFIRMADO/HIPÓTESE |

> [!NOTE]
> Guia passo a passo: [`GUIDE.md`](GUIDE.md).

---

## Por que o glossário importa

Sistemas legados têm vocabulário próprio, raramente documentado em um lugar acessível — ele vive em nomes de variável, abreviações de campo e comentários de código. Se o time do Estágio 2 não souber o que significam `DSCT`, `BENF`, `PE` ou `CTC`, vai escrever uma especificação baseada em suposições sobre esses termos.

O glossário transforma abreviações de 3 a 6 caracteres em uma linguagem ubíqua compartilhada pelo time inteiro — e dá a base para os nomes de entidades e atributos do modelo de domínio no Estágio 3.

**Erro comum:** marcar um termo como CONFIRMADO sem evidência literal no código ou na documentação histórica. Se você inferiu o significado pelo contexto, marque como HIPÓTESE e identifique quem é responsável pela validação.

---

## Como preencher

| Coluna | O que registrar |
|---|---|
| **Termo** | A abreviação ou sigla exatamente como aparece no código. |
| **Expansão** | O significado completo do termo. |
| **Programa** | O arquivo `.NSN` ou `.ddm` onde o termo foi encontrado. |
| **Contexto** | Explicação breve de como e onde o termo é usado. |
| **Status** | `CONFIRMADO` — evidência literal no código ou na documentação. `HIPÓTESE` — inferido do contexto e aguardando validação. |

### Dica de extração com o modo Ask do GitHub Copilot

Antes de usar o prompt abaixo, cole no chat o conteúdo de 2 a 3 arquivos `.NSN`:

> "Liste todas as abreviações e siglas usadas neste código Natural. Para cada uma, sugira a expansão e marque como 'CONFIRMADO' ou 'HIPÓTESE'."

Compare a sugestão do Copilot com o que você observou diretamente no código. Se coincidirem, registre como CONFIRMADO; caso contrário, registre como HIPÓTESE.

---

## Termos encontrados

**Extraído em:** 2026-09-10, a partir da leitura completa dos 24 membros, dos 4 DDMs e da listagem FDT.
**Critério de status:** `CONFIRMADO` quando o significado está escrito literalmente no código, no DDM ou na documentação histórica. `HIPÓTESE` quando foi inferido do contexto.

### Tipos de membro e estrutura Natural

| # | Termo | Expansão | Programa | Contexto | Status |
|---|---|---|---|---|---|
| 1 | `DDM` | Data Definition Module | `BENEFIC.ddm:1` | Visão lógica que o Natural tem de um arquivo Adabas | CONFIRMADO |
| 2 | `FDT` | Field Definition Table | `FDT-150-BENEFICIARY.txt:8` | Estrutura física do arquivo; nomes curtos de 2 bytes | CONFIRMADO |
| 3 | `PDA` | Parameter Data Area | `PDACALC.NSA:2` | Contrato de parâmetros entre chamador e chamado | CONFIRMADO |
| 4 | `LDA` | Local Data Area | `LDASIFAP.NSL:2` | Área de campos e tabelas compartilhada entre módulos | CONFIRMADO |
| 5 | `CC` | Copycode | `CCVALCPF.NSC:2` | Prefixo de copycode; inserido por `INCLUDE` | CONFIRMADO |
| 6 | `SUB` | Subprograma | `SUBVALCP.NSN:2` | Prefixo de subprograma; alvo de `CALLNAT` | CONFIRMADO |
| 7 | `FNR` | File Number | `PAYMENT.ddm:27` | Identificador numérico do arquivo Adabas | CONFIRMADO |
| 8 | `ISN` | Internal Sequence Number | `FDT-150-BENEFICIARY.txt:128` | Endereço interno do registro no Adabas | CONFIRMADO |
| 9 | `MU` | Multiple-value field | `SOCPROG.ddm:106` | Campo com várias ocorrências | CONFIRMADO |
| 10 | `PE` | Periodic group | `BENEFIC.ddm:85` | Grupo de campos repetido; dependentes usam `1:10` | CONFIRMADO |
| 11 | `NU` | Null suppression | `FDT-150-BENEFICIARY.txt:114` | Campo vazio não ocupa espaço nem entra no índice | CONFIRMADO |
| 12 | `DE` / `UQ` / `FI` | Descriptor / Unique / Fixed storage | `FDT-150-BENEFICIARY.txt:112-113` | Opções de campo na FDT | CONFIRMADO |
| 13 | `SIFAPPRD` | Biblioteca Natural de produção | `SIFAPJ01.jcl:70` | Biblioteca plana onde vivem os 24 membros | CONFIRMADO |
| 14 | `NATBATCH` | Executor Natural em batch | `SIFAPJ01.jcl:46` | Programa z/OS que executa membros Natural | CONFIRMADO |

### Cadastro de beneficiário

| # | Termo | Expansão | Programa | Contexto | Status |
|---|---|---|---|---|---|
| 15 | `BENF` | Beneficiário | `CCAUDIT.NSC:37` | Tipo de entidade na trilha de auditoria | CONFIRMADO |
| 16 | `CPF` | Cadastro de Pessoa Física | `BENEFIC.ddm:40` | Chave do beneficiário; `A11` sem formatação | CONFIRMADO |
| 17 | `NIS` / `PIS` / `PASEP` | Número de Identificação Social | `SUBVALNI.NSN:7`, `BENEFIC.ddm:52` | Documento secundário; validado por módulo 11 desde 2011 | CONFIRMADO |
| 18 | `DV` | Dígito verificador | `CCVALCPF.NSC:92` | Dois dígitos calculados por módulo 11 | CONFIRMADO |
| 19 | `STAT-BENEFICIARY` | Situação cadastral | `BENEFIC.ddm:74` | Domínio fixo `A`=ativo, `S`=suspenso, `C`=cancelado | CONFIRMADO |
| 20 | `I` / `D` (situação) | Inativo / Encerrado | `VALBENEF.NSN:174`, `VALELEG.NSN:139-147` | Valores usados por três programas e ausentes do DDM | HIPÓTESE |
| 21 | `COD-REGION` | Código de região | `BENEFIC.ddm:66` | `01-05` ou `99` (especial) segundo o DDM | CONFIRMADO |
| 22 | `99` (região) | Região internacional/diplomática | `VALELEG.NSN:121` | Rotulada no código; concede elegibilidade sem verificação | HIPÓTESE |
| 23 | `QTY-DEPEND` | Quantidade de dependentes ativos | `BENEFIC.ddm:81` | Contador do titular; incrementado em `CADDEPEN` | CONFIRMADO |
| 24 | `GRP-DEPEND` | Grupo de dependentes | `BENEFIC.ddm:87` | Grupo periódico com máximo de 10 ocorrências | CONFIRMADO |
| 25 | `FI` / `CO` / `IR` | Filho / Cônjuge / Irmão | `CADDEPEN.NSP:22` | Graus de parentesco documentados no comentário da view | CONFIRMADO |
| 26 | `OU` (parentesco) | Outro | `CADDEPEN.NSP:152-156` | Quarto valor aceito, sem significado documentado | HIPÓTESE |
| 27 | `AMT-FAMILY-INCOME` | Renda familiar declarada | `BENEFIC.ddm:78` | Renda total da família, informada no cadastro | CONFIRMADO |
| 28 | `IND-PERCAP-INCOME` | Renda per capita calculada | `BENEFIC.ddm:80` | Campo existente e não utilizado pelos cálculos | CONFIRMADO |
| 29 | `IND-DOCS-OK` | Indicador de documentação regular | `VALELEG.NSN:96` | Exigido na elegibilidade; nenhum programa o grava | CONFIRMADO |

### Programa social e cálculo

| # | Termo | Expansão | Programa | Contexto | Status |
|---|---|---|---|---|---|
| 30 | `PROG` | Programa social | `CCAUDIT.NSC:37` | Tipo de entidade na trilha de auditoria | CONFIRMADO |
| 31 | `TYPE-PROGRAM` | Tipo de programa | `SOCPROG.ddm:31` | Domínio fixo `A`=assistência, `T`=trabalho | CONFIRMADO |
| 32 | `P` (tipo de programa) | Previdência | `CADPROG.NSP:98`, `VALELEG.NSN:201` | Terceiro valor aceito, ausente do domínio do DDM | HIPÓTESE |
| 33 | `AMT-BASE-INDIVIDUAL` | Valor base mensal por pessoa | `SOCPROG.ddm:41` | Base do cálculo do benefício | CONFIRMADO |
| 34 | `FACTOR-K` | Fator especial de correção | `SOCPROG.ddm:47` | Campo declarado em 2008; nenhum programa o grava | CONFIRMADO |
| 35 | `FACTOR-ADJUST` | Fator de ajuste sobre o valor base | `SOCPROG.ddm:52` | Incluído em 2002; aplicado no cadastro e no cálculo | CONFIRMADO |
| 36 | `GRP-CALC-BAND` | Faixas de cálculo por renda | `SOCPROG.ddm:69` | Grupo periódico de até 5 faixas; estrutura ociosa | CONFIRMADO |
| 37 | `COD-ELIGIBILITY` | Código de elegibilidade | `SOCPROG.ddm:65` | Código posicional `A5`; `R` exige NIS, `D` exige dependentes | CONFIRMADO |
| 38 | `MAX-PERCAP-INCOME` | Teto de renda per capita | `SOCPROG.ddm:56` | Comparado contra a renda familiar total no código | CONFIRMADO |
| 39 | `13º` / `THIRTEENTH` | Décimo terceiro benefício | `CALCBENF.NSN:39` | Parcela adicional gerada em dezembro | CONFIRMADO |
| 40 | `AMT-BONUS` | Abono de fim de ano | `PAYMENT.ddm:46` | Acréscimo de 15% para programas do tipo `A` em dezembro | CONFIRMADO |
| 41 | `IPCA` | Índice de preços ao consumidor amplo | `CALCCORR.NSP:9` | Índice da correção retroativa; tabela de 2010 a 2012 | CONFIRMADO |
| 42 | `Plano Verão` | Plano econômico de 1989 | `CALCCORR.NSP:132` | Conversão Cruzado→Cruzeiro; bloco desativado no fonte | CONFIRMADO |

### Pagamento e conciliação

| # | Termo | Expansão | Programa | Contexto | Status |
|---|---|---|---|---|---|
| 43 | `PGTO` | Pagamento | `CCAUDIT.NSC:37` | Tipo de entidade na trilha de auditoria | CONFIRMADO |
| 44 | `STAT-PAYMENT` | Situação do pagamento | `PAYMENT.ddm:60` | Domínio do DDM: `P`=pendente, `G`=gerado, `E`=emitido | CONFIRMADO |
| 45 | `P` / `E` (uso no código) | Pago / Estornado | `BATCHCON.NSP:207`, `RELPGT.NSP:184-193` | Semântica usada pelos programas, oposta à do DDM | HIPÓTESE |
| 46 | `TYPE-PAYMENT` | Tipo de pagamento | `CALCBENF.NSN:39` | `N`=normal, `D`=décimo terceiro, `T`=terceiro | CONFIRMADO |
| 47 | `TYPE-DISC` | Tipo de desconto | `PAYMENT.ddm:51` | Domínio do DDM: `IR/JD/CS/PA/EM/TX/OU/EX` | CONFIRMADO |
| 48 | `J` / `P` / `I` / `S` / `A` | Judicial / Pensão / Imposto / Sindical / Administrativo | `CALCDSCT.NSP:127-167` | Códigos de uma letra usados no cálculo de descontos | CONFIRMADO |
| 49 | `Teto de 30%` | Limite total de descontos | `CALCDSCT.NSP:107` | Percentual do valor bruto; descontos judiciais são exceção | CONFIRMADO |
| 50 | `CNAB 240` | Layout bancário FEBRABAN | `BATCHCON.NSP:11` | Formato do arquivo de remessa e retorno | CONFIRMADO |
| 51 | `SIAFI` | Sistema Integrado de Administração Financeira | `PAYMENT.ddm:88` | Integração prevista; campo nunca populado | CONFIRMADO |
| 52 | `SUPER-CPF-PERIOD` | Superdescritor de CPF + período | `PAYMENT.ddm:127` | Único superdescritor usado no acervo | CONFIRMADO |

### Auditoria, arquivos e normas

| # | Termo | Expansão | Programa | Contexto | Status |
|---|---|---|---|---|---|
| 53 | `COD-ACTION` | Código de ação auditada | `AUDIT.ddm:38-49` | `IN/AL/EX/CO/LG/LO/BT/ER/AU/RE` | CONFIRMADO |
| 54 | `IN` / `AL` / `EX` | Inclusão / Alteração / Exclusão | `AUDIT.ddm:39-41` | Ações de escrita registradas na trilha | CONFIRMADO |
| 55 | `CO` | Consulta | `AUDIT.ddm:41` | Também usado para conciliação a partir de 2014 | CONFIRMADO |
| 56 | `BT` | Batch | `AUDIT.ddm:45` | Evento de ciclo de processamento | CONFIRMADO |
| 57 | `COD-PROFILE` | Perfil do usuário | `AUDIT.ddm:63` | `ADM/OPR/CON/AUD/SUP`; nunca populado | CONFIRMADO |
| 58 | `STAT-BATCH` | Situação da execução batch | `AUDIT.ddm:79` | `S`=sucesso, `E`=erro, `W`=aviso | CONFIRMADO |
| 59 | `CMWKF01` / `CMWKF02` | Arquivos de trabalho z/OS | `SIFAPJ01.jcl:56`, `:63` | Remessa bancária e log de rejeitados | CONFIRMADO |
| 60 | `CMPRINT` / `CMPRT01` | Saídas de impressão | `SIFAPJ02.jcl:56`, `:60` | Log de execução e impressora lógica | CONFIRMADO |
| 61 | `CMSYNIN` | Entrada de comandos Natural | `SIFAPJ01.jcl:69` | Onde o período de processamento é informado | CONFIRMADO |
| 62 | `IN-TCU 63/2010` | Instrução Normativa do TCU | `CCAUDIT.NSC:9-10` | Norma que exige a trilha de auditoria | CONFIRMADO |
| 63 | `PORT. CGTI 213/2010` | Portaria da CGTI | `CCAUDIT.NSC:46` | Proíbe auditar consultas por volume desde 2010 | CONFIRMADO |
| 64 | `NT-SUPDE-011/1998` | Nota técnica de contrato de PDA | `PDAVALID.NSA:26` | Uma PDA por família de rotinas | CONFIRMADO |
| 65 | `NT-SUPDE-014` | Nota técnica da rotina padrão de CPF | `CCVALCPF.NSC:9` | Define `CCVALCPF` como versão padrão | CONFIRMADO |
| 66 | `Lei 8159 art. 14` | Lei de arquivos públicos | `AUDIT.ddm:17`, `BATCHREL.NSP:20` | Retenção mínima de 10 anos | CONFIRMADO |

### Órgãos e unidades

| # | Termo | Expansão | Programa | Contexto | Status |
|---|---|---|---|---|---|
| 67 | `MDAS` | Ministério do Desenvolvimento e Assistência Social | `legacy-sifap/README.md` | Gestão dos programas de transferência de renda | CONFIRMADO |
| 68 | `SENARC` | Secretaria Nacional de Renda de Cidadania | `legacy-sifap/README.md` | Regulamentação e acompanhamento dos benefícios | CONFIRMADO |
| 69 | `CGPB` | Coordenação-Geral de Processamento de Benefícios | `legacy-sifap/README.md` | Operação direta do processamento mensal | CONFIRMADO |
| 70 | `DEFIS` | Departamento de Fiscalização | `legacy-sifap/README.md` | Auditoria e controle de pagamentos indevidos | CONFIRMADO |
| 71 | `CGTI` | Coordenação-Geral de Tecnologia da Informação | `legacy-sifap/README.md` | Interface técnica; autora da Portaria 213/2010 | CONFIRMADO |
| 72 | `SUPDE/DESIF` | Unidade técnica responsável pelo sistema | `PDAVALID.NSA:26` | Origem das notas técnicas `NT-SUPDE-*` | CONFIRMADO |

---

## Termos marcados como hipótese

Seis termos foram inferidos do contexto e aguardam validação. Todos estão registrados como questões em aberto em [`mysteries-found.md`](mysteries-found.md).

| Termo | Questão associada |
|---|---|
| `I` / `D` como situação cadastral | Domínio real difere do declarado no DDM |
| `99` como região especial | `SIFAP-M-13` |
| `OU` como grau de parentesco | Sem significado documentado em nenhuma fonte |
| `P` como tipo de programa | Ausente do domínio de `SOCPROG.ddm:31` |
| `P` / `E` na situação de pagamento | Semântica do código é oposta à do dicionário |

---

## Definição de pronto

- [x] 30 termos ou mais registrados — **72 termos**.
- [x] Todo termo tem um programa de origem com `arquivo:linha`.
- [x] Todo termo tem status CONFIRMADO ou HIPÓTESE.
- [x] As hipóteses estão marcadas e associadas a questões em aberto.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [GUIDE do Estágio 1](GUIDE.md)<br/><sub>Cronograma passo a passo.</sub> | [Relatório de Descoberta](discovery-report.md)<br/><sub>Consolidação final do estágio.</sub> |

<sub>[Voltar ao índice do kit](../README.md)</sub>
