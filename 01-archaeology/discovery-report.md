# Relatório de Descoberta — Estágio 1: Arqueologia Digital

> **Trilha:** [Kit do Time](../README.md) › [Estágio 1](README.md) › **Relatório de Descoberta**

**Artefato preenchido pelo time ao fim do Estágio 1.** Consolida os achados da arqueologia e é a entrada principal do Estágio 2.

| Campo | Valor |
|---|---|
| **Público-alvo** | Todas as duplas — consolidação ao fim do Estágio 1 |
| **Pré-requisitos** | Catálogo de regras, mapa de dependências e glossário preenchidos |
| **Estágio** | Estágio 1 — Arqueologia |
| **Resultado esperado** | Documento de até 3 páginas com resumo, hipóteses de fatiamento e artefatos de origem |

> [!IMPORTANT]
> Este documento consolida todos os achados do Estágio 1. Preencha cada seção com as conclusões do time. Sem ele, a especificação do Estágio 2 não tem base de evidência.

> [!NOTE]
> Guia passo a passo: [`GUIDE.md`](GUIDE.md).

**Time**: Modernização SIFAP — execução individual
**Data**: 2026-09-10
**Edição**: Migração completa do sistema, fora do formato de imersão de um dia
**Participantes**: 1 pessoa acumulando as 10 personas do kit

---

## 1. Resumo executivo

O SIFAP tem 24 membros Natural, 4 DDMs Adabas e 1 listagem FDT, todos lidos, dos quais se extraíram 175 regras candidatas. Apenas **14 estão confirmadas** por concordância entre código e uma segunda fonte; 62 são inferidas somente do código e 99 são questões em aberto. O sistema é fortemente acoplado por dados e fracamente por chamadas: 94 arestas verificadas, sendo apenas 9 `CALLNAT`, enquanto quatro arquivos Adabas concentram 50 acessos e `PAYMENT` sozinho tem cinco programas que o escrevem. O maior risco para a Etapa 2 é que **a fórmula de cálculo do benefício implementada contradiz a documentada** (`SIFAP-M-09`), resolvido pela política de preservação de comportamento da seção 4, que replica o que o código faz hoje e adia a discussão normativa sem parar o projeto. A confiança para modernizar é **média**: há poucas regras confirmadas por segunda fonte, mas nenhum mistério bloqueia a Etapa 2, porque todos têm tratamento definido.

---

## 2. O que sabemos (confirmado)

### 2.1 Regras de negócio

As 14 regras abaixo são as únicas com evidência em código **e** confirmação por DDM ou pelo levantamento de 2012. Fonte: [business-rules-catalog.md](business-rules-catalog.md).

| # | Regra | Candidato EARS | Origem |
|---|---|---|---|
| 3 | Validação de CPF por módulo 11 com dois dígitos verificadores | Indesejada | `CADBENEF.NSP:344-413` |
| 5 | Data de nascimento é obrigatória | Indesejada | `CADBENEF.NSP:176-180` |
| 11 | Inclusão de beneficiário atribui situação "ativo" | Orientada a evento | `CADBENEF.NSP:245-247` |
| 18 | Toda inclusão ou alteração cadastral gera auditoria | Ubíqua | `CADBENEF.NSP:296-302` |
| 29 | Dependente sem CPF é registrado com zeros | Opcional | `CADDEPEN.NSP:177-178` |
| 33 | Inclusão de dependente gera auditoria como alteração do titular | Orientada a evento | `CADDEPEN.NSP:205-211` |
| 43 | Inclusão de programa social gera auditoria | Orientada a evento | `CADPROG.NSP:141-147` |
| 50 | Rotina padrão de CPF valida dois dígitos com pesos 10 e 11 | Indesejada | `CCVALCPF.NSC:92-128` |
| 53 | O subprograma corporativo delega à rotina padrão | Ubíqua | `SUBVALCP.NSN:72`, `:94` |
| 74 | Valores monetários são truncados em duas casas | Ubíqua | `CALCBENF.NSN:264-266` |
| 82 | CPF inválido interrompe a correção retroativa | Indesejada | `CALCCORR.NSP:157-167` |
| 90 | Desconto judicial não se sujeita ao teto de 30% | Opcional | `CALCDSCT.NSP:128-137` |
| 96 | Programa social inativo impede elegibilidade | Indesejada | `VALELEG.NSN:114-118` |
| 113 | NIS inválido registra erro na validação documental | Indesejada | `VALDOCS.NSP:100-116` |

### 2.2 Dependências

94 arestas verificadas, todas com `arquivo:linha`. Fonte: [dependency-map.md](dependency-map.md).

| Tipo | Total | Observação |
|---|---:|---|
| `CALLNAT` | 9 | Nenhuma referência quebrada |
| `INCLUDE` | 9 | Dois copycodes: `CCAUDIT` (7 usos), `CCVALCPF` (2) |
| `USING` | 23 | `LDASIFAP` com 13 entradas é o membro mais referenciado |
| JCL → programa | 3 | `SIFAPJ01` e `SIFAPJ02` agendam 3 dos 15 programas |
| Acesso a dados | 50 | Nenhum `DELETE`: a exclusão é sempre lógica |

Sete quebras são **documentais**, não de compilação: o cabeçalho do `BATCHPGT` declara chamar `CALCDSCT`, que nenhum programa invoca; 25 milhões de eventos de auditoria são gravados por código ausente do acervo; e três arquivos Adabas de auditoria não têm DDM publicado.

### 2.3 Estruturas de dados

| DDM | FNR | Registros | Escritores | Leitores |
|---|---|---:|---:|---:|
| `BENEFIC` | 150 | 4.201.884 | 2 | 9 |
| `SOCPROG` | 151 | ~45 | 1 | 4 |
| `PAYMENT` | 152 | ~180.000.000 | 5 | 8 |
| `AUDIT` | 153 | 417.884.120 | 8 (via copycode) | 3 |

A listagem FDT confirma 4,2 milhões de beneficiários, grupo periódico de dependentes com máximo de 10 ocorrências e janela estimada de 90 minutos para descarga completa. Fonte: [inventory.md](inventory.md) e [business-rules-catalog.md](business-rules-catalog.md), regras 164 a 175.

---

## 3. O que é arriscado

### 3.1 Questões em aberto aguardando validação humana

Os 20 mistérios canônicos. Nenhum recebeu validação humana. Fonte: [mysteries-found.md](mysteries-found.md), onde constam evidência completa, impacto e hipótese.

| ID | Questão em aberto | Evidência (`path:line`) | Responsável | Status |
|---|---|---|---|---|
| `M-01` | Por que beneficiários com mais de 75 anos são suspensos automaticamente? | `CADBENEF.NSP:250-252` | SENARC | aguardando validação humana |
| `M-02` | Qual é o limite válido de dependentes: 3, 5, 6 ou 10? | `CADDEPEN.NSP:117-120` | SENARC | aguardando validação humana |
| `M-03` | Por que a alteração grava a situação cadastral em branco? | `CADBENEF.NSP:245-252`, `:315` | SUPDE/DESIF | aberta |
| `M-04` | De onde vem a constante `0.347215` do fator de correção? | `CADPROG.NSP:124` | SENARC | aguardando validação humana |
| `M-05` | O batch grava dois pagamentos por beneficiário por ciclo? | `BATCHPGT.NSP:381`, `:390-488` | SUPDE/DESIF | aberta |
| `M-06` | Quantos registros ainda chegam com data em `YYMMDD`? | `BATCHPGT.NSP:327-345` | SUPDE/DESIF | aberta |
| `M-07` | Por que o relatório arredonda e o cálculo trunca? | `BATCHREL.NSP:165-170` | CGPB | aguardando validação humana |
| `M-08` | Por que o número do pagamento nunca é atribuído? | `CALCBENF.NSN:308-319` | SUPDE/DESIF | aberta |
| `M-09` | Qual fórmula de cálculo está vigente: multiplicativa ou aditiva? | `CALCBENF.NSN:255-258` | SENARC | aguardando validação humana |
| `M-10` | Qual das duas contribuições sociais é a vigente? | `CALCBENF.NSN:356-366`, `CALCDSCT.NSP:197-205` | SENARC | aguardando validação humana |
| `M-11` | A faixa de renda usa renda familiar ou per capita? | `CALCBENF.NSN:174` | SENARC | aguardando validação humana |
| `M-12` | A correção aplica índice de um mês ou o acumulado? | `CALCCORR.NSP:229-239` | SENARC | aguardando validação humana |
| `M-13` | Que norma autoriza a região 99 a conceder elegibilidade total? | `VALELEG.NSN:120-128` | SENARC | aguardando validação humana |
| `M-14` | Que norma criou os oito prefixos de CPF que zeram os erros? | `VALDOCS.NSP:226-241` | DEFIS | aguardando validação humana |
| `M-15` | Por que CPF iniciado em `000` é válido como teste? | `VALBENEF.NSN:229-245` | SUPDE/DESIF | aguardando validação humana |
| `M-16` | Qual das cinco rotinas de CPF é a correta? | `CCVALCPF.NSC:32-37` | SUPDE/DESIF | aguardando validação humana |
| `M-17` | Qual norma prevalece sobre auditar consultas? | `CCAUDIT.NSC:45-49`, `CONSBENF.NSP:167-179` | CGTI/MDAS | aguardando validação humana |
| `M-18` | Por que o relatório de auditoria omite as exclusões? | `RELAUDIT.NSP:128-134` | DEFIS | aguardando validação humana |
| `M-19` | `CO` significa consulta ou conciliação? | `AUDIT.ddm:41`, `RELAUDIT.NSP:170-172` | SUPDE/DESIF | aguardando validação humana |
| `M-20` | Que código grava os eventos de login e logout? | `AUDIT.ddm:38-49` | SUPDE/DESIF | aberta |

Há ainda **27 achados bônus** e **4 pendências de semântica da linguagem** no mesmo artefato, também sem validação.

### 3.2 Regras com evidências fracas

**62 regras são inferidas apenas do código.** Elas não estão confirmadas por DDM nem por documento, e sustentam requisitos por seu próprio risco. Quatro grupos concentram o perigo:

- **Domínios divergentes** — `P` significa "pendente" no dicionário e "pago" no código; situação de beneficiário tem 3 valores no DDM e 5 no código.
- **Parâmetros codificados no fonte** — fatores regionais, faixas de renda e índices IPCA estão em `MOVE`, não em dados.
- **Ausências tratadas como regra** — quatro validações documentadas não existem em código; podem nunca ter existido.
- **Comportamento não confirmado da linguagem** — truncamento contra arredondamento afeta todos os valores monetários do sistema.

---

## 4. Fatiamento proposto e ordem de migração

> [!IMPORTANT]
> As cinco fatias abaixo são **hipóteses de limite**, não decisões de arquitetura. Derivam dos agrupamentos do [dependency-map.md](dependency-map.md) e cabem ao `@architect` avaliar na Etapa 2.
> Juntas, cobrem os 24 membros e os 4 DDMs: **o alvo é o sistema completo**. A ordem define por onde começar, não o que descartar.

### Por que migrar em fatias

Duas restrições do próprio sistema impõem sequenciamento:

- **Dependência de dados.** `BATCHPGT` lê `BENEFIC` e `SOCPROG` para escrever `PAYMENT`. Começar pela folha exige as duas outras já modeladas.
- **Massa de regras.** 175 regras candidatas não cabem numa única especificação. O fatiamento distribui a escrita EARS ao longo do projeto.

### Fatia 1 — Fundações transversais

Primeira porque todo módulo que escreve grava auditoria e valida documento: adiar significa retrofitar as duas coisas em código já pronto.

- Programas: `CCAUDIT`, `CCVALCPF`, `SUBVALCP`, `SUBVALNI`
- DDMs: `AUDIT` (gravação)
- Mistérios: `M-16`, `M-17` — tratamento definido na política abaixo
- Evidência: `CCAUDIT` é `INCLUDE` em 7 programas; `SUBVALCP` é o subprograma mais chamado do acervo

### Fatia 2 — Cadastro de Beneficiário

Segunda porque `BENEFIC` é lido por 9 programas e escrito por apenas 2: fronteira mais estreita do sistema, e base de todas as demais fatias.

- Programas: `CADBENEF`, `CADDEPEN`, `VALBENEF`, `VALDOCS`, `CONSBENF`
- DDMs: `BENEFIC`
- Mistérios: `M-01`, `M-02`, `M-03`, `M-15`
- Nenhum deles bloqueia requisito de valor

### Fatia 3 — Catálogo de Programas Sociais

Terceira porque é pequena, tem escritor único e é pré-requisito do cálculo: cerca de 45 registros de parâmetros.

- Programas: `CADPROG`
- DDMs: `SOCPROG`
- Mistérios: `M-04`

### Fatia 4 — Processamento de Folha

Quarta porque consome as três anteriores e concentra 8 dos 20 mistérios.

- Programas: `BATCHPGT`, `CALCBENF`, `VALELEG`, `CALCDSCT`, `CALCCORR`
- DDMs: `PAYMENT` (escrita); lê `BENEFIC` e `SOCPROG`
- Mistérios: `M-05`, `M-06`, `M-08`, `M-09`, `M-10`, `M-11`, `M-12`, `M-13`
- **Iniciar com testes de caracterização**: capturar entrada e saída do cálculo atual como linha de base antes de escrever qualquer regra

### Fatia 5 — Conciliação e Relatórios

Última porque só lê ou atualiza dados que as fatias anteriores produzem.

- Programas: `BATCHCON`, `BATCHREL`, `RELPGT`, `RELAUDIT`
- DDMs: `PAYMENT` (atualização), `AUDIT` (leitura), arquivos `CMWKF01`
- Mistérios: `M-07`, `M-18`

### O que não vira fatia

`PDACALC`, `PDAVALID` e `LDASIFAP` são estruturas da linguagem, não módulos: as PDAs viram assinaturas de método e a LDA vira configuração. `SIFAPJ01` e `SIFAPJ02` viram agendamento na plataforma de destino.

### Política de tratamento dos mistérios

**Regra geral: preservar o comportamento observável do código atual.** O legado responde "o que acontece"; só uma pessoa com autoridade sobre a norma responde "o que deveria acontecer". Replicar o comportamento e registrar a dúvida permite migrar sem esperar por terceiros.

A regra tem exceção: **defeito de integridade não se preserva.** Comportamento que grava dado fora do domínio declarado, duplica registro ou impede rastreabilidade legal não é regra de negócio a replicar — é falha a corrigir. Daí os três níveis.

| Nível | Tratamento | Verificação |
|---|---|---|
| **P** — Preservar | Replicar exatamente o comportamento atual | Teste de caracterização com dado real |
| **PS** — Preservar com sinalização | Replicar, e expor o efeito em log ou relatório | Teste de caracterização + métrica de ocorrências |
| **C** — Corrigir | Não replicar; implementar o comportamento correto | Teste que falha contra o legado, por design |

#### Decisão por mistério

| ID | Nível | Decisão adotada |
|---|---|---|
| `M-01` | PS | Manter suspensão automática acima de 75 anos; registrar cada ocorrência em log de negócio |
| `M-02` | PS | Manter o limite efetivo de 6 dependentes; expor a divergência com a `RN-004` em relatório |
| `M-03` | **C** | Situação cadastral é obrigatória e restrita ao domínio; alteração preserva o valor anterior |
| `M-04` | P | Replicar a constante `0.347215` como parâmetro nomeado e configurável |
| `M-05` | **C** | Um pagamento por beneficiário por período; a dupla gravação é defeito |
| `M-06` | PS | Aceitar `YYMMDD` na leitura com a janela de século; gravar sempre `YYYYMMDD` |
| `M-07` | P | Replicar o arredondamento do consolidado, com nota de divergência no próprio relatório |
| `M-08` | **C** | Número de pagamento obrigatório, gerado por sequência do banco |
| `M-09` | P | Replicar a fórmula multiplicativa de cinco fatores, que é a que roda |
| `M-10` | P | Replicar os 3% do `CALCBENF`, único caminho efetivamente executado |
| `M-11` | PS | Replicar o uso da renda familiar; expor em relatório a diferença contra a renda per capita |
| `M-12` | P | Replicar o índice de mês único; falhar explicitamente quando o ano não existir na tabela |
| `M-13` | PS | Manter a região 99 como dispensa de elegibilidade; auditar toda concessão por essa via |
| `M-14` | **C** | Prefixo especial de CPF não anula outras validações |
| `M-15` | **C** | CPF de dígitos repetidos é inválido, sem exceção de prefixo |
| `M-16` | **C** | Rotina única de CPF, equivalente ao `CCVALCPF`, versão normativa por `NT-SUPDE-014` |
| `M-17` | **C** | Auditar consulta a dado pessoal, com retenção configurável por tipo de ação |
| `M-18` | **C** | Relatório de auditoria exibe exclusões; filtro vira opção do usuário |
| `M-19` | **C** | Consulta e conciliação recebem códigos distintos; a migração separa por período de origem |
| `M-20` | P | Fora do escopo do acervo; a plataforma nova registra autenticação nativamente |

**Doze preservados, oito corrigidos.** As oito correções são defeitos de integridade ou de controle: nenhuma altera valor de benefício. Todas as decisões que afetam quanto uma pessoa recebe estão em `P` ou `PS`.

> [!WARNING]
> **`M-15` e `M-16` têm efeito sobre a carga inicial.** CPFs de dígitos repetidos e de prefixo especial, hoje aceitos por `CADBENEF.NSP:344-413` e `VALDOCS.NSP:226-241`, passam a ser inválidos. A carga precisa quantificar esse conjunto e migrá-lo sinalizado, nunca descartá-lo: a decisão sobre esses registros é de negócio.

> [!NOTE]
> Toda linha desta tabela vira um ADR na Etapa 2 e permanece **reversível**. Os 20 mistérios continuam sem validação humana em [`mysteries-found.md`](mysteries-found.md); o que esta política define é como proceder enquanto a validação não vem.

---

## 5. Artefatos de origem

| Artefato | Caminho | Conteúdo |
|---|---|---|
| Inventário | [inventory.md](inventory.md) | 40 arquivos, 11 padrões de nomenclatura, 3 itens estranhos |
| Regras de Negócio | [business-rules-catalog.md](business-rules-catalog.md) | 175 regras: 14 confirmadas, 62 inferidas, 99 mistérios |
| Dependências | [dependency-map.md](dependency-map.md) · [.mmd](dependency-map.mmd) | 94 arestas, 0 quebras de código, 7 quebras documentais |
| Questões em aberto | [mysteries-found.md](mysteries-found.md) | 20 canônicos, 27 bônus, 4 pendências de semântica; achados posteriores em seção própria |
| Glossário | [glossary.md](glossary.md) | 72 termos: 66 confirmados, 6 hipóteses |

---

## 6. Aprovação do time

- Revisado por: Modernização SIFAP — execução individual
- Data: 2026-09-10
- Confiança: **Média**

**Critério da avaliação.** Média, e não alta, porque apenas 14 de 175 regras têm confirmação por segunda fonte e 8 dos 20 mistérios canônicos bloqueiam requisitos de valor. Média, e não baixa, porque os 24 membros foram lidos integralmente, toda regra tem `arquivo:linha` utilizável como `source_legacy:`, o grafo de dependências não tem nenhuma referência quebrada de código, e as três primeiras fatias da ordem de migração não dependem de nenhum mistério bloqueante de valor.

**Escopo confirmado:** migração do sistema completo, executada em cinco fatias na ordem da seção 4.

---

## Definição de pronto

- [x] Resumo com no máximo 5 frases.
- [x] De 3 a 5 hipóteses de fatiamento documentadas e rotuladas como hipóteses.
- [x] Questões em aberto com evidência `path:line`, responsável e status.
- [x] Todos os artefatos de origem referenciados por caminho relativo.
- [x] Ordem de migração definida, com justificativa de dependência por fatia.
- [x] Identificação e aprovação preenchidas, com critério de confiança explícito.
- [x] Política de tratamento definida para os 20 mistérios, com nível e caminho de reversão.
- [x] **Nenhum bloqueio pendente para a Etapa 2.**

**O que acompanha a Etapa 2 sem bloqueá-la:** os 20 mistérios permanecem sem validação humana em [`mysteries-found.md`](mysteries-found.md). A política da seção 4 define como proceder até que ela venha, e cada decisão é reversível por ADR.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [GUIDE do Estágio 1](GUIDE.md)<br/><sub>Cronograma passo a passo.</sub> | [Estágio 2 — Especificação moderna](../02-modern-spec/README.md)<br/><sub>Handoff H1 e início do EARS.</sub> |

<sub>[Voltar ao índice do kit](../README.md)</sub>
