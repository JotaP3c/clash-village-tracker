# PROMPT_PARA_KIRO.md — Implementation request / Pedido de implementação

> **Instructions:** The Portuguese and English sections below are equivalent versions of the **same request**. Send only **one** language version to the Kiro chat; do not run both independently. Keep the resulting source code identifiers and CLI flags in English.

# Português (Brasil) — PT-BR

## Papel e objetivo

Atue como **engenheiro de software Java sênior**, com responsabilidade pelo desenvolvimento incremental do **Clash Village Tracker**. O projeto aberto no Kiro contém documentação funcional de Clash of Clans CV18, snapshots, catálogos de melhorias, uma planilha de referência e uma especificação em `.kiro/specs/clash-village-sync/`.

Minha meta é um programa **Java executável localmente, sob demanda**, que leia dados autorizados da minha vila (`#U8J89QP2`), calcule níveis, Sobrecargas, recursos e tempos restantes com rastreabilidade, e gere um **Excel claro, editável e com Dashboard dinâmico**. Em etapas posteriores, conectaremos à API oficial da Supercell, importaremos exportação nativa da vila e, opcionalmente, criaremos relatórios JasperReports.

**Não quero apenas um plano:** inspecione, implemente, compile, teste e entregue um MVP offline real. Respeite todas as permissões do ambiente e não execute operações destrutivas sem aprovação.

## 1. Leia e compreenda antes de alterar código

Primeiro, percorra a árvore real do repositório. Abra e leia:

- `AGENTS.md` e `README.md` — regras e visão do projeto.
- `.kiro/steering/clash-cv18.md` — contexto persistente existente.
- `docs/00_indice.md`, `docs/01_regras_jogo.md`, `docs/15_regras_automacao.md`, `docs/16_modelo_dados.md`, `docs/17_api_e_exportacao.md`, `docs/18_fontes.md` e `docs/19_testes_e_pendencias.md`.
- Todos os documentos por categoria em `docs/02_defesas.md` até `docs/14_construcoes_auxiliares.md`, de forma que nenhuma categoria seja omitida.
- `.kiro/specs/clash-village-sync/requirements.md`, `design.md` e `tasks.md`.
- `data/snapshot_2026-10-19.json`, `data/upgrade_steps.json` e `data/upgrade_steps.csv`.
- A planilha `controle_cv18_jaoo_2026-10-19.xlsx`, apenas como referência visual de dados e fórmulas.

**Inspecione o conteúdo real dos JSON/CSV, sem assumir propriedades com base apenas na documentação.** Verifique se já existem código, build ou testes. Não crie cópias duplicadas de classes ou de configurações.

Após a leitura, produza um resumo curto dos módulos existentes, divergências e pontos que impedem implementação. Corrija especificações inconsistentes somente quando houver base, documentando decisões. Em seguida, **continue para o código** sem aguardar nova solicitação, desde que a ação esteja autorizada.

## 2. Contrato de domínio do Clash of Clans

Considere a conta TH18 e snapshot de **19/10/2026** como fotografia histórica, não consulta em tempo real. Existem **6 construtores permanentes**; o Goblin Builder é temporário. O Passe Dourado de 20% refere-se ao snapshot e deve ser parametrizável.

Implemente suporte a: defesas, construções auxiliares, armazenamentos, coletores, armadilhas, muros, Sobrecargas, heróis, pets, guardiões, tropas, feitiços, máquinas de cerco e atributos de defesas artesanais.

Regras inegociáveis:

1. `currentLevel` representa **nível concluído**. `targetLevel` representa **melhoria em andamento**. Se há 3→4 ativo, nível atual ainda é 3.
2. Construções repetidas podem possuir **níveis e Sobrecargas diferentes**. Modele instâncias individuais quando possível; estados agregados devem ser rotulados.
3. Sobrecargas são independentes dos níveis permanentes e podem ser invalidadas por atualização do jogo. O catálogo deve definir elegibilidade e etapas de SC por tipo/versão.
4. Cronômetro observado já reflete a situação do jogo: **nunca aplique novamente o desconto do Passe** sobre `observedRemainingSeconds`.
5. Cada nível pendente tem sua própria duração. Não use uma duração fixa para todas as evoluções.
6. `null`, `UNKNOWN`, `NOT_APPLICABLE`, `CONFLICT` e zero são estados distintos; **nunca converta desconhecido para 0**.
7. Laboratório, Casa dos Pets e capacidade de construtores possuem regras próprias; não divida todos os tempos por seis.
8. Não afirme que a API pública fornece construções/SC/filas sem verificar o endpoint real. Parser de exportação somente após amostra genuína.
9. Todos os valores conciliados devem carregar origem, data da observação e confiabilidade.
10. Não declare **FULL absoluto** com itens desconhecidos ou incompletos.

## 3. Implementar o MVP offline agora

Monte um projeto **Maven + Java 17 ou 21**. Use Jackson, Apache POI, JUnit 5, Java HttpClient quando necessário e logging consistente. Evite adicionar Spring Boot, banco de dados e JasperReports sem necessidade no MVP.

Organize em responsabilidades claras (`domain`, `catalog`, `importer`, `reconciliation`, `calculation`, `report.excel`, `history`, `cli`). Crie classes efetivamente usadas, interfaces testáveis e regras desacopladas do Excel.

Pipeline mínimo:

`load → validate → normalize → reconcile → calculate → generate → persist`

A primeira versão deve:

1. Ler snapshot histórico JSON e catálogo JSON/CSV a partir de caminhos configuráveis.
2. Validar formato, tipos, unicidade de IDs confiáveis e consistência dos níveis.
3. Normalizar nomes/categorias e preservar o dado bruto quando não compreendido.
4. Separar nível concluído, nível-alvo, status e tempo restante observado.
5. Enumerar **cada etapa** entre nível atual e máximo, e as Sobrecargas válidas.
6. Retornar subtotal de durações conhecidas e lista das etapas sem tempo, sem fabricar total exato.
7. Calcular durações com e sem bônus editável do Passe, preservando saldos observados.
8. Calcular progresso acumulado por etapas desde os níveis iniciais, progresso permanente, SC e cobertura de dados separadamente.
9. Produzir relatório XLSX com abas organizadas, fórmulas vinculadas a células editáveis e gráfico de pizza.
10. Salvar snapshot normalizado, resumo de pendências e, quando existir snapshot anterior, diff de evolução.
11. Garantir comportamento idempotente: executar com o mesmo snapshot não duplica entidades ou upgrades.
12. Não sobrescrever histórico, planilha original ou alterações manuais sem política explícita de merge.

## 4. Contrato do Excel

O relatório deve ser **mais fácil de usar que a planilha inicial**. Prefira abas:

- Dashboard.
- Visão Geral.
- Defesas e Sobrecargas.
- Coletores/Recursos.
- Armadilhas e Muros.
- Heróis, Pets e Guardiões.
- Laboratório (tropas, feitiços, cercos, com filtro por tipo).
- Fila Ativa.
- Etapas por Nível.
- Histórico e Diferenças.
- Pendências, Configurações e Fontes.

Pode reorganizar abas se melhorar a navegação sem eliminar conteúdo.

O Dashboard precisa incluir **percentual concluído e restante**, gráfico de pizza dinâmico, categorias, progresso permanente, SC, data de coleta, total de etapas pendentes, duração parcial conhecida e cobertura/pendências. A métrica não é um percentual oficial da Supercell.

Cada linha deve deixar óbvios: nome, categoria, instância/quantidade, nível concluído, máximo, próxima etapa, SC aplicáveis/concluídas, duração sem Passe, com Passe, tempo ativo observado, fonte e status de validação.

Campos editáveis precisam de estilo diferente de fórmulas. Use filtros, cabeçalhos congelados e larguras legíveis. O gráfico deve recalcular quando as entradas mudarem. Verifique validade real do XLSX produzido, não apenas a existência do arquivo.

## 5. CLI, configuração e saídas

Proponha e implemente uma execução offline como:

```bash
java -jar clash-village-tracker.jar report --snapshot data/snapshot_2026-10-19.json --catalog data/upgrade_steps.json --gold-pass-boost 0.20 --output output/
```

Inclua um comando para verificar integridade do catálogo quando útil:

```bash
java -jar clash-village-tracker.jar validate-catalog --catalog data/upgrade_steps.json
```

Trate argumentos inválidos e arquivos ausentes com mensagens claras. Em caso de erro, retorne código de saída não zero e registre a causa, sem produzir uma aparência de sucesso.

Arquivos esperados em `output/` (nomes podem mudar com justificativa): `report.xlsx`, `normalized-snapshot.json`, `pending-validation.json`, `diff.json` e log/resumo de execução.

## 6. Testes obrigatórios

Use JUnit 5 e dados de teste sintéticos. Cubra, no mínimo:

- Melhoria iniciada não altera nível concluído.
- Duas Defesas Aéreas com Sobrecargas distintas.
- Sobrecarga só aparece quando elegível.
- Catálogo atualizado com novo máximo preserva histórico.
- Bônus de 20% não é aplicado duas vezes.
- Etapa com duração `null` não soma 0 nem gera total exato.
- Seis construtores fixos; Goblin temporário opcional.
- Filas independentes de Laboratório e Casa dos Pets.
- Importação duplicada não duplica dados.
- Campo ausente em uma fonte não apaga informação conhecida.
- Snapshot histórico permanece imutável.
- Dashboard/Excel possui fórmulas válidas e gráfico vinculado.
- 100% catalogado com cobertura incompleta não produz FULL absoluto confirmado.

Execute `mvn test`, corrija falhas da implementação e informe claramente quando uma verificação não puder ser realizada.

## 7. API oficial e exportação da vila — fase seguinte

Após o MVP passar nos testes:

- Investigue a documentação oficial em `https://developer.clashofclans.com/` e os exemplos disponíveis no projeto.
- Planeje um cliente de API isolado, com token em `CLASH_API_TOKEN`, timeout, tratamento de falhas HTTP e limites de chamadas.
- Registre apenas os campos comprovadamente expostos, sem pressupor construções ou temporizadores.
- **Solicite um exemplo real da exportação do jogo** antes de criar o parser desse formato; preserve o texto bruto e documente o esquema observado.
- Desenvolva reconciliação campo a campo, identificando origem, data e divergência; ausência não implica remoção.
- Deixe JasperReports para depois de estabilizar o Excel. Não o adicione como requisito obrigatório do MVP.

Não use chamadas autenticadas sem configuração autorizada pelo usuário. Nunca coloque tokens, `.env` sensível ou payloads privados em commits/logs.

## 8. Autonomia, checkpoints e entrega

Trabalhe com autonomia para inspeções, edição de arquivos comuns, compilação e testes dentro das permissões do Kiro. Não peça aprovação para cada leitura ou alteração simples. Não contorne mecanismos de segurança e peça autorização quando houver ação destrutiva, publicação ou efeito externo relevante.

Para evitar perder contexto entre sessões, mantenha as especificações e uma pequena nota de progresso em arquivo do projeto, identificando o que foi **implementado**, **testado** e **a fazer**. Se a tarefa for interrompida, retome o trabalho real a partir desse estado, sem alegar que uma etapa está concluída apenas por constar em plano.

Ao terminar, retorne objetivamente:

1. Diagnóstico do repositório e decisões de arquitetura.
2. Funcionalidades implementadas e comportamento real.
3. Lista de arquivos criados/alterados.
4. Comandos exatos de compilação, testes e execução no Windows.
5. Resultado dos testes (passou, falhou, não executado).
6. Arquivos de saída produzidos e como conferi-los.
7. Pendências de dados, fontes e funcionalidades.
8. Próximo incremento recomendado (API/exportação/histórico).

**Comece agora pela leitura do repositório, ajuste apenas as especificações necessárias e siga diretamente para a implementação do MVP offline. Não pare após apresentar um planejamento.**

## 9. Inventário detalhado do estado inicial

Antes de programar, gere uma visão consolidada do snapshot, sem alterar o arquivo original.

- Identifique todas as categorias presentes e ausentes.
- Liste quantos objetos existem por categoria e quantos estão agregados.
- Liste os `entityKey` reconhecidos e aqueles sem mapeamento.
- Mostre campos `UNKNOWN`, `NOT_APPLICABLE`, `CONFLICT` e valores `null`.
- Identifique upgrades ativos e seus níveis concluídos e alvos.
- Determine quantas durações-base possuem fonte verificada.
- Encontre inconsistências entre JSON, CSV, documentação e planilha.
- Verifique a data e versão do catálogo antes de chamar qualquer dado de “atual”.

O resultado dessa etapa deve ser um pequeno diagnóstico verificável. Em seguida, continue implementando o MVP, sem esperar nova solicitação quando não houver bloqueio real.

## 10. Contrato de entrada e validação

Crie validadores que impeçam a aplicação de aceitar dados estruturalmente incoerentes sem aviso.

Casos a rejeitar ou sinalizar:

- `currentLevel` negativo ou superior ao máximo sem justificativa.
- `targetLevel` inferior ao concluído em um upgrade normal.
- Sobrecarga marcada como concluída numa construção não elegível, sem explicação de versão.
- Quantidade de instâncias menor que a soma das distribuições.
- Duas entradas conflitantes com a mesma chave de etapa.
- Duração negativa, token de API armazenado em catálogo ou unidade de tempo ambígua.
- Data de observação ausente em cronômetro informado como real.
- Mesmo item cadastrado simultaneamente como instância e agregado sem reconciliação.

Não descarte silenciosamente o registro: gere um diagnóstico com caminho do campo e motivo.

## 11. Modelo de domínio mínimo

Sugestão de objetos e responsabilidades (adapte aos campos reais dos arquivos):

| Conceito | Responsabilidade |
|---|---|
| `VillageSnapshot` | Identificação da vila, observação e entidades |
| `VillageEntity` | Tipo, categoria, nível, máximos e procedência |
| `BuildingGroup` | Quantidades e distribuição agregada |
| `ActiveUpgrade` | Nível concluído, alvo, status e restante observado |
| `UpgradeStep` | Uma transição por nível, duração e fonte |
| `SuperchargeState` | Estado por carga e por instância/grupo |
| `GameCatalog` | Versão, regras, máximos e etapas |
| `ValidationIssue` | Inconsistência ou lacuna identificada |
| `ProgressReport` | Métricas e totais conhecidos |
| `SnapshotDiff` | Alterações entre duas fotografias |

Não crie classes somente porque elas aparecem nesta lista. Implemente apenas as que tiverem uso concreto no fluxo do MVP.

## 12. Implementar etapa por etapa, não tempo médio

Uma melhoria 9→12 precisa resultar em três itens independentes:

- Etapa 9→10.
- Etapa 10→11.
- Etapa 11→12.

O sistema deve consultar duração, custo opcional e fonte de cada etapa. Uma etapa desconhecida não deve ser substituída pelo tempo da etapa vizinha.

Crie métodos testáveis para enumerar transições permanentes e de Sobrecarga. Retorne também o conjunto de etapas ainda não verificadas.

## 13. Cálculo de tempo com Passe Dourado

Utilize segundos como unidade interna, evitando arredondar dias antes da soma.

- Tempo futuro sem Passe: soma das durações-base confirmadas.
- Tempo futuro com Passe: aplicação do bônus configurável apenas a etapas elegíveis.
- Tempo ativo observado: utilizar exatamente o saldo capturado e sua data de observação.
- Não aplicar duas vezes o bônus já refletido no tempo do jogo.
- Bônus configurável por execução, sem impor 20% para sempre.
- Total exato indisponível se houver alguma etapa obrigatória sem duração confirmada.
- Mostrar subtotal conhecido e quantidade de etapas desconhecidas.

Crie testes para descontos de 0%, 15% e 20%, mais pelo menos um cronômetro observado.

## 14. Separar as capacidades das filas

Implemente ou modele explicitamente as capacidades independentes.

- Seis construtores permanentes para atividades elegíveis.
- Goblin Builder opcional, temporário e fora da capacidade permanente.
- Laboratório com fila própria.
- Casa dos Pets com fila própria.
- Verificação de consumo de construtor para heróis e guardiões conforme mecânicas aplicáveis.
- Transições consecutivas da mesma entidade devem respeitar dependência.

Para a primeira versão, é aceitável apresentar `builderDays`, `labDays` e `petDays` separadamente. Não declare um calendário completo se não houver escalonador confiável implementado.

## 15. Tratamento explícito das construções repetidas

Não suponha que todas as Defesas Aéreas, X-Bestas, Cabanas, Teslas ou Minas tenham o mesmo estágio de Sobrecarga.

- Use instâncias distintas quando a fonte comprovar identidade.
- Use agrupamentos com `countByStage` quando a informação for agregada.
- Permita valores desconhecidos por instância ou grupo.
- Não atribua cargas a unidades específicas por dedução indevida.
- Garanta que a soma das distribuições é consistente com a quantidade total.
- Evite dupla contagem ao reconciliar dados agregados com individuais.

Gere pelo menos uma fixture com quatro defesas de estágios diferentes.

## 16. Supercargas e atualização da versão do jogo

A elegibilidade de SC vem do catálogo, não de regras fixas para todos os edifícios.

- Modelo de SC1, SC2 e SC3 conforme o tipo e a versão.
- Níveis permanentes e SC são trilhas separadas.
- SC futuras só podem ser planejadas quando a elegibilidade for conhecida.
- Uma nova versão do jogo pode alterar máximos e resetar a validade das SC.
- Snapshots antigos não devem ser reescritos.
- Mudanças no denominador de progresso devem ser sinalizadas.

Faça teste em que uma defesa antes FULL recebe novo nível permanente e passa a não estar FULL na versão atual.

## 17. Métricas e regra de FULL absoluto

Calcule pelo menos:

- Progresso permanente por etapas.
- Progresso de Sobrecargas.
- Progresso por categoria.
- Progresso geral catalogado.
- Cobertura da base.
- Quantidade de passos com tempo desconhecido.
- Quantidade de entidades e instâncias não confirmadas.

Não anuncie “100% FULL absoluto” somente porque todas as linhas conhecidas estão concluídas. A condição de certificação exige escopo completo e nenhuma lacuna impeditiva.

Demonstre em teste que 100% catalogado com cobertura incompleta **não** resulta em FULL confirmado.

## 18. Preservação do snapshot e histórico

O JSON histórico de 19/10/2026 deve continuar intocado.

- Gere versões normalizadas em pasta de saída.
- Preserve `observedAt` e fonte por campo.
- Produza diff comparando snapshots somente quando ambos estiverem disponíveis.
- Reimportar um mesmo snapshot não deve duplicar registros.
- A ausência de campo em fonte nova não apaga campo antigo confirmado.
- Diminuição de nível deve gerar conflito ou justificativa rastreável.
- Não utilize cronômetros históricos como se fossem atuais.

Teste a reimportação e a imutabilidade de arquivos de entrada.

## 19. Excel: objetivo de usabilidade

A primeira planilha criada anteriormente ficou difícil de utilizar. A nova versão deve resolver isso de forma concreta.

- Tela inicial limpa, com resumo objetivo.
- Abas por categoria, com nomes curtos.
- Tabelas com filtros e cabeçalhos congelados.
- Colunas de edição destacadas e separadas de fórmulas.
- Uma linha por tipo/grupo, com detalhes de SC em aba própria.
- Links internos no Dashboard para localizar rapidamente informações.
- Formatação coerente de duração, porcentagem, recursos e datas.
- Sem colunas ocultas indispensáveis para entendimento do usuário.
- Aba de pendências para durações desconhecidas e conflitos.

Se necessário, implemente uma planilha-modelo ou gerador de abas independente, mas mantenha o domínio livre de dependências de POI.

## 20. Excel: Dashboard e métricas dinâmicas

O gráfico de pizza deve medir **etapas concluídas desde os níveis iniciais até o máximo vigente**, e não somente o conteúdo introduzido no CV18.

- Concluído vs. restante.
- Progresso permanente e de SC, separados.
- Cobertura dos dados, separada do progresso.
- Data da coleta e versão do catálogo em destaque.
- Indicadores de upgrades em andamento.
- Quantidade de etapas sem duração conhecida.
- Percentuais que se atualizam com a edição da aba de dados.
- Fórmulas que continuam válidas quando novas linhas são adicionadas.
- Mensagem clara caso 100% catalogado não equivalha a FULL certificado.

Valide o XLSX gerado, incluindo abertura correta das abas, fórmulas e objeto gráfico.

## 21. Excel: etapas individuais e comparação de tempos

Na aba de detalhamento, quero uma linha por etapa necessária.

| Campo | Exemplo de finalidade |
|---|---|
| Categoria | Herói, tropa, defesa, coletor |
| Entidade | Nome padronizado |
| Instância/quantidade | Agrupamento ou identificador confiável |
| Etapa | 9→10, 10→11, SC1, SC2 |
| Duração base | Sem Passe |
| Duração com bônus | Cenário do Passe |
| Situação | Pendente, ativa, concluída |
| Saldo observado | Tempo que o jogo mostrou |
| Fonte | Referência consultada |
| Verificação | Confirmado, estimado ou pendente |

A soma dos tempos deve permanecer auditável: o usuário precisa entender de onde vem cada resultado.

## 22. Validação do catálogo antes de gerar relatórios

Implemente um comando de validação que verifique:

- Chaves duplicadas.
- Saltos de nível inválidos.
- Etapas sem duração.
- SC não elegíveis.
- Máximos menores que níveis observados.
- URLs ou versão ausentes, quando obrigatórios.
- Quantidades ou tipos incoerentes.

Durações ausentes devem ser reportadas como pendência, não necessariamente impedir toda geração do Excel. Erros estruturais impeditivos devem retornar código de falha.

## 23. Interface CLI e exemplos de execução

Implemente execução manual local com comandos claros.

```powershell
mvn clean test
mvn package
java -jar target/clash-village-tracker.jar report --snapshot data/snapshot_2026-10-19.json --catalog data/upgrade_steps.json --gold-pass-boost 0.20 --output output/
```

Esses nomes são um contrato inicial: ajuste os exemplos à implementação real e registre-os no README.

O CLI deve validar argumentos, apresentar ajuda e diferenciar `SUCCESS`, `PARTIAL_SUCCESS` e `FAILED`.

## 24. Testes obrigatórios de regressão

Além dos cenários anteriores, inclua:

1. Bebê Dragão 9→12 gera três passos.
2. Torre do Mago 13→17 gera quatro passos.
3. Rainha 105→110 gera cinco passos.
4. Smasher 3→4 permanece 3 até conclusão confirmada.
5. SC1 concluída e SC2 pendente em uma de várias Defesas Aéreas.
6. SC desconhecida não deve ser marcada como não existente.
7. Minutos restantes observados não recebem desconto adicional.
8. Falta de duração impede total exato, sem impedir subtotal.
9. O Goblin não é contado como sétimo construtor permanente.
10. Reimportação duplicada não aumenta quantidades.
11. Excel mantém fórmulas e gráfico ligados a dados editáveis.
12. FULL catalogado com dados desconhecidos não certifica a vila.

Não invente resultado de testes: execute-os e apresente saídas verificáveis.

## 25. Tratamento de erros e observabilidade

Não deixe o programa morrer com uma exceção sem contexto.

- Arquivo de entrada ausente: mensagem com caminho e instrução de correção.
- JSON malformado: motivo e região aproximada do erro, quando disponível.
- Entidade não mapeada: registrar em pendências, preservando dado original.
- Catálogo incompleto: subtotal e quantidades desconhecidas.
- Falha ao criar Excel: status de erro com caminho do arquivo.
- Erros parciais: preservar entradas válidas e reportar limitações.
- Logs não devem expor tokens ou dados protegidos.

Produza resumo final do processamento com quantidade de itens e artefatos gerados.

## 26. Critérios para iniciar a API oficial

A integração autenticada deve ser tratada **após** o MVP offline estar compilando e testado.

- Inspecione `https://developer.clashofclans.com/`.
- Verifique o contrato real de endpoints necessários.
- Não invente dados de construções, SC ou filas.
- Token configurado via `CLASH_API_TOKEN`, nunca no repositório.
- Cliente HTTP desacoplado, com timeout e tratamento de respostas 401/403/404/429/5xx.
- Testes com respostas simuladas e sem dependência de conexão real.
- Campos ausentes na API não podem apagar dados conhecidos por outras fontes.

Se não houver token, documente a etapa e deixe o MVP funcionando offline.

## 27. Exportação da vila: ainda não inventar parser

A importação da exportação nativa do jogo depende de exemplo real do usuário.

Até que esse material exista:

- Prepare interface e documentação do adaptador.
- Não produza JSON sintético alegando que é exportação real.
- Não presuma identificadores, encoding ou estrutura de propriedades.
- Mantenha separado esse adaptador da integração API.
- Planeje testes a partir de amostras reais posteriormente fornecidas.

Esse cuidado evita construir um parser incompatível com a fonte verdadeira.

## 28. Relatórios e JasperReports em fase futura

O Excel é a entrega principal do MVP.

- Cálculos ficam na camada de serviço, nunca dentro da geração POI.
- Gere um DTO de relatório independente do formato de saída.
- JasperReports deve consumir os mesmos resultados quando implementado.
- Não adicione dependências de Jasper sem necessidade imediata.
- Salve um resumo JSON para permitir futura exportação PDF ou frontend.

Prefira implementar um MVP completo a iniciar simultaneamente três tecnologias de apresentação.

## 29. Regra de autonomia e aprovações

Quero que você avance autonomamente nas operações locais de baixo risco permitidas pelo Kiro.

- Inspecione arquivos, crie classes, edite documentação, compile e rode testes sem pedir confirmação a cada passo.
- Não altere repositórios remotos, publique dados ou execute operações destrutivas sem a autorização correspondente.
- Não tente contornar confirmações e permissões do ambiente.
- Diante de uma dúvida secundária, registre suposição explicitamente e prossiga apenas onde há base técnica.
- Peça esclarecimento quando uma ambiguidade impedir resultado correto ou seguro.
- Evite parar no planejamento; implemente um incremento vertical demonstrável.

## 30. Checkpoints para sessões longas

Para não perder contexto ao reiniciar uma sessão, mantenha um registro conciso de progresso dentro do projeto.

Cada checkpoint deve informar:

- Objetivo da tarefa.
- O que foi implementado.
- Arquivos alterados.
- Testes executados e resultados.
- Erros conhecidos.
- Próximos passos.

Nunca marque uma fase como concluída somente por estar documentada no backlog. Releia o estado real antes de retomar o trabalho.

## 31. Saída final exigida do agente

Ao terminar cada incremento, responda com:

1. Análise do estado anterior e decisões importantes.
2. Implementação de fato realizada.
3. Arquivos criados e modificados.
4. Comandos de build/teste executados.
5. Testes aprovados, falhos e não executados.
6. Caminho dos relatórios gerados.
7. Pendências de catálogo, importação e apresentação.
8. Próximo incremento recomendado.

Se o MVP ainda não estiver pronto, declare explicitamente a situação, sem anunciar uma entrega completa.

## 32. Ordem de execução sugerida nesta sessão

Execute esta sequência, adaptando-a ao estado real do repositório:

1. Inspecionar repositório e documentação.
2. Validar os formatos reais dos JSON e CSV.
3. Criar/verificar `pom.xml` e estrutura Java.
4. Implementar importador e validação de snapshot.
5. Implementar importador e validação do catálogo.
6. Implementar cálculo de progresso e enumeração de etapas.
7. Implementar cálculo de durações conhecidas e pendências.
8. Gerar Excel inicial com Dashboard funcional.
9. Criar testes e executar `mvn test`.
10. Documentar comando manual e registrar próximos passos.

Não aguarde a integração online para demonstrar o MVP offline.

## 33. Critério final de aceite do trabalho

Não considero essa tarefa cumprida se você apenas criar requisitos, diagramas ou explicações.

Preciso de uma aplicação Java local que consiga processar o snapshot existente, calcular informações consistentes e gerar o Excel. A saída precisa ser verificável, com testes e documentação de limites conhecidos.

**Comece a implementação após a análise inicial e avance até produzir um incremento executável, respeitando as permissões do ambiente.**

---

# English (EN) — Ready-to-use Kiro prompt

## Role and goal

Act as a **senior Java software engineer** responsible for incrementally building **Clash Village Tracker**. The repository open in Kiro contains TH18 Clash of Clans domain documentation, historical snapshots, upgrade catalogs, a reference workbook, and a specification under `.kiro/specs/clash-village-sync/`.

I want a **locally executable, on-demand Java application** that reads authorized data for my village (`#U8J89QP2`), calculates remaining levels, Supercharges, resources and upgrade durations with full traceability, and generates a **clear, editable Excel workbook with a dynamic Dashboard**. In later phases, it should integrate with the official Supercell API, support the in-game village export, and optionally generate JasperReports.

**Do not stop at planning.** Inspect the existing repository, implement, compile, test, and deliver a real offline MVP. Respect environment permissions and do not perform destructive operations without approval.

## 1. Read and understand before editing code

Start by inspecting the actual repository tree. Open and read:

- `AGENTS.md` and `README.md` for rules and goals.
- `.kiro/steering/clash-cv18.md` for existing persistent context.
- `docs/00_indice.md`, `docs/01_regras_jogo.md`, `docs/15_regras_automacao.md`, `docs/16_modelo_dados.md`, `docs/17_api_e_exportacao.md`, `docs/18_fontes.md`, and `docs/19_testes_e_pendencias.md`.
- Every category-specific document from `docs/02_defesas.md` through `docs/14_construcoes_auxiliares.md` to avoid omitting a game category.
- `.kiro/specs/clash-village-sync/requirements.md`, `design.md`, and `tasks.md`.
- `data/snapshot_2026-10-19.json`, `data/upgrade_steps.json`, and `data/upgrade_steps.csv`.
- `controle_cv18_jaoo_2026-10-19.xlsx` as a visual reference for data and workbook behavior.

**Inspect the files' actual JSON/CSV structures; do not invent field names based on prose.** Check for existing source code, build files, and tests. Avoid duplicating classes or configuration.

After reading, provide a concise inventory of existing modules, discrepancies, and true blockers. Revise inconsistent specifications only when supported by evidence, documenting your reasoning. Then **continue to implementation** without waiting for a second request, as long as the action is authorized.

## 2. Clash of Clans domain contract

Treat the TH18 account and **2026-10-19** snapshot as a historical baseline, not live state. There are **six permanent builders**; the Goblin Builder is temporary. The 20% Gold Pass boost is a configurable snapshot-specific parameter.

Support defenses, auxiliary buildings, storages, collectors, traps, walls, Supercharges, heroes, pets, guardians, troops, spells, siege machines, and crafted-defense attributes.

Non-negotiable rules:

1. `currentLevel` is the last **completed level**. `targetLevel` is the **in-progress upgrade**. During 3→4, the completed level remains 3.
2. Repeated building types may have **different levels and Supercharge states**. Model stable individual instances when available; explicitly label aggregated data.
3. Supercharges are separate from normal levels and can be invalidated by game updates. Catalog versions determine eligibility and applicable stages.
4. Observed timers already reflect current game conditions: **never apply another Gold Pass discount** to `observedRemainingSeconds`.
5. Each pending level has its own duration. Never assume a single fixed duration for a series of upgrades.
6. `null`, `UNKNOWN`, `NOT_APPLICABLE`, `CONFLICT`, and numeric zero are different concepts; **never turn unknown into zero**.
7. The Laboratory, Pet House, and builders have separate scheduling rules; do not divide every duration by six.
8. Never claim public API access to buildings, Supercharges or queues without verifying the actual endpoint. Write an export parser only after obtaining a genuine sample.
9. Reconciled values must retain source, observation time, and confidence.
10. Do not declare the village **fully maxed** while required items are unknown or incomplete.

## 3. Implement the offline MVP now

Create or update a **Maven + Java 17 or 21** project. Prefer Jackson, Apache POI, JUnit 5, Java HttpClient when needed, and consistent logging. Do not add Spring Boot, a database, or JasperReports as mandatory MVP dependencies.

Keep responsibilities separate (`domain`, `catalog`, `importer`, `reconciliation`, `calculation`, `report.excel`, `history`, `cli`). Create classes that are actually used and testable interfaces. Keep business rules independent of Excel generation.

Minimum pipeline:

`load → validate → normalize → reconcile → calculate → generate → persist`

Version 1 must:

1. Load the historical snapshot JSON and upgrade catalog JSON/CSV from configurable paths.
2. Validate format, types, trustworthy IDs, and level consistency.
3. Normalize names/categories while preserving raw unknown fields.
4. Separate completed level, target level, status, and observed remaining countdown.
5. Enumerate **every individual step** from current to maximum level plus eligible Supercharges.
6. Return known-duration subtotals and unknown-step lists instead of manufacturing exact totals.
7. Calculate boosted and unboosted durations without changing observed countdowns.
8. Calculate cumulative step-based progress from early levels, separate permanent/SC progress, and data coverage.
9. Generate an organized XLSX workbook with formulas linked to editable cells and a pie chart.
10. Save normalized snapshot, unresolved issues, and snapshot diff when previous observations exist.
11. Be idempotent: the same input must not duplicate entities or upgrades.
12. Preserve history, original workbooks, and manual corrections unless an explicit merge policy authorizes otherwise.

## 4. Excel requirements

Make the generated workbook **easier to use than the current manual spreadsheet**. Suggested sheets:

- Dashboard.
- Overview.
- Defenses and Supercharges.
- Collectors/Resources.
- Traps and Walls.
- Heroes, Pets, and Guardians.
- Laboratory (troops, spells, siege machines; filterable).
- Active Upgrades.
- Upgrade Steps.
- History and Diffs.
- Validation Issues, Settings, and Sources.

You may improve the sheet layout if it simplifies navigation without dropping information.

The Dashboard must show **completed and remaining percentages**, a linked pie chart, category progress, permanent vs SC progress, snapshot timestamp, outstanding steps, known-duration subtotal, and data coverage/unresolved issues. This is a project-defined metric, not an official Supercell completion percentage.

Each record should clearly show item, category, stable instance/quantity, completed level, max level, next upgrade, eligible/completed SC stages, unboosted duration, Gold Pass duration, observed active countdown, source, and verification status.

Style editable cells differently from computed formulas. Add filters, frozen headers, readable column widths, and sensible formatting. Editing source values must recalculate the chart. Validate the **actual** XLSX structure, not just whether a file exists.

## 5. CLI, configuration and outputs

Implement an offline entry point similar to:

```bash
java -jar clash-village-tracker.jar report --snapshot data/snapshot_2026-10-19.json --catalog data/upgrade_steps.json --gold-pass-boost 0.20 --output output/
```

Optionally add catalog validation:

```bash
java -jar clash-village-tracker.jar validate-catalog --catalog data/upgrade_steps.json
```

Provide clear errors for invalid arguments or missing files. Return a non-zero exit code on failure rather than reporting success.

Desired artifacts under `output/` (names may vary with justification): `report.xlsx`, `normalized-snapshot.json`, `pending-validation.json`, `diff.json`, and a concise execution summary/log.

## 6. Mandatory tests

Use JUnit 5 with synthetic fixtures. Cover at least:

- Starting an upgrade does not advance completed level.
- Two Air Defenses with distinct Supercharge progress.
- Supercharge steps exist only when eligible.
- Changing max levels in a newer catalog preserves historical state.
- A 20% boost is never applied twice.
- A `null` duration does not silently count as zero or yield an exact total.
- Six permanent builders and optional temporary Goblin Builder.
- Independent Laboratory and Pet House queues.
- Repeated import is idempotent.
- Missing source fields do not erase known data.
- Historical snapshots are immutable.
- Excel formulas are valid and the chart is linked.
- 100% cataloged-step progress with incomplete coverage does not certify a fully maxed village.

Run `mvn test`, fix implementation failures, and disclose any validation you could not execute.

## 7. Official API and in-game export — next phase

After the offline MVP is tested:

- Review official API documentation at `https://developer.clashofclans.com/` and any real examples already available in the repo.
- Plan an isolated API client with `CLASH_API_TOKEN`, timeouts, HTTP error handling, and rate-limit awareness.
- Model only fields proven to exist in a real API contract/response; do not assume building states or timers are exposed.
- **Request a genuine in-game village-export example** before writing an export parser. Preserve the raw text and document the observed schema.
- Implement field-level reconciliation with source, date, and conflicts; missing does not mean deleted.
- Postpone JasperReports until the workbook implementation is stable.

Do not make authenticated API calls without authorized configuration. Never commit tokens, sensitive `.env` files, or private payloads.

## 8. Autonomy, checkpoints and deliverables

Work autonomously for repository inspection, ordinary edits, builds, and tests within Kiro's permissions. Do not ask for approval for each routine command. Do not bypass security controls; ask before destructive operations, publishing, or significant external side effects.

To avoid context loss between sessions, maintain concise progress notes/spec tasks that distinguish **implemented**, **tested**, and **pending** work. If interrupted, resume from verified repository state instead of treating planned work as finished.

When complete, report:

1. Repository inventory and architecture decisions.
2. Actual implemented features and behavior.
3. Files created/modified.
4. Exact Windows build, test, and run commands.
5. Test outcomes (passed, failed, not run).
6. Generated outputs and how to verify them.
7. Data and functionality gaps.
8. The recommended next increment (API/export/history).

**Start by reading the repository, align only the specifications that need changes, and proceed directly to the offline MVP implementation. Do not stop after producing a plan.**

## 9. Detailed inventory of the initial state

Before writing code, create a consolidated view of the snapshot without altering the original file.

- Identify every represented and missing category.
- Count entities per category and determine which are aggregated.
- List recognized `entityKey` values and unmapped names.
- Report `UNKNOWN`, `NOT_APPLICABLE`, `CONFLICT`, and `null` fields.
- Identify active upgrades with completed and target levels.
- Count base durations backed by verified sources.
- Find contradictions among JSON, CSV, documentation, and workbook.
- Check catalog timestamp and version before describing any data as “current”.

Provide a short, verifiable diagnostic. Then continue the MVP implementation without waiting for a new prompt unless there is a real blocker.

## 10. Input and validation contract

Build validators that prevent silently accepting structurally inconsistent information.

Reject or flag:

- Negative `currentLevel` or one above max without justification.
- `targetLevel` below the completed level for a normal upgrade.
- A completed Supercharge on an ineligible building without a version explanation.
- An instance count smaller than the sum of level distributions.
- Two conflicting stage entries with the same logical identity.
- Negative durations, API credentials embedded in catalogs, or ambiguous duration units.
- Missing observation timestamp for a timer claimed as actually observed.
- An item represented both as individual instances and aggregate without reconciliation.

Never silently discard an invalid record; return a diagnostic with field path and explanation.

## 11. Minimum domain model

Suggested domain objects and responsibilities (adapt them to real existing fields):

| Concept | Responsibility |
|---|---|
| `VillageSnapshot` | Village identity, observation, and entities |
| `VillageEntity` | Type, category, levels, maximum, provenance |
| `BuildingGroup` | Aggregated counts and distributions |
| `ActiveUpgrade` | Completed level, target, status, observed remainder |
| `UpgradeStep` | One level transition with duration and source |
| `SuperchargeState` | Per-stage state by instance/group |
| `GameCatalog` | Version, rules, maximums, stages |
| `ValidationIssue` | Detected inconsistency or data gap |
| `ProgressReport` | Metrics and known totals |
| `SnapshotDiff` | Changes between observations |

Do not create classes merely because they are listed here. Implement only those with real responsibilities in the MVP flow.

## 12. Step-by-step upgrades, not average duration

An upgrade from level 9 to level 12 must produce three independent steps:

- 9→10.
- 10→11.
- 11→12.

Look up each step's duration, optional cost, and provenance. Never fill a missing duration using a neighboring step's time.

Implement testable methods for enumerating permanent transitions and Supercharge transitions. Return unverified steps explicitly.

## 13. Gold Pass duration calculation

Store internal durations as seconds and avoid rounding individual days before summing.

- Future duration without Pass: sum of confirmed base durations.
- Future duration with Pass: configurable reduction on eligible steps only.
- Observed active remainder: preserve exactly the captured value and its timestamp.
- Never apply Gold Pass twice to already discounted in-game time.
- Make the boost configurable per run; do not assume it remains 20% indefinitely.
- Exact totals are unavailable when a required step lacks confirmed duration.
- Show known subtotal and count of unknown steps.

Test 0%, 15%, and 20% boost scenarios plus at least one observed timer.

## 14. Separate queue capacities

Implement or explicitly model independent capacities.

- Six permanent builders for eligible tasks.
- Optional temporary Goblin Builder, outside permanent capacity.
- Independent Laboratory queue.
- Independent Pet House queue.
- Validate actual builder consumption of heroes and guardians based on applicable rules.
- Consecutive levels of the same entity have dependencies.

The first release may report `builderDays`, `labDays`, and `petDays` separately. Do not announce a comprehensive calendar forecast without a reliable scheduler.

## 15. Explicit handling of repeated buildings

Do not assume every Air Defense, X-Bow, Builder's Hut, Tesla, or Gold Mine has the same Supercharge stage.

- Represent instances separately only when the source supplies reliable identity.
- Represent aggregates using `countByStage` when needed.
- Allow unknown values for instances or groups.
- Do not infer which specific unit received a charge.
- Validate that distributions match total counts.
- Prevent double counting when reconciling aggregates and individuals.

Create a fixture containing four defenses with different charge states.

## 16. Supercharges and game-version changes

SC eligibility comes from the catalog, never a universal rule for every building.

- Model SC1, SC2, and SC3 as appropriate by type and version.
- Keep permanent levels and SC as separate progression tracks.
- Plan future SC only when eligibility is established.
- New game versions can change max levels and invalidate previous SC states.
- Never rewrite historical snapshots.
- Flag changes to the progress denominator.

Add a test where a previously maxed defense receives a new permanent level and is no longer maxed in the current catalog version.

## 17. Progress metrics and absolute completion

At minimum calculate:

- Permanent progression by cataloged steps.
- Supercharge progression.
- Per-category progress.
- Overall cataloged progress.
- Data coverage.
- Number of steps without known durations.
- Number of unverified entities/instances.

Do not claim “100% absolutely maxed” simply because all known rows are complete. Certification requires a complete scope and no unresolved required data gaps.

Demonstrate with a test that 100% cataloged progress and incomplete coverage **does not** imply confirmed absolute max.

## 18. Snapshot and history preservation

The historical JSON from October 19, 2026 must remain untouched.

- Write normalized versions to an output folder.
- Preserve field-level `observedAt` and source.
- Produce diffs only when two snapshots are available.
- Reimporting identical data must not duplicate entries.
- A missing field in the new source never erases an older confirmed field.
- Decreasing levels require a conflict record or traceable explanation.
- Do not present old timers as live remaining time.

Test reimport and input-file immutability.

## 19. Excel usability goal

The first workbook was difficult to navigate. The new version must concretely improve usability.

- A clean overview on the first screen.
- Focused sheets with short names.
- Filterable tables with frozen headers.
- Editable cells highlighted separately from formula cells.
- One row per entity type/group, with SC details on a dedicated sheet.
- Internal Dashboard links for quick navigation.
- Consistent formatting for time, percentages, resources, and dates.
- Avoid hiding essential information in unexplained columns.
- A validation sheet for unknown durations and conflicts.

If needed, introduce a workbook template or standalone sheet generators, but keep domain rules independent from POI.

## 20. Excel Dashboard and dynamic metrics

The pie chart must represent **completed steps since initial progression up to current catalog maximums**, not just content introduced at TH18.

- Completed versus remaining.
- Separate permanent and SC percentages.
- Data coverage separate from progress.
- Visible observation date and catalog version.
- Indicators of active upgrades.
- Count of unknown durations.
- Percentages updated when users edit data sheets.
- Formulas that remain valid as new records are added.
- Clear warning if 100% cataloged does not mean certified absolute max.

Validate the generated workbook, including sheet accessibility, formula integrity, and the chart object.

## 21. Excel per-step timing comparison

The detailed sheet must contain one row per needed stage.

| Field | Example purpose |
|---|---|
| Category | Hero, troop, defense, collector |
| Entity | Normalized name |
| Instance/count | Aggregate or reliable identifier |
| Step | 9→10, 10→11, SC1, SC2 |
| Base duration | Without Gold Pass |
| Boosted duration | Gold Pass scenario |
| Status | Pending, active, completed |
| Observed remainder | In-game time |
| Source | Consulted reference |
| Verification | Confirmed, estimated, pending |

Duration totals must be auditable: users need to understand how each result was produced.

## 22. Catalog validation before reporting

Implement a validation command to check:

- Duplicate keys.
- Invalid level transitions.
- Missing step duration.
- Ineligible SC.
- Maximum levels lower than observed levels.
- Missing URLs/version where mandatory.
- Inconsistent counts or types.

Missing durations should be reported as validation issues, not necessarily block all Excel generation. Blocking structural errors must return failure status.

## 23. CLI and execution examples

Implement a clear, manual local execution flow.

```powershell
mvn clean test
mvn package
java -jar target/clash-village-tracker.jar report --snapshot data/snapshot_2026-10-19.json --catalog data/upgrade_steps.json --gold-pass-boost 0.20 --output output/
```

These commands are an initial contract: update them to match the actual implementation and document them in README.

The CLI must validate arguments, display help, and distinguish `SUCCESS`, `PARTIAL_SUCCESS`, and `FAILED`.

## 24. Mandatory regression tests

In addition to earlier scenarios, include:

1. Baby Dragon 9→12 produces three steps.
2. Wizard Tower 13→17 produces four steps.
3. Archer Queen 105→110 produces five steps.
4. Smasher 3→4 remains at completed level 3 until confirmed completion.
5. One of several Air Defenses has SC1 completed and SC2 pending.
6. Unknown SC is not mistaken for no SC.
7. Observed remaining minutes are not discounted again.
8. Missing duration blocks exact total, not known subtotal.
9. Goblin is never permanently counted as a seventh builder.
10. Repeated imports do not increase entity counts.
11. Workbook formulas and pie chart remain connected to editable inputs.
12. Cataloged FULL with unknown required data does not certify the village.

Do not fabricate test results: execute tests and provide verifiable outcomes.

## 25. Error handling and observability

Do not let the program crash without actionable context.

- Missing input: show path and instructions.
- Malformed JSON: show explanation and location when possible.
- Unmapped entity: place in validation issues, preserve original input.
- Incomplete catalog: show subtotal and unknown counts.
- Excel generation failure: log error status and path.
- Partial errors: preserve valid input and report limitations.
- Logs must never disclose tokens or sensitive data.

Produce a final processing summary with item counts and generated files.

## 26. Prerequisites for the official API

Authenticated integration comes **after** a successful offline MVP build and tests.

- Inspect `https://developer.clashofclans.com/`.
- Verify actual endpoint contracts.
- Do not invent buildings, SC, or queue fields.
- Read tokens from `CLASH_API_TOKEN`, never the repository.
- Isolate HTTP client with timeouts and 401/403/404/429/5xx handling.
- Use simulated HTTP responses for tests and avoid requiring internet.
- Missing API fields must not erase known data from other sources.

If no token is configured, document the limitation and keep the offline MVP functional.

## 27. Village export: no invented parser

The native game-export importer depends on a real sample from the user.

Until a sample is available:

- Prepare an adapter interface and documentation.
- Never pass fabricated JSON as an actual game export.
- Do not assume IDs, encoding, or property names.
- Keep this adapter distinct from the official API client.
- Plan tests around genuinely provided samples later.

This prevents implementing a parser incompatible with the real format.

## 28. JasperReports as a later phase

Excel is the main MVP deliverable.

- Keep calculations in the service/domain layer, never inside POI formatting.
- Produce a report DTO independent of presentation format.
- Later JasperReports can consume the same values.
- Do not add Jasper dependencies without an immediate requirement.
- Save JSON summaries for future PDF or frontend export.

Prefer completing one useful MVP over simultaneously building three presentation technologies.

## 29. Autonomy and approvals

Work autonomously on low-risk local tasks permitted by Kiro.

- Inspect, create classes, edit documentation, compile, and test without repeated approval requests.
- Do not change remote repositories, publish data, or run destructive operations without required authorization.
- Never bypass environment permissions.
- For secondary uncertainty, record explicit assumptions and proceed only where technically justified.
- Ask for clarification when ambiguity blocks correct or safe behavior.
- Do not stop at planning; deliver a demonstrable executable vertical slice.

## 30. Checkpoints for long sessions

Maintain a concise project progress record so work survives session restarts.

Each checkpoint must contain:

- Task goal.
- Implemented work.
- Changed files.
- Tests run and results.
- Known errors.
- Next steps.

Never mark a phase complete merely because it appears in a task list. Reinspect actual project state before resuming.

## 31. Required agent response

After each implementation increment, report:

1. Previous state and key decisions.
2. Actual implementation completed.
3. Created and modified files.
4. Build/test commands run.
5. Passed, failed, and unexecuted tests.
6. Generated report paths.
7. Catalog, import, and presentation issues.
8. Recommended next increment.

If the MVP is not finished, state its actual status without announcing complete delivery.

## 32. Suggested execution order for this session

Follow this sequence, adapting it to the repository's actual state:

1. Inspect repository and docs.
2. Validate real JSON/CSV shapes.
3. Create/verify `pom.xml` and Java layout.
4. Implement snapshot import and validation.
5. Implement catalog import and validation.
6. Implement progress calculations and step enumeration.
7. Implement known durations and missing-data reporting.
8. Generate a first workbook with working Dashboard.
9. Add tests and run `mvn test`.
10. Document manual commands and next steps.

Do not wait for online integration before demonstrating the offline MVP.

## 33. Final task acceptance criterion

I will not consider this task complete if you only create requirements, diagrams, or explanations.

I need a local Java application that processes the existing snapshot, computes consistent information, and generates Excel. The results must be verifiable, with tests and documented limitations.

**Begin implementation after initial analysis and proceed to an executable increment, following the environment's permissions.**
