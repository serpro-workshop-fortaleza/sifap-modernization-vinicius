# Registro de Questões em Aberto — Estágio 1

> **Trilha:** [Kit do Time](../README.md) › [Estágio 1](README.md) › **Questões em Aberto**

**Registro rastreável das incertezas do Estágio 1.** Cada entrada documenta uma pergunta sem resposta, com evidência, hipótese marcada como não confirmada e responsável pela validação.

| Campo | Valor |
|---|---|
| **Público-alvo** | Todas as duplas |
| **Pré-requisitos** | Ler os programas atribuídos |
| **Estágio** | Estágio 1 — Arqueologia |
| **Resultado esperado** | Perguntas sem conclusão, com evidência e responsável identificado |

> [!IMPORTANT]
> Uma pergunta só vira regra de negócio, requisito ou conclusão depois de validação humana explícita e com a evidência preservada como `path:line`. Este registro não é uma resposta e não substitui essa validação.

---

## Registro

Use uma linha por mistério. Preencha com o **ID canônico** da sua dupla (`SIFAP-M-01` … `SIFAP-M-20` — veja o [checklist](mysteries-checklist.md)) ou `BONUS` para achados fora da lista.

> [!IMPORTANT]
> **Os IDs canônicos abaixo são uma proposta e precisam de confirmação.** O critério de atribuição foi delegado ao agente pela pessoa responsável pela leitura. Aplicou-se a escada do [checklist](mysteries-checklist.md) — dois óbvios, um médio e um difícil por área — selecionando 20 achados entre os 47 registrados. Os 27 restantes estão marcados como `BONUS` na própria coluna `ID`, sem sair da tabela da sua área.
> As linhas foram transcritas de [`business-rules-catalog.md`](business-rules-catalog.md), que registra a evidência de cada uma. O agente não formula hipótese: onde a leitura não produziu hipótese, o campo permanece aguardando.
> O `Status` segue critério fornecido pela pessoa responsável: **`aguardando validação humana`** para o que depende de decisão de negócio ou de norma, e **`aberta`** para o que pode ser resolvido por teste ou consulta ao ambiente. A distinção indica a via de resolução, não a prioridade.
> A `Pessoa/área responsável` foi preenchida por **área**, não por pessoa, conforme as atribuições descritas em [`legacy-sifap/README.md`](legacy-sifap/README.md). Substitua pelo nome de quem for designado.
> O HARD GATE permanece: nenhuma linha pode virar regra, requisito ou conclusão antes de validação humana explícita apoiada na evidência registrada.

#### Área: Cadastro — `SIFAP-M-01` … `SIFAP-M-04`

| ID | Questão em aberto | Evidência (`path:line`) | Impacto | Hipótese (não confirmada) | Pessoa/área responsável | Status |
|---|---|---|---|---|---|---|
| `SIFAP-M-01` | Por que beneficiários com mais de 75 anos recebem automaticamente a situação `S`, que o DDM define como suspensão, sem qualquer decisão administrativa? | `01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP:250-252`, `01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm:74` | Suspensão retira o beneficiário do cálculo da folha em `CALCBENF.NSN:180-185`. Na migração, replicar a regra sem confirmá-la mantém a exclusão silenciosa; omiti-la muda o resultado financeiro. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `SIFAP-M-03` | Por que a alteração de um beneficiário com 75 anos ou menos grava a situação cadastral em branco? | `01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP:245-252`, `01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP:315` | Grava valor fora do domínio fixo `A/S/C`. Em PostgreSQL com restrição de domínio, a carga desses registros falha. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `SIFAP-M-02` | Qual é o limite válido de dependentes por beneficiário: 3, 5, 6 ou 10? | `01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP:117-120`, `01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP:192`, `01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm:85-87`, `01-archaeology/legacy-sifap/legacy-docs/BUSINESS-RULES-2012.md` RN-004 | Define a cardinalidade do agregado de dependentes e a validação da API. Quatro fontes divergem. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `BONUS` | Por que dependentes são gravados sem situação e sem indicador de deficiência, se a contagem do titular é descrita como "de dependentes ativos"? | `01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP:194-198`, `01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm:81`, `01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm:93` | A contagem usada no cálculo do benefício não corresponde à situação real dos dependentes. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `SIFAP-M-04` | De onde vem a constante `0.347215` aplicada ao fator de correção do programa social, e qual norma a define? | `01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP:124` | Altera o valor base gravado de todo programa social. O levantamento de 2012 registra o `FACTOR-K` como não explicado. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `BONUS` | Quem popula o campo `FACTOR-K` do arquivo de programas sociais, se o cadastro calcula um valor local e nunca o grava? | `01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm:47`, `01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP:124-139` | Se ninguém popula, cálculos que dependam do campo usam nulo. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `BONUS` | Quem mantém as faixas de cálculo do grupo periódico do programa social, se nenhum programa lido as grava nem as lê? | `01-archaeology/legacy-sifap/adabas-ddms/SOCPROG.ddm:67-74`, `01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP:13-25` | Estrutura paramétrica existe e está ociosa; as faixas efetivas estão codificadas no fonte do cálculo. | <!-- aguardando: não confirmada --> | SENARC | aberta |
| `BONUS` | Onde estão implementadas as validações de programa social ativo, limite de dependentes, código de região e idade mínima, ausentes do cadastro? | Ausência verificada em `01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP:117-331`; `01-archaeology/legacy-sifap/legacy-docs/BUSINESS-RULES-2012.md` RN-003, RN-004, RN-005, RN-006 | Quatro regras documentadas sem código correspondente. Podem nunca ter existido. | <!-- aguardando: não confirmada --> | SENARC | aberta |

#### Área: Batch — `SIFAP-M-05` … `SIFAP-M-08`

| ID | Questão em aberto | Evidência (`path:line`) | Impacto | Hipótese (não confirmada) | Pessoa/área responsável | Status |
|---|---|---|---|---|---|---|
| `SIFAP-M-05` | O batch da folha grava dois registros de pagamento por beneficiário por ciclo, um pelo subprograma de cálculo e outro pelo cálculo repetido no próprio corpo? | `01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP:381`, `01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP:390-488`, `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:319` | Se confirmado, o arquivo de pagamentos tem o dobro dos registros e os relatórios divergem do que foi transmitido ao banco. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `SIFAP-M-08` | Por que o número do pagamento nunca é atribuído antes da gravação no subprograma de cálculo, se o campo é descritor único? | `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:308-319`, `01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm:34` | Registros sem número não casam na conciliação bancária e permanecem com situação `G` indefinidamente. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `BONUS` | Por que o código de retorno do subprograma de cálculo não é verificado após a chamada, enquanto o da elegibilidade é? | `01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP:369-388` | Erros de cálculo não interrompem a geração do pagamento. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aguardando validação humana |
| `SIFAP-M-06` | Quantos registros de produção ainda chegam com data de nascimento em `YYMMDD`, se a janela de século continua ativa no batch? | `01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP:327-345` | A remediação Y2K de 1998 não foi concluída. Afeta idade, faixa etária e carga inicial da migração. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `BONUS` | Por que o layout gravado no extrato de remessa não corresponde às posições que a conciliação espera no arquivo de retorno? | `01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP:490-502`, `01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP:149-153` | Sugere transformação intermediária ausente do acervo. | <!-- aguardando: não confirmada --> | CGPB | aberta |
| `SIFAP-M-07` | Por que o relatório consolidado arredonda o valor bruto enquanto o cálculo trunca? | `01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP:165-170`, `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:264-266` | O total do relatório não bate com a soma da base. Afeta prestação de contas. | <!-- aguardando: não confirmada --> | CGPB | aguardando validação humana |
| `BONUS` | Quem edita o período fixado no job da folha a cada mês, e o que ocorre quando isso é esquecido? | `01-archaeology/legacy-sifap/natural-programs/SIFAPJ01.jcl:78-86` | Período literal em JCL de produção. Risco operacional recorrente. | <!-- aguardando: não confirmada --> | CGPB | aguardando validação humana |
| `BONUS` | Quem executa a conciliação bancária e com que frequência, se ela não está automatizada em nenhum job? | `01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP:14-15` | Ciclos não conciliados deixam pagamentos sem confirmação de crédito. | <!-- aguardando: não confirmada --> | CGPB | aguardando validação humana |

#### Área: Cálculo — `SIFAP-M-09` … `SIFAP-M-12`

| ID | Questão em aberto | Evidência (`path:line`) | Impacto | Hipótese (não confirmada) | Pessoa/área responsável | Status |
|---|---|---|---|---|---|---|
| `SIFAP-M-09` | Qual fórmula de cálculo do benefício está vigente: a multiplicativa de cinco fatores implementada ou a aditiva documentada? | `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:255-258`, `01-archaeology/legacy-sifap/legacy-docs/BUSINESS-RULES-2012.md` RN-013 | Nenhum requisito de valor pode ser escrito antes da resposta. É a decisão central do Estágio 2. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `SIFAP-M-11` | A faixa de renda deve ser determinada pela renda familiar total ou pela renda per capita? | `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:174`, `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:127-137`, `01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm:80`, `01-archaeology/legacy-sifap/legacy-docs/BUSINESS-RULES-2012.md` RN-018 | O campo de renda per capita existe e não é usado. Impacto financeiro sistemático sobre famílias numerosas. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `BONUS` | O fator de ajuste do programa deve ser aplicado duas vezes, uma no cadastro e outra no cálculo? | `01-archaeology/legacy-sifap/natural-programs/CADPROG.NSP:124-125`, `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:262` | Se for dupla aplicação, todos os valores base estão inflados. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `SIFAP-M-10` | Qual das duas contribuições sociais é a vigente: a alíquota única de 3% do cálculo ou a tabela progressiva do módulo de descontos? | `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:356-366`, `01-archaeology/legacy-sifap/natural-programs/CALCDSCT.NSP:197-205` | Os critérios são inversos entre si. Define o desconto de toda a folha. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `BONUS` | O módulo de descontos é executado em produção, se nenhum programa o invoca? | Ausência verificada em `01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP:276`, `:369`, `:381`; `01-archaeology/legacy-sifap/natural-programs/CALCDSCT.NSP:71-74` | Determina se a tabela progressiva e o teto de 30% valem na prática. | <!-- aguardando: não confirmada --> | CGPB | aberta |
| `BONUS` | Por que o total de descontos é recalculado sem que o valor líquido do pagamento seja atualizado? | `01-archaeology/legacy-sifap/natural-programs/CALCDSCT.NSP:183-188` | O registro fica internamente inconsistente: líquido diferente de bruto menos desconto. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `SIFAP-M-12` | A correção retroativa deve aplicar o índice de um único mês ou o acumulado do período? | `01-archaeology/legacy-sifap/natural-programs/CALCCORR.NSP:229-239` | Impacto financeiro direto sobre beneficiários com pagamentos atrasados. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `BONUS` | Por que a tabela de índices cobre apenas 2010 a 2012, se o comentário declara carga até 2014? | `01-archaeology/legacy-sifap/natural-programs/CALCCORR.NSP:76`, `01-archaeology/legacy-sifap/natural-programs/CALCCORR.NSP:86-129` | Anos ausentes não são corrigidos e o programa termina sem aviso. | <!-- aguardando: não confirmada --> | CGPB | aguardando validação humana |
| `BONUS` | Qual fórmula do décimo terceiro é a correta: a do comentário, proporcional aos meses ativos, ou a do código, com fator etário? | `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:272-281` | Não existe proporcionalidade para quem entrou no meio do ano. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |

#### Área: Validação — `SIFAP-M-13` … `SIFAP-M-16`

| ID | Questão em aberto | Evidência (`path:line`) | Impacto | Hipótese (não confirmada) | Pessoa/área responsável | Status |
|---|---|---|---|---|---|---|
| `SIFAP-M-13` | Que norma autoriza a região 99 a conceder elegibilidade sem nenhuma outra verificação, e quantos registros de produção a utilizam? | `01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN:120-128`, `01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm:66`, `01-archaeology/legacy-sifap/legacy-docs/BUSINESS-RULES-2012.md` RN-005 | Situação, idade, renda e documentação são ignoradas. Nenhum programa valida quem pode atribuir o valor. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `SIFAP-M-14` | Que norma criou os oito prefixos de CPF que zeram todos os erros da validação documental, e não apenas o erro de CPF? | `01-archaeology/legacy-sifap/natural-programs/VALDOCS.NSP:55-65`, `01-archaeology/legacy-sifap/natural-programs/VALDOCS.NSP:226-241` | Um RG inválido já detectado é apagado junto. | <!-- aguardando: não confirmada --> | DEFIS | aguardando validação humana |
| `SIFAP-M-15` | Por que um CPF com onze dígitos iguais iniciado em `000` é considerado válido como documento de teste governamental? | `01-archaeology/legacy-sifap/natural-programs/VALBENEF.NSN:229-245`, `01-archaeology/legacy-sifap/natural-programs/SUBVALCP.NSN:56-60` | O mesmo valor significa "inválido", "ausente" e "válido" conforme o módulo. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aguardando validação humana |
| `SIFAP-M-16` | Qual é a rotina de validação de CPF correta para a modernização, se existem cinco implementações com quatro comportamentos distintos? | `01-archaeology/legacy-sifap/natural-programs/CCVALCPF.NSC:32-37`, `01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP:344-413`, `01-archaeology/legacy-sifap/natural-programs/VALBENEF.NSN:196-281`, `01-archaeology/legacy-sifap/natural-programs/VALDOCS.NSP:137-204` | O mesmo CPF é aceito por um módulo e recusado por outro. Ticket 6620/2011 aberto. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aguardando validação humana |
| `BONUS` | Qual a qualidade dos dados cadastrais anteriores a 2011, se o validador rodava sem receber variáveis preenchidas? | `01-archaeology/legacy-sifap/natural-programs/VALBENEF.NSN:11-13` | Treze anos de cadastro sem validação efetiva. Define se a migração pode confiar na base. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `BONUS` | Quem grava o indicador de documentação regular, se nenhum programa do acervo o escreve e a elegibilidade o exige? | `01-archaeology/legacy-sifap/natural-programs/VALDOCS.NSP:53-125`, `01-archaeology/legacy-sifap/natural-programs/VALELEG.NSN:196` | Se ninguém grava, todo beneficiário de programa assistencial é inelegível por documentação. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `BONUS` | Por que o indicador de documento especial previsto no contrato corporativo nunca é preenchido pelos subprogramas? | `01-archaeology/legacy-sifap/natural-programs/PDAVALID.NSA:54`, `01-archaeology/legacy-sifap/natural-programs/SUBVALCP.NSN:45`, `01-archaeology/legacy-sifap/natural-programs/SUBVALNI.NSN:53` | A centralização prevista em 2003 nunca saiu; cada programa criou o próprio desvio. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aguardando validação humana |
| `BONUS` | Quantos registros têm data de nascimento em 29 de fevereiro de ano não bissexto, se o calendário fixa fevereiro em 29 dias desde 1997? | `01-archaeology/legacy-sifap/natural-programs/VALBENEF.NSN:102-114`, `01-archaeology/legacy-sifap/natural-programs/LDASIFAP.NSL:76-81` | Datas inválidas aceitas em toda a base. Falham na carga para PostgreSQL. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |

#### Área: Consultas e relatórios — `SIFAP-M-17` … `SIFAP-M-20`

| ID | Questão em aberto | Evidência (`path:line`) | Impacto | Hipótese (não confirmada) | Pessoa/área responsável | Status |
|---|---|---|---|---|---|---|
| `SIFAP-M-17` | Qual norma prevalece sobre a auditoria de consultas: a portaria de 2010 que a proíbe por volume ou a instrução normativa que exige rastreio de acesso a dado pessoal? | `01-archaeology/legacy-sifap/natural-programs/CCAUDIT.NSC:45-49`, `01-archaeology/legacy-sifap/natural-programs/CONSBENF.NSP:167-179` | Duas normas internas em conflito direto. Define se o sistema moderno audita consultas. | <!-- aguardando: não confirmada --> | CGTI/MDAS | aguardando validação humana |
| `SIFAP-M-18` | Quando e por que o relatório de auditoria passou a omitir os eventos de exclusão? | `01-archaeology/legacy-sifap/natural-programs/RELAUDIT.NSP:128-134`, `01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm:164-167` | A categoria mais relevante para auditoria não aparece. O DBA documenta o contorno pelo SYSAOS. | <!-- aguardando: não confirmada --> | DEFIS | aguardando validação humana |
| `SIFAP-M-19` | O código de ação `CO` significa consulta ou conciliação, se o relatório o traduz como conciliação e a consulta o grava como consulta? | `01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm:41`, `01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm:152-153`, `01-archaeology/legacy-sifap/natural-programs/RELAUDIT.NSP:170-172`, `01-archaeology/legacy-sifap/natural-programs/CONSBENF.NSP:172` | Três significados no mesmo campo, separados por período. Migrar para valor único perde informação. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aguardando validação humana |
| `SIFAP-M-20` | Que código grava os eventos de login, logout, autorização e rejeição, se nenhum programa do acervo o faz? | `01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm:38-49`, `01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm:154` | 25 milhões de eventos gravados por código fora do acervo. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `BONUS` | Qual processo depende do defeito conhecido na máscara de CPF, a ponto de a correção exigir aprovação de auditoria? | `01-archaeology/legacy-sifap/natural-programs/CONSBENF.NSP:291-310` | Proteção de dado pessoal com falha conhecida e congelada. | <!-- aguardando: não confirmada --> | DEFIS | aguardando validação humana |
| `BONUS` | Por que o relatório impresso expõe oito dos onze dígitos do CPF ao lado do nome e do valor? | `01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP:164-167`, `01-archaeology/legacy-sifap/natural-programs/SIFAPJ02.jcl:83-91` | Relatório físico distribuído ao centro de impressão e à SENARC. Reidentificação trivial. | <!-- aguardando: não confirmada --> | DEFIS | aguardando validação humana |

#### Área: Dados e infraestrutura

| ID | Questão em aberto | Evidência (`path:line`) | Impacto | Hipótese (não confirmada) | Pessoa/área responsável | Status |
|---|---|---|---|---|---|---|
| `BONUS` | O domínio de situação do pagamento é o declarado no dicionário, em que `P` significa pendente, ou o usado no código, em que `P` significa pago? | `01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm:60`, `01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP:207`, `01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP:182-193` | Qualquer consulta direta à base interpreta os pagamentos ao contrário. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aguardando validação humana |
| `BONUS` | Qual é o domínio verdadeiro dos tipos de desconto, se o dicionário, o código e a documentação usam três conjuntos incompatíveis? | `01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm:51`, `01-archaeology/legacy-sifap/natural-programs/CALCDSCT.NSP:127-167`, `01-archaeology/legacy-sifap/legacy-docs/BUSINESS-RULES-2012.md` RN-022 | Um desconto gravado como contribuição é silenciosamente descartado no cálculo. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `BONUS` | Qual número de registros de auditoria usar no dimensionamento: os 25 milhões da documentação ou os 418 milhões do dicionário? | `01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm:145-158`, `01-archaeology/legacy-sifap/README.md` | Diferença de dezesseis vezes no volume e 311 GB de dados. | <!-- aguardando: não confirmada --> | DBA Adabas — SUPDE/DESIF | aberta |
| `BONUS` | Os três arquivos de auditoria histórica sem dicionário publicado entram no escopo da migração? | `01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm:169-172` | FNR 154, 155 e 156 não constam de nenhum inventário. Quadruplica o escopo da auditoria. | <!-- aguardando: não confirmada --> | CGTI/MDAS | aguardando validação humana |
| `BONUS` | A ampliação de ISN do arquivo de beneficiários, exigida até 2022, chegou a ser executada? | `01-archaeology/legacy-sifap/adabas-ddms/FDT-150-BENEFICIARY.txt:139-141` | Se não foi, novos cadastros falham. Se foi, houve manutenção pós-2018 não documentada. | <!-- aguardando: não confirmada --> | DBA Adabas — SUPDE/DESIF | aberta |
| `BONUS` | O que é o campo `AA` do arquivo físico de beneficiários, descritor único que o dicionário lógico não expõe? | `01-archaeology/legacy-sifap/adabas-ddms/FDT-150-BENEFICIARY.txt:15`, `01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm:40` | Chave única invisível ao Natural. A migração precisa decidir se a preserva. | <!-- aguardando: não confirmada --> | DBA Adabas — SUPDE/DESIF | aguardando validação humana |
| `BONUS` | Que regra de negócio está codificada no hiperdescritor que combina data de nascimento e renda per capita, cujo código está fora do acervo? | `01-archaeology/legacy-sifap/adabas-ddms/FDT-150-BENEFICIARY.txt:88` | Rotina em Assembler ligada ao núcleo do Adabas. Regra de indexação não reproduzível. | <!-- aguardando: não confirmada --> | DBA Adabas — SUPDE/DESIF | aberta |
| `BONUS` | Qual versão de Natural e Adabas está em produção, se o JCL e o dicionário indicam 4.2.6 e a documentação do sistema declara 6.3.12? | `01-archaeology/legacy-sifap/natural-programs/SIFAPJ01.jcl:39-42`, `01-archaeology/legacy-sifap/adabas-ddms/AUDIT.ddm:21`, `01-archaeology/legacy-sifap/README.md` | Define quais recursos de linguagem existem e afeta as quatro pendências de semântica do catálogo. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |

### Achados adicionais (bônus)

Achados legítimos fora dos 20 mistérios canônicos. Contam no debrief, **não** mudam o denominador e **não** substituem um mistério canônico que ficou faltando.

Os **27 achados bônus** estão identificados com `BONUS` na coluna `ID` das tabelas por área acima. Foram mantidos junto da sua área de origem para preservar a leitura por módulo; a coluna `ID` é a fonte da distinção.

| Área | Canônicos | Bônus |
|---|---:|---:|
| Cadastro | 4 | 4 |
| Batch | 4 | 4 |
| Cálculo | 4 | 5 |
| Validação | 4 | 4 |
| Consultas e relatórios | 4 | 2 |
| Dados e infraestrutura | 0 | 8 |
| **Total** | **20** | **27** |

### Achados posteriores ao Estágio 1

Questões encontradas **depois** da arqueologia, durante a leitura detalhada que a especificação e a implementação de cada fatia exigem. Ficam em seção própria para não alterar o denominador do Estágio 1: os 20 canônicos e os 27 bônus acima continuam sendo o que se sabia ao fim daquele estágio.

O ID segue o formato `SIFAP-F<fatia>-<sequência>`. Valem as mesmas regras das tabelas anteriores: evidência em `path:line`, hipótese apenas quando a leitura a produziu, e nenhuma linha vira conclusão antes de validação humana.

| ID | Questão em aberto | Evidência (`path:line`) | Impacto | Hipótese (não confirmada) | Pessoa/área responsável | Status |
|---|---|---|---|---|---|---|
| `SIFAP-F4-01` | Quantos pagamentos de produção foram calculados com o fator de renda do beneficiário processado imediatamente antes, por a renda superar a última faixa? | `01-archaeology/legacy-sifap/natural-programs/BATCHPGT.NSP:380-412` | A última faixa termina em `9.999,99`. Acima disso o laço de `DET-INCOME-BAND-BATCH` não encontra faixa e `#FACTOR-INCOME` não é reinicializado a cada iteração: o valor do beneficiário anterior permanece. Dois beneficiários com dados idênticos recebem valores diferentes conforme a ordem de leitura do arquivo. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `SIFAP-F5-01` | Quantos registros de retorno bancário foram contados como conciliados sem que o pagamento fosse atualizado, por trazerem código de retorno fora do domínio? | `01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP:203`, `01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP:237-242` | `ADD 1 TO #QTY-RECONCILED` ocorre antes do `DECIDE` que classifica o código. O ramo `NONE` apenas escreve no log. O resumo de execução declara conciliação sobre registros que nenhum ramo tratou. | <!-- aguardando: não confirmada --> | CGPB | aberta |
| `SIFAP-F5-02` | Como listar os pagamentos com divergência de valor de um período, se a divergência não deixa marca no próprio pagamento? | `01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP:192-201`, `01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP:332-336` | A divergência gera registro de auditoria e mantém o pagamento na situação anterior. A única via de consulta é varrer 418 milhões de eventos da trilha. | <!-- aguardando: não confirmada --> | CGPB | aberta |
| `SIFAP-F5-03` | Qual banco pagador está registrado nos pagamentos conciliados, se o campo alfanumérico recebe um literal numérico? | `01-archaeology/legacy-sifap/natural-programs/BATCHCON.NSP:212`, `01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm:72` | `MOVE 1 TO PAYMENT-V.COD-BANK` sobre campo `A3` declarado como código FEBRABAN. O código do banco que efetivamente creditou não é preservado. | <!-- aguardando: não confirmada --> | SUPDE/DESIF | aberta |
| `SIFAP-F5-04` | Desde quando o consolidado mensal por região está agrupando incorretamente, e quem consome esse relatório? | `01-archaeology/legacy-sifap/natural-programs/BATCHREL.NSP:150-170`, `01-archaeology/legacy-sifap/adabas-ddms/BENEFIC.ddm:66` | O domínio de `COD-REGION` é `01`-`05` e `99`; a classificação usa faixas `1-5`, `6-10`, `11-15`, `16-20` e resto. Todos os códigos válidos somam como Norte e o `99` como Centro-Oeste. Relatório distribuído à SENARC desde 1999. | <!-- aguardando: não confirmada --> | SENARC | aguardando validação humana |
| `SIFAP-F5-05` | Os subtotais por programa do relatório detalhado já foram conferidos contra a base? | `01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP:141-148`, `01-archaeology/legacy-sifap/natural-programs/RELPGT.NSP:120-121` | A quebra manual compara com o valor anterior e pressupõe ordenação por programa; a leitura é ordenada por período. Programas intercalados recebem vários subtotais parciais. | <!-- aguardando: não confirmada --> | CGPB | aberta |

**Sobre os outros dois achados da Fatia 4.** A dupla gravação do `SIFAP-M-05` e o número ausente do `SIFAP-M-08` são o mesmo defeito visto de dois ângulos — o pagamento que `CALCBENF.NSN:319` grava é justamente o que nunca recebe `NUM-PAYMENT`. A aplicação dupla do fator de ajuste já constava como bônus na área Cálculo. Nenhum dos dois abre questão nova; a evidência está em [`specs/005-processamento-de-folha/spec.md`](../specs/005-processamento-de-folha/spec.md).

---

## Pendências de semântica da linguagem

Quatro pontos não são mistérios de negócio: dependem do comportamento do Natural 4.2.6 e do Adabas no ambiente de produção. Estão registrados aqui porque bloqueiam requisitos, e são resolvidos por teste, não por entrevista.

| Ponto | Evidência (`path:line`) | Regras do catálogo afetadas | Pessoa/área responsável | Status |
|---|---|---|---|---|
| O corpo de um bloco `FIND` executa quando a busca não retorna registros? | `01-archaeology/legacy-sifap/natural-programs/CADBENEF.NSP:206-211` | 8, 9, 22, 38 | SUPDE/DESIF | aberta |
| Qual laço o `ESCAPE BOTTOM` encerra dentro de um `FOR` aninhado em `FIND`? | `01-archaeology/legacy-sifap/natural-programs/CADDEPEN.NSP:175-184` | 28 | SUPDE/DESIF | aberta |
| A atribuição a campo inteiro trunca ou arredonda? | `01-archaeology/legacy-sifap/natural-programs/CALCBENF.NSN:264-266` | 74, 148 | SUPDE/DESIF | aberta |
| O Adabas trata zero como valor em descritor único sem supressão de nulos? | `01-archaeology/legacy-sifap/adabas-ddms/PAYMENT.ddm:34`, `01-archaeology/legacy-sifap/adabas-ddms/FDT-150-BENEFICIARY.txt:142-145` | 79, 158 | DBA Adabas — SUPDE/DESIF | aberta |

---

## Regras de integridade

- Registre apenas questões em aberto; não escreva uma resposta no catálogo.
- Mantenha a evidência no formato `path:line` para preservar a rastreabilidade.
- Marque toda hipótese explicitamente como **não confirmada**.
- Só a pessoa responsável pode dar a validação humana e mudar o status.
- Sem evidência humana, a questão continua em aberto.

---

### Continue lendo

| Anterior | Próximo |
|---|---|
| [Checklist de Questões em Aberto](mysteries-checklist.md)<br/><sub>Verificação de rastreabilidade.</sub> | [Relatório de Descoberta](discovery-report.md)<br/><sub>Consolidação final do estágio.</sub> |

<sub>[Voltar ao índice do kit](../README.md)</sub>
