# ADR-0003: Preservar o comportamento do código legado, e não a regra documentada

| Campo | Valor |
|---|---|
| **Status** | Aceita |
| **Data** | 2026-09-10 |
| **Feature relacionada** | Todas as fatias de migração |

---

## Contexto

A arqueologia do Estágio 1 extraiu 175 regras candidatas de 24 membros Natural. Apenas 14 têm confirmação por segunda fonte. Das demais, 62 são inferidas somente do código e 99 permanecem como questões em aberto ([`business-rules-catalog.md`](../../01-archaeology/business-rules-catalog.md)).

O problema não é falta de informação sobre **o que o sistema faz** — o código é explícito. O problema é que, em vários pontos, o comportamento implementado contradiz a regra documentada:

- `CALCBENF.NSN:255-258` calcula o benefício por produto de cinco fatores; a `RN-013` do levantamento de 2012 descreve uma soma.
- `CALCBENF.NSN:174` compara a renda familiar total contra um campo chamado `MAX-PERCAP-INCOME`, que significa renda **per capita**.
- `CALCBENF.NSN:361` e `CALCDSCT.NSP:199` implementam a mesma contribuição social com critérios **inversos** entre si.

Duas circunstâncias impedem resolver isso por consulta:

1. O documento de 2012 declara sobre si mesmo: *"Este documento NÃO foi validado pela equipe técnica e pode conter imprecisões."* O levantamento foi interrompido.
2. Sete das oito pessoas da equipe original saíram. O último integrante com conhecimento abrangente foi transferido em 2017 ([`legacy-sifap/README.md`](../../01-archaeology/legacy-sifap/README.md)).

O sistema paga cerca de 3,8 milhões de benefícios por mês. Qualquer escolha aqui tem efeito financeiro sobre pessoas reais.

---

## Opções consideradas

### Opção 1: Implementar a regra documentada

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Corrige defeitos históricos de uma vez; o sistema novo nasce alinhado à norma escrita |
| **Desvantagens** | A norma escrita é rascunho não validado; alterar a fórmula muda o valor pago a milhões de pessoas sem autorização de quem tem competência para isso; impossível distinguir "corrigir defeito" de "mudar política pública" |

### Opção 2: Aguardar validação de negócio antes de especificar

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | Nenhuma decisão é tomada sem autoridade; risco jurídico mínimo |
| **Desvantagens** | Paralisa o projeto por prazo indeterminado; 8 dos 20 mistérios canônicos dependem de áreas externas; nenhuma das três primeiras fatias avança enquanto a folha espera |

### Opção 3: Preservar o comportamento observável, com exceção para defeito de integridade

| Aspecto | Avaliação |
|---|---|
| **Vantagens** | O sistema novo produz os mesmos valores que o atual, verificável por teste; a discussão normativa acontece depois, com o sistema rodando e capaz de simular alternativas; nenhuma decisão de política pública é tomada por engenharia |
| **Desvantagens** | Replica comportamento provavelmente incorreto; exige disciplina para não confundir "preservado" com "aprovado"; a correção posterior terá custo |

---

## Decisão

**Adotada a Opção 3.**

O legado responde "o que acontece". Só quem tem autoridade sobre a norma responde "o que deveria acontecer". Enquanto a segunda resposta não vier, o sistema novo replica a primeira.

A política opera em três níveis:

| Nível | Tratamento | Verificação |
|---|---|---|
| **P** — Preservar | Replicar exatamente o comportamento atual | Teste de caracterização com dado real |
| **PS** — Preservar com sinalização | Replicar, e expor o efeito em log ou relatório | Teste de caracterização + métrica de ocorrências |
| **C** — Corrigir | Não replicar; implementar o comportamento correto | Teste que falha contra o legado, por design |

**O critério que separa `C` dos demais:** comportamento que grava dado fora do domínio declarado, duplica registro ou impede rastreabilidade legal não é regra de negócio — é falha. Nenhuma das correções previstas altera valor de benefício.

A classificação dos 20 mistérios canônicos está na seção 4 do [`discovery-report.md`](../../01-archaeology/discovery-report.md): 12 preservados, 8 corrigidos.

---

## Consequências

### Positivas

- O Estágio 2 avança sem depender de resposta de SENARC, CGPB, DEFIS ou CGTI.
- Todo requisito de valor passa a ter verificação objetiva: o teste de caracterização compara com o comportamento atual, não com interpretação de documento.
- A distinção entre "replicado" e "aprovado" fica registrada por escrito, evitando que a migração legitime defeitos por omissão.
- Quando a validação vier, o sistema novo já tem instrumentação para medir o impacto da mudança antes de aplicá-la.

### Negativas

- O sistema novo nasce replicando comportamento que provavelmente está incorreto, com destaque para `M-11`, a base de renda.
- Os 5 itens em nível `PS` exigem esforço adicional de instrumentação que não entrega funcionalidade.
- As 8 correções em nível `C` produzem divergência deliberada em relação ao legado, o que invalida comparação direta nesses pontos e exige documentação por teste.
- Se a validação futura contrariar uma preservação, a correção custará mais do que teria custado no início.

---

## Requisitos relacionados

Aplica-se a todos os requisitos de todas as fatias. Cada requisito derivado de mistério cita o nível aplicado.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [Bounded contexts](../../02-modern-spec/bounded-contexts.md)<br/><sub>Decomposição do sistema.</sub> | [ADR-0004](0004-mapeamento-dependentes-jpa.md)<br/><sub>Grupo periódico de dependentes.</sub> |
