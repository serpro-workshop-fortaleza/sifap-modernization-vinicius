# Inventário do Legado — Time `<preencher>`

> **Trilha:** [Kit do Time](../README.md) › [Estágio 1](README.md) › **Inventário**

**Primeiro artefato do Estágio 1.** Varra a estrutura e conte os arquivos sem abrir nenhum programa — use apenas os nomes de arquivo e a estrutura de pastas.

| Campo | Valor |
|---|---|
| **Público-alvo** | Dupla responsável pela varredura inicial |
| **Pré-requisitos** | Acesso ao diretório `legacy-sifap/` |
| **Estágio** | Estágio 1 — Arqueologia, Passo 1 |
| **Resultado esperado** | Contagens corretas, padrões de nomenclatura identificados e 3 itens estranhos sinalizados |

> [!NOTE]
> Monte este inventário sem abrir nenhum programa. Trabalhe apenas com nomes de arquivo e estrutura de pastas. Ele será revisado à medida que o time extrai regras, mapeia dependências e registra mistérios.

**Data:** 2026-09-10
**Dupla responsável:** <!-- preencher -->
**Caminho varrido:** `01-archaeology/legacy-sifap/`
**Natureza deste documento:** primeira varredura, gerada por `/archaeology-kickoff`. Será revisada quando o time extrair regras e mapear dependências.

---

## Estrutura de pastas

```text
01-archaeology/legacy-sifap/
├── HOW-TO-READ-NATURAL.md
├── README.md
├── adabas-ddms/
│   ├── AUDIT.ddm
│   ├── BENEFIC.ddm
│   ├── FDT-150-BENEFICIARY.txt
│   ├── PAYMENT.ddm
│   ├── README.md
│   └── SOCPROG.ddm
├── legacy-docs/
│   ├── BUSINESS-RULES-2012.docx
│   ├── BUSINESS-RULES-2012.md
│   ├── ORIGINAL-ARCHITECTURE-1997.docx
│   ├── ORIGINAL-ARCHITECTURE-1997.md
│   ├── README.md
│   ├── TECHNICAL-MANUAL-SIFAP-2008.docx
│   └── TECHNICAL-MANUAL-SIFAP-2008.md
└── natural-programs/
    ├── BATCHCON.NSP
    ├── BATCHPGT.NSP
    ├── BATCHREL.NSP
    ├── CADBENEF.NSP
    ├── CADDEPEN.NSP
    ├── CADPROG.NSP
    ├── CALCBENF.NSN
    ├── CALCCORR.NSP
    ├── CALCDSCT.NSP
    ├── CCAUDIT.NSC
    ├── CCVALCPF.NSC
    ├── CONSBENF.NSP
    ├── LDASIFAP.NSL
    ├── PDACALC.NSA
    ├── PDAVALID.NSA
    ├── README.md
    ├── RELAUDIT.NSP
    ├── RELPGT.NSP
    ├── SIFAPJ01.jcl
    ├── SIFAPJ02.jcl
    ├── SUBVALCP.NSN
    ├── SUBVALNI.NSN
    ├── VALBENEF.NSN
    ├── VALDOCS.NSP
    └── VALELEG.NSN
```

**Total de diretórios:** 4 (a raiz `legacy-sifap/` + 3 subdiretórios). Profundidade máxima: 1 nível. Não há aninhamento por módulo, o que é coerente com uma biblioteca Natural plana, na qual os membros são resolvidos pelo nome e não por caminho.

**Total de arquivos:** 40.

---

## Contagem de arquivos por tipo

| Extensão | Contagem | Finalidade provável |
|---|---|---|
| `.NSP` | 12 | Programa Natural — ponto de entrada executável, batch ou online |
| `.NSN` | 5 | Subprograma Natural — invocado por `CALLNAT` |
| `.NSA` | 2 | PDA, *Parameter Data Area* — contrato de parâmetros entre chamador e chamado |
| `.NSC` | 2 | Copycode — fragmento inserido por `INCLUDE` em tempo de compilação |
| `.NSL` | 1 | LDA, *Local Data Area* — campos compartilhados entre módulos |
| `.ddm` | 4 | Data Definition Module — visão Natural de um arquivo Adabas |
| `.txt` | 1 | Listagem em texto puro — não é membro Natural |
| `.jcl` | 2 | JCL z/OS — execução batch fora do Natural |
| `.md` | 8 | Documentação em Markdown (5 READMEs, 1 guia de leitura, 3 documentos históricos convertidos) |
| `.docx` | 3 | Documentação histórica em formato binário |

**Leitura das contagens:**

- **22 membros Natural** de código-fonte (`.NSP` + `.NSN` + `.NSA` + `.NSC` + `.NSL`), mais 2 `.jcl`, totalizando 24 artefatos executáveis ou de origem em `natural-programs/`.
- A proporção de **12 programas para 5 subprogramas** sugere um grafo de chamadas raso, e não uma biblioteca profundamente hierárquica. Hipótese a confirmar em `/map-dependencies`.
- Existem **4 `.ddm` para apenas 1 listagem FDT**. Três arquivos Adabas não têm FDT disponível no kit.
- Todos os nomes de membro Natural têm **8 caracteres ou menos**, consistente com o limite da biblioteca Natural.

> Verificação independente: `find 01-archaeology/legacy-sifap -type f | sed 's/.*\.//' | sort | uniq -c | sort -rn`

---

## Padrões da convenção de nomes

Agrupamento feito **somente pelos nomes**, sem abrir arquivos. Padrões com 2 ou mais ocorrências:

| Prefixo | Contagem | Extensões | Hipótese |
|---|---|---|---|
| `CC*` | 2 | `.NSC` | Convenção geral de Natural: `CC` = *copycode*. O prefixo coincide com o tipo de membro indicado pela extensão. |
| `PDA*` | 2 | `.NSA` | Convenção geral de Natural: `PDA` = *Parameter Data Area*. Prefixo e extensão concordam. |
| `SUB*` | 2 | `.NSN` | Convenção geral de Natural: `SUB` = subprograma, alvo de `CALLNAT`. Prefixo e extensão concordam. |
| `LDA*` | 1 | `.NSL` | Convenção geral de Natural: `LDA` = *Local Data Area*. Ocorrência única, listada por concordar com o mesmo padrão estrutural. |
| `BATCH*` | 3 | `.NSP` | Desconhecido — investigar na próxima etapa. É um prefixo de domínio, não uma convenção da linguagem. Correlacionar com os `.jcl`. |
| `CAD*` | 3 | `.NSP` | Desconhecido — investigar na próxima etapa. |
| `CALC*` | 3 | `.NSN`, `.NSP` | Desconhecido — investigar na próxima etapa. Tipos de membro mistos dentro do grupo. |
| `VAL*` | 3 | `.NSN`, `.NSP` | Desconhecido — investigar na próxima etapa. Tipos de membro mistos dentro do grupo. |
| `REL*` | 2 | `.NSP` | Desconhecido — investigar na próxima etapa. |
| `SIFAPJ*` | 2 | `.jcl` | Desconhecido — investigar na próxima etapa. O sufixo numérico (`01`, `02`) sugere uma sequência de jobs. |
| `CONS*` | 1 | `.NSP` | Desconhecido — investigar na próxima etapa. Ocorrência única. |

**Observação sobre o método.** Os quatro primeiros prefixos são sustentados por convenções gerais de Natural **e** confirmados pela extensão do arquivo. Os demais são abreviações de domínio: qualquer significado atribuído a eles agora seria suposição. O time confirma ou refuta cada um ao abrir os programas.

**Segunda camada de nomenclatura.** Vários nomes parecem compostos por dois segmentos de 3 a 4 caracteres (`CAD`+`BENEF`, `CALC`+`BENF`, `VAL`+`BENEF`, `CONS`+`BENF`, `SUB`+`VALCP`). O segundo segmento se repete entre grupos distintos e é candidato a nome de entidade. Confirmar contra os nomes de campo dos `.ddm`.

---

## Itens estranhos (top 3)

> [!NOTE]
> Critérios aplicados: extensão única, nome fora do padrão e inconsistência estrutural. O tamanho dos arquivos **não** foi medido nesta varredura, porque abrir os arquivos está fora do escopo deste passo.

| # | Caminho do arquivo | O que o torna estranho | Investigação sugerida |
|---|---|---|---|
| 1 | `legacy-sifap/natural-programs/SIFAPJ01.jcl`, `SIFAPJ02.jcl` | Única extensão não-Natural dentro da pasta da biblioteca. JCL é z/OS e vive fora do Natural, mas foi guardado junto com os membros. Além disso, há **2 jobs para 12 programas**. | Verificar quais programas são de fato acionados pelo agendador e quais só rodam online. Os `.jcl` provavelmente nomeiam seus programas, o que os torna um ponto de entrada barato para o mapa de dependências. |
| 2 | `legacy-sifap/adabas-ddms/FDT-150-BENEFICIARY.txt` | Única extensão `.txt` do repositório legado. O nome tem 21 caracteres, hífens e dígitos — quebra o limite de 8 caracteres dos membros Natural, ou seja, **não é um membro**, é uma listagem exportada. E existe FDT para apenas 1 dos 4 arquivos Adabas. | Confirmar a qual `.ddm` o número 150 corresponde e por que só esse arquivo teve a FDT preservada. Perguntar se as outras três FDTs existem fora do kit. |
| 3 | `legacy-sifap/natural-programs/CALCBENF.NSN` e `VALDOCS.NSP` | Tipos de membro **mistos dentro do mesmo prefixo**. Em `CALC*`, um é subprograma (`.NSN`) e dois são programas (`.NSP`); em `VAL*`, dois são `.NSN` e um é `.NSP`. Um `.NSN` não pode ser executado diretamente e um `.NSP` não pode ser alvo de `CALLNAT`. | Descobrir por que módulos aparentemente irmãos têm formas de invocação diferentes. Costuma indicar épocas de manutenção distintas ou reuso posterior de uma rotina. Checar as datas de cabeçalho ao abrir. |

**Outros pontos anotados, fora do top 3:**

- `legacy-docs/` mantém **os mesmos 3 documentos em `.docx` e `.md`**. Definir qual versão é a fonte de referência antes de citá-la como evidência.
- Os nomes dos documentos carregam anos (`1997`, `2008`, `2012`) que não se estendem até a última alteração conhecida do sistema. Há uma lacuna documental a registrar como risco, não como conclusão.

---

## Ordem de leitura proposta

> [!IMPORTANT]
> Esta ordem é **hipótese**, derivada apenas de nomes e estrutura. Ela deve mudar assim que o time rastrear as dependências reais com `/map-dependencies`.

1. **Arquivos DDM primeiro** — `BENEFIC.ddm`, `PAYMENT.ddm`, `SOCPROG.ddm`, `AUDIT.ddm` e a listagem `FDT-150-BENEFICIARY.txt`. Eles revelam o modelo de dados antes de qualquer lógica e dão o vocabulário de campos que aparecerá em todos os programas.
2. **Áreas de dados compartilhadas** — `LDASIFAP.NSL`, `PDACALC.NSA`, `PDAVALID.NSA`. São contratos entre módulos: lidos cedo, tornam legível qualquer `CALLNAT` encontrado depois.
3. **Pontos de entrada batch** — `SIFAPJ01.jcl` e `SIFAPJ02.jcl` primeiro, depois `BATCHPGT.NSP`, `BATCHCON.NSP` e `BATCHREL.NSP`. O JCL nomeia o que a produção realmente executa, o que ancora a leitura no comportamento operacional em vez de na suposição.
4. **Candidatos a módulos mais conectados** — `SUBVALCP.NSN`, `SUBVALNI.NSN`, `CCAUDIT.NSC`, `CCVALCPF.NSC`, `CALCBENF.NSN`, `VALBENEF.NSN`, `VALELEG.NSN`. Subprogramas e copycodes só existem para serem chamados ou incluídos, portanto tendem a concentrar arestas de entrada.
5. **Demais programas** — `CAD*`, `CONSBENF.NSP`, `REL*`, `VALDOCS.NSP`, `CALCCORR.NSP`, `CALCDSCT.NSP`, conforme a atribuição de cada dupla e o escopo da feature escolhida.

**Como isso se cruza com a divisão por dupla.** A matriz de leitura em [`LEGACY-EXPLORATION-CHECKLIST.md`](LEGACY-EXPLORATION-CHECKLIST.md) atribui 3 programas a cada dupla e é o gate obrigatório. A ordem acima organiza o **material de apoio** que qualquer dupla pode precisar; ela não substitui a atribuição.

---

## Definição de pronto

- [x] O inventário existe com contagens corretas.
- [x] 3 padrões de nomenclatura ou mais identificados.
- [x] 3 itens estranhos sinalizados.
- [ ] Uma segunda pessoa conferiu as contagens com `find`.
- [ ] O nome do time e a dupla responsável foram preenchidos no cabeçalho.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [GUIDE do Estágio 1](GUIDE.md)<br/><sub>Cronograma passo a passo.</sub> | [Catálogo de Regras](business-rules-catalog.md)<br/><sub>Passo 2 — extração de regras.</sub> |

<sub>[Voltar ao índice do kit](../README.md)</sub>
