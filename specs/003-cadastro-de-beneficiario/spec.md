# Especificação — Cadastro de Beneficiário

> **Fatia de migração:** 2 — Cadastro
> **Destino arquitetural:** contexto delimitado (`beneficiary`)
> **Dados sob responsabilidade:** `BENEFIC` (FNR 150) — 4.201.884 registros

| Campo | Valor |
|---|---|
| **Estágio** | Estágio 2 — Especificação |
| **Data** | 2026-09-10 |
| **ADRs aplicáveis** | [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md), [ADR-0004](../../docs/adr/0004-mapeamento-dependentes-jpa.md), [ADR-0005](../../docs/adr/0005-rotina-unica-validacao-cpf.md), [ADR-0006](../../docs/adr/0006-situacao-cadastral-do-beneficiario.md) |
| **Entrada** | [`business-rules-catalog.md`](../../01-archaeology/business-rules-catalog.md), regras 1-35, 105-115 e 139 |
| **Legado de origem** | `CADBENEF`, `CADDEPEN`, `VALBENEF`, `VALDOCS`, `CONSBENF` |

---

## Contexto

O `BENEFIC` é o arquivo do qual todo o resto do SIFAP deriva: o próprio dicionário registra que o volume de `PAYMENT` e de `AUDIT` decorre desta população (`BENEFIC.ddm:174-176`). É também o contexto com a fronteira de escrita mais estreita do sistema — nove leitores e dois escritores.

A leitura dos cinco programas encontrou um padrão que se repete em todos eles e organiza esta especificação:

> **O cadastro chama as validações corporativas e não usa o resultado.**

- `CADBENEF.NSP:161` chama `SUBVALCP` e o teste seguinte, em `:164`, examina `#CPF-VALID` — variável da cópia interna, não o retorno da chamada.
- `CADBENEF.NSP:263` chama `VALBENEF` e `:266` apenas escreve um aviso na tela.
- `CADDEPEN.NSP:170` monta a mensagem `INVALID DEPENDENT CPF` e não a exibe nem interrompe.

Os três pontos citam os mesmos tickets — `6620/2011` e `6621/2011` — com a mesma justificativa: *pending Benefits Department review*. A revisão está pendente há quinze anos, e nesse intervalo a validação corporativa existe, roda, custa tempo de execução e não tem efeito nenhum.

O segundo achado é o `SIFAP-M-03`, agora confirmado na fonte: a tela do `CADBENEF` **não possui campo de situação cadastral**, e `#STATUS` só recebe valor quando a operação é inclusão (`:246`) ou quando a idade supera 75 anos (`:251`). Em toda alteração de beneficiário com 75 anos ou menos, `:314` grava branco sobre a situação vigente.

## Escopo

**Dentro:** identidade do beneficiário, dados cadastrais nucleares, situação cadastral, dependentes, consulta e carga inicial.

**Fora:** dados bancários, biometria, óbito, bloqueio judicial e representante legal — campos que existem no dicionário e que nenhum dos cinco programas lê ou escreve. Cada um pertence à fatia que o utiliza.

> [!NOTE]
> O `BENEFIC.ddm` declara 69 campos elementares. Os cinco programas desta fatia tocam 24. Modelar os 45 restantes sem um consumidor seria especificar sem evidência de uso — exatamente o que o `REQ-BEN-019` corrige em relação ao legado.

---

## Requisitos

### REQ-BEN-001 — Identificar o beneficiário pelo CPF

O sistema DEVE identificar cada beneficiário por um CPF único, recusando inclusão cujo CPF já exista.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm#L40
- nível: `P` — preservado; o dicionário declara `AB NUM-CPF A 11 U`
- AC-001.1: Dado um CPF já cadastrado, Quando uma inclusão for solicitada, Então a operação é recusada com motivo de duplicidade.
- AC-001.2: Dado um CPF inexistente, Quando uma alteração for solicitada, Então a operação é recusada com motivo de registro não encontrado.

### REQ-BEN-002 — Exigir os dados nucleares na inclusão

SE o CPF, o nome, a data de nascimento ou o sexo não forem informados, ENTÃO o sistema DEVE recusar a inclusão.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP#L145-L186
- nível: `P` — preservado
- AC-002.1: Dado um cadastro sem nome, Quando incluído, Então a operação é recusada.
- AC-002.2: Dado um cadastro com sexo diferente de `M` ou `F`, Quando incluído, Então a operação é recusada.

> O dicionário admite `I` para sexo indefinido (`BENEFIC.ddm:45`) e o programa não aceita. A entrada preserva a restrição do programa; o domínio real dos dados é medido pelo `REQ-BEN-021`.

### REQ-BEN-003 — Impedir a gravação quando a validação cadastral falhar

SE a validação cadastral de CPF, NIS, nome, unidade federativa ou situação apontar inconsistência, ENTÃO o sistema DEVE recusar a gravação.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP#L263-L268
- nível: `C` — corrigido; o legado executa a validação e apenas escreve um aviso
- AC-003.1: Dado um beneficiário cuja validação retorne inconsistência, Quando a gravação for solicitada, Então a operação é recusada e nenhum registro é alterado.
- AC-003.2: Dado um NIS inválido, Quando a inclusão for solicitada, Então a operação é recusada, e não apenas sinalizada.
- AC-003.3: Dado um beneficiário válido, Quando gravado, Então a validação não é executada duas vezes sobre o mesmo dado.

> [!WARNING]
> Esta correção tem efeito sobre a carga inicial, tratado no `REQ-BEN-020`. Registros hoje aceitos em modo aviso passam a ser inválidos.

### REQ-BEN-004 — Manter a situação cadastral obrigatória e dentro do domínio

O sistema DEVE manter para cada beneficiário uma situação cadastral obrigatória, restrita ao domínio declarado no dicionário.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm#L74
- nível: `C` — corrigido; decisão do `SIFAP-M-03`, registrada no [ADR-0006](../../docs/adr/0006-situacao-cadastral-do-beneficiario.md)
- AC-004.1: Dada uma tentativa de gravar situação fora de `A`, `S`, `C`, `I` ou `D`, Quando executada, Então a operação é recusada.
- AC-004.2: Dada uma tentativa de gravar situação em branco, Quando executada, Então a operação é recusada.

### REQ-BEN-005 — Preservar a situação vigente em alterações cadastrais

QUANDO uma alteração de dados cadastrais for gravada, o sistema DEVE preservar a situação cadastral vigente.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP#L314
- nível: `C` — corrigido; o legado grava branco sobre a situação vigente
- AC-005.1: Dado um beneficiário ativo, Quando seu telefone for alterado, Então sua situação permanece ativa.
- AC-005.2: Dada uma mudança de situação, Quando solicitada, Então ela ocorre por operação própria, distinta da alteração cadastral.
- AC-005.3: Dado um beneficiário suspenso, Quando seus dados cadastrais forem alterados, Então ele permanece suspenso e continua inelegível.

> A tela do `CADBENEF` não tem campo de situação. O valor gravado em `:314` é o conteúdo residual de `#STATUS`, que só é preenchido na inclusão ou acima de 75 anos.

> [!WARNING]
> **O branco reativa o beneficiário.** A verificação de elegibilidade testa `NE 'A'` e depois compara com `S`, `C`, `D` e `I` (`VALELEG.NSN:133-151`). Um valor em branco não casa com nenhum dos quatro e sai do bloco sem alterar o indicador de elegibilidade. Um beneficiário suspenso que sofra qualquer alteração cadastral volta a ser elegível ao pagamento, em silêncio.

### REQ-BEN-006 — Registrar toda mudança de situação cadastral

QUANDO a situação cadastral de um beneficiário mudar, o sistema DEVE publicar um evento contendo a situação anterior, a nova e o motivo.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP#L250-L251
- nível: `PS` — preservado com sinalização; exigência do `SIFAP-M-01`
- AC-006.1: Dado um beneficiário que complete a idade de suspensão automática, Quando a suspensão ocorrer, Então existe evento com motivo de suspensão por idade.
- AC-006.2: Dado um período qualquer, Quando as suspensões automáticas forem consultadas, Então é possível contá-las e identificá-las.

> A suspensão acima de 75 anos é preservada porque afeta quem recebe, e o `ADR-0003` só autoriza corrigir defeito de integridade. O que muda é a visibilidade: hoje a suspensão é indistinguível de qualquer outra alteração de campo.

### REQ-BEN-007 — Calcular a idade pela diferença de anos civis

O sistema DEVE calcular a idade do beneficiário pela diferença entre o ano corrente e o ano de nascimento.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP#L242
- nível: `PS` — preservado com sinalização
- AC-007.1: Dado um beneficiário nascido em dezembro, Quando a idade for calculada em janeiro, Então o resultado é o mesmo do legado.
- AC-007.2: Dada a apuração de idade, Quando ela determinar suspensão automática, Então a data de nascimento e a idade apurada constam do evento.

> O cálculo ignora mês e dia: a idade muda em 1º de janeiro, não no aniversário, com margem de até onze meses. É defeito, mas altera quem é suspenso — e portanto quem recebe. Corrigi-lo é decisão de negócio, não de migração.

### REQ-BEN-008 — Limitar os dependentes ativos por titular

SE o titular já possuir seis dependentes ativos, ENTÃO o sistema DEVE recusar a inclusão de novo dependente.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP#L117
- nível: `PS` — preservado com sinalização; decisão do `SIFAP-M-02`, registrada no [ADR-0004](../../docs/adr/0004-mapeamento-dependentes-jpa.md)
- AC-008.1: Dado um titular com cinco dependentes ativos, Quando um dependente for incluído, Então a inclusão é aceita.
- AC-008.2: Dado um titular com seis dependentes ativos, Quando um dependente for incluído, Então a inclusão é recusada.
- AC-008.3: Dada a divergência com a `RN-004`, Quando o relatório de divergências for gerado, Então o limite efetivo aplicado consta dele.

> O teste legado é `IF #NUM-DEPEND > 5`, e portanto aceita o sexto. O grupo periódico comporta dez (`BENEFIC.ddm:86`) e a `RN-004` documenta outro número. Preserva-se o limite que roda.

### REQ-BEN-009 — Derivar a contagem de dependentes ativos

O sistema DEVE derivar a quantidade de dependentes ativos a partir dos dependentes registrados, sem armazená-la como valor independente.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm#L81
- nível: `C` — corrigido; o legado incrementa um contador armazenado
- AC-009.1: Dado um dependente inativado, Quando a contagem for consultada, Então ela não o inclui.
- AC-009.2: Dada qualquer operação sobre dependentes, Quando concluída, Então a contagem e os dependentes registrados nunca divergem.

> O dicionário define `CK QTY-DEPEND` como *active dependent count*, e `CADDEPEN.NSP:192` incrementa o contador sem consultar a situação de ninguém. Pior: `:176` percorre a verificação de duplicidade usando esse contador, de modo que um contador defasado deixa duplicatas passarem.

### REQ-BEN-010 — Exigir situação do dependente

O sistema DEVE registrar cada dependente com situação obrigatória, restrita ao domínio declarado no dicionário.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm#L93
- nível: `C` — corrigido; o legado nunca preenche o campo
- AC-010.1: Dado um dependente incluído, Quando consultado, Então sua situação é `A`, `I` ou `D`.
- AC-010.2: Dada uma tentativa de gravar dependente sem situação, Quando executada, Então a operação é recusada.

### REQ-BEN-011 — Admitir dependente sem CPF

ONDE o dependente não possuir CPF, o sistema DEVE registrar o vínculo sem documento, sem impedir outros dependentes na mesma condição.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP#L176-L182
- nível: `P` — preservado
- AC-011.1: Dado um dependente sem CPF, Quando incluído, Então a inclusão é aceita.
- AC-011.2: Dados dois dependentes sem CPF no mesmo titular, Quando incluídos, Então nenhum é recusado por duplicidade.

> O legado representa a ausência por onze zeros (`BENEFIC.ddm:88`) e exclui esse valor da verificação de duplicidade. A ausência passa a ser representada como ausência, não como um valor sentinela.

### REQ-BEN-012 — Recusar dependente com CPF já vinculado ao titular

SE o CPF informado já estiver vinculado como dependente do mesmo titular, ENTÃO o sistema DEVE recusar a inclusão.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP#L175-L188
- nível: `C` — corrigido; a verificação legada percorre um contador que pode estar defasado
- AC-012.1: Dado um CPF já vinculado ao titular, Quando um dependente for incluído com ele, Então a inclusão é recusada.
- AC-012.2: Dado um titular cuja contagem esteja defasada, Quando a duplicidade for verificada, Então ela é detectada mesmo assim.

### REQ-BEN-013 — Validar o CPF do dependente

SE o CPF informado para um dependente for inválido, ENTÃO o sistema DEVE recusar a inclusão.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP#L168-L171
- nível: `C` — corrigido; o legado monta a mensagem de erro e a descarta
- AC-013.1: Dado um CPF de dependente inválido, Quando a inclusão for solicitada, Então ela é recusada.
- AC-013.2: Dado um dependente sem CPF, Quando a inclusão for solicitada, Então a validação não é aplicada.

> O comentário do legado declara *warning mode*, mas não há sequer aviso: `#MSG` recebe a mensagem, nunca é exibida, e `#ERR` não é ligado. Um CPF de dependente inválido entra na base em silêncio.

### REQ-BEN-014 — Impedir dependente em titular cancelado ou desligado

SE a situação do titular for cancelada ou desligada, ENTÃO o sistema DEVE recusar a inclusão de dependente.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP#L110
- nível: `P` — preservado
- AC-014.1: Dado um titular cancelado, Quando um dependente for incluído, Então a inclusão é recusada.
- AC-014.2: Dado um titular suspenso, Quando um dependente for incluído, Então a inclusão é aceita.

> A permissão para titular suspenso ou inativo é comportamento do legado, não descuido de redação: o teste em `:110` cobre apenas `C` e `D`.

### REQ-BEN-015 — Consultar beneficiário por CPF ou por NIS

O sistema DEVE permitir consultar um beneficiário pelo CPF ou pelo NIS.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CONSBENF.NSP#L148-L163
- nível: `P` — preservado
- AC-015.1: Dado um CPF cadastrado, Quando consultado, Então os dados cadastrais são retornados.
- AC-015.2: Dado um NIS cadastrado, Quando consultado, Então os dados cadastrais são retornados.
- AC-015.3: Dado um documento não cadastrado, Quando consultado, Então o resultado indica ausência de registro.

### REQ-BEN-016 — Registrar acesso a dado pessoal na consulta

QUANDO um beneficiário for consultado, o sistema DEVE publicar um evento de acesso contendo o CPF consultado e o autor.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CONSBENF.NSP#L168-L179
- nível: `P` — preservado; o legado já registra, e a `REQ-AUD-007` já implementou o consumidor
- AC-016.1: Dada uma consulta bem-sucedida, Quando concluída, Então existe evento de acesso na trilha própria.
- AC-016.2: Dada uma consulta que não encontre registro, Quando concluída, Então o comportamento de registro é o mesmo do legado.

### REQ-BEN-017 — Mascarar o documento na exibição

O sistema DEVE exibir o CPF de forma mascarada nas telas de consulta.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CONSBENF.NSP#L200-L205
- nível: `P` — preservado; o legado declara `HIDE SENSITIVE DATA`
- AC-017.1: Dada a exibição de um beneficiário, Quando a tela for montada, Então o CPF aparece parcialmente oculto.

### REQ-BEN-018 — Preservar integralmente os dados informados

O sistema NÃO DEVE truncar nem descartar dado cadastral informado e aceito.

- source_legacy: 01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP#L277-L279
- nível: `C` — corrigido; o legado perde os últimos 20 caracteres do endereço
- AC-018.1: Dado um endereço com mais de sessenta caracteres, Quando gravado, Então nenhum caractere é perdido.
- AC-018.2: Dado um campo capturado na interface, Quando ele não tiver destino de gravação, Então ele não é oferecido na captura.

> Duas perdas conhecidas: `CADBENEF.NSP:277-279` grava um campo de 80 posições em um de 60, com o ticket `4471/2003` documentando a perda; e `CADDEPEN` captura documento e sexo do dependente em tela para campos que não existem no arquivo.

### REQ-BEN-019 — Registrar autor e instante de cada gravação

QUANDO um registro de beneficiário for criado ou alterado, o sistema DEVE registrar o autor e o instante da operação.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm#L114-L117
- nível: `C` — corrigido; os campos existem no dicionário e nenhum programa os preenche
- AC-019.1: Dado um beneficiário incluído, Quando consultado, Então constam o autor e o instante da criação.
- AC-019.2: Dado um beneficiário alterado, Quando consultado, Então constam o autor e o instante da última alteração.

### REQ-BEN-020 — Migrar registro inválido de forma sinalizada

QUANDO a carga inicial encontrar um registro que se torne inválido pelas regras desta especificação, o sistema DEVE migrá-lo sinalizado e incluí-lo em relatório de ocorrências.

- source_legacy: "[GREENFIELD] o legado não possui processo de carga; requisito absorve o REQ-DOC-010, adiado pela Fatia 1, e protege contra exclusão indevida de beneficiário"
- nível: `C` — decorrente das correções desta fatia e do [ADR-0005](../../docs/adr/0005-rotina-unica-validacao-cpf.md)
- AC-020.1: Dado um beneficiário com CPF hoje aceito em modo aviso, Quando a carga executar, Então o registro é migrado com marcação de pendência.
- AC-020.2: Dado o fim da carga, Quando o relatório for gerado, Então ele traz a contagem por motivo de pendência.
- AC-020.3: Dada qualquer pendência, Quando a carga executar, Então nenhum beneficiário é descartado.

> [!WARNING]
> Nenhum beneficiário pode ser descartado pela carga. Decidir o destino de um registro pendente é competência de negócio.

### REQ-BEN-021 — Inventariar valores fora do domínio declarado

QUANDO a carga inicial executar, o sistema DEVE inventariar os valores encontrados que estejam fora do domínio declarado no dicionário.

- source_legacy: 01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm#L91
- nível: `C` — corrigido; o domínio efetivo dos dados é desconhecido
- AC-021.1: Dado o campo de grau de parentesco, Quando a carga executar, Então o relatório traz cada valor encontrado com sua contagem.
- AC-021.2: Dado um valor fora do domínio, Quando encontrado, Então o registro é migrado sinalizado, e não recusado.

> O dicionário declara `FI`, `CJ`, `NT` e `TU` para grau de parentesco; o `CADDEPEN.NSP:152` aceita `FI`, `CO`, `IR` e `OU`. Só `FI` coincide. Não é possível decidir o domínio do sistema novo sem saber o que existe nos 4,2 milhões de registros.

---

## Fora de escopo

| Item | Razão | Destino |
|---|---|---|
| Dados bancários (`HA`–`HE`) | Nenhum dos cinco programas os lê ou escreve | Fatia 5, conciliação |
| Biometria (`FA`–`FD`) | `DIGITAL-HASH` marcado como não implementado no próprio dicionário | Fora do escopo de modernização |
| Óbito e bloqueio (`IA`–`IG`) | Alimentados por cruzamento SISOBI, cujo código não consta do acervo | Fatia futura |
| Representante legal (`JA`–`JB`) | Sem consumidor no acervo lido | Fatia futura |
| Exclusão de beneficiário | Não existe em nenhum programa; o acervo não tem `DELETE` | Não será implementada |
| Exclusão de dependente | Não existe no `CADDEPEN` | Fatia futura |
| Validação de RG | Apenas comprimento mínimo, sem dígito nem UF emissora | Adiada; ver `REQ-DOC` fora de escopo |

---

## Rastreabilidade

| REQ-ID | Regra do catálogo | Mistério associado | Nível |
|---|---|---|---|
| `REQ-BEN-001` | 8, 9 | — | `P` |
| `REQ-BEN-002` | 2, 4, 5, 6 | — | `P` |
| `REQ-BEN-003` | 3, 7, 13, 51 | `SIFAP-M-16` | `C` |
| `REQ-BEN-004` | 109 | `SIFAP-M-03` | `C` |
| `REQ-BEN-005` | 15, 16 | `SIFAP-M-03` | `C` |
| `REQ-BEN-006` | 12 | `SIFAP-M-01` | `PS` |
| `REQ-BEN-007` | 10 | — | `PS` |
| `REQ-BEN-008` | 24 | `SIFAP-M-02` | `PS` |
| `REQ-BEN-009` | 30 | — | `C` |
| `REQ-BEN-010` | 31 | — | `C` |
| `REQ-BEN-011` | 29 | — | `P` |
| `REQ-BEN-012` | 28 | — | `C` |
| `REQ-BEN-013` | 27 | — | `C` |
| `REQ-BEN-014` | 23 | — | `P` |
| `REQ-BEN-015` | 136 | — | `P` |
| `REQ-BEN-016` | 139 | `SIFAP-M-17` | `P` |
| `REQ-BEN-017` | 137 | — | `P` |
| `REQ-BEN-018` | 17, 32 | — | `C` |
| `REQ-BEN-019` | 62 | — | `C` |
| `REQ-BEN-020` | — | `SIFAP-M-15`, `SIFAP-M-16` | `C` |
| `REQ-BEN-021` | 26 | — | `C` |

**Oito preservações, dez correções, três com sinalização.** As três decisões que afetam quem recebe — suspensão por idade, cálculo de idade e limite de dependentes — estão todas em `PS`, nenhuma em `C`, conforme o critério do [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md).

**Mistérios que permanecem abertos.** `SIFAP-M-01`, `SIFAP-M-02` e `SIFAP-M-03` continuam sem validação humana em [`mysteries-found.md`](../../01-archaeology/mysteries-found.md).

**Um achado novo desta leitura.** O domínio de grau de parentesco diverge entre dicionário e código, com apenas um valor em comum. A regra 26 do catálogo pedia confirmação no DDM; a confirmação mostrou divergência, não coincidência. O `REQ-BEN-021` transforma isso em medição em vez de suposição.

---

## Definição de pronto

- [x] Todo requisito usa um padrão EARS com `DEVE`.
- [x] Todo REQ-ID é único e declarado como título.
- [x] Todo REQ-ID tem `source_legacy:` apontando para arquivo existente ou `[GREENFIELD]` justificado.
- [x] Todo requisito tem critérios de aceitação em Dado/Quando/Então.
- [x] Cada requisito declara o nível de tratamento do [ADR-0003](../../docs/adr/0003-preservacao-de-comportamento.md).
- [x] `plan.md` e `tasks.md` gerados.
