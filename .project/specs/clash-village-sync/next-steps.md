# Próximos passos — API, PostgreSQL e evolução por período

> **Atualização 2026-11-03 — direção full-stack:** a prioridade do produto passou a ser uma aplicação web funcional. As etapas **E5 (API de leitura Spring Boot)** e **E6 (Angular com dados reais)** foram implementadas como um MVP vertical sobre o snapshot histórico, com integração real backend↔frontend verificada (ver `progress.md`, checkpoint 2026-11-03). A persistência **PostgreSQL (E2/E3) e a comparação por período (E4) continuam planejadas** e não são pré-requisito do MVP web; elas habilitarão histórico e evolução por datas quando implementadas. O texto abaixo é o planejamento original de dados, mantido como referência.

Data da decisão original: 2026-10-21. E1 foi implementada e verificada localmente.

## 1. Decisões e ponto de partida

- Aplicação local Java 17/Maven, com código já existente e comandos `report`, `validate-catalog` e `fetch-player`.
- PostgreSQL local será a persistência da evolução; DBeaver será usado para inspeção. SQLite deixa de ser a opção proposta para esta evolução.
- Objetivo seguinte: persistir coletas e observações no PostgreSQL, comparar posições históricas (inclusive 15 dias atrás até hoje), gerar Excel filtrável e disponibilizar consultas para uma interface.
- Nome proposto para o banco de desenvolvimento dedicado: `clash_village_tracker_dev`. Criar/conectar somente quando o alvo e as credenciais estiverem configurados e confirmados; nunca reutilizar outro banco por suposição.
- Flyway é a ferramenta recomendada para migrações SQL versionadas. DBeaver fica para administração e inspeção, não para guardar credenciais no código.
- Recomendação de interface: Angular, aproveitando a experiência do usuário. Recomendação de API de leitura: Spring Boot sobre Java 17, reaproveitando o domínio e os casos de uso atuais.
- O frontend foi separado pelo usuário em `C:\Kiro\clash-village-tracker-frontend`, com remoto próprio. Ele usará Angular 21 e Node 22.12.0, ambos instalados localmente. Criar repositórios Java/adaptadores PostgreSQL em `persistence` no backend.
- O navegador acessará somente a API HTTP. PostgreSQL e token da Supercell serão acessados apenas pelo processo backend.
- Snapshot histórico inicial: 2026-10-19, 123 registros em 12 categorias. Nenhuma coleta anterior foi comprovada; uma comparação de 15 dias não estará disponível só com essa fotografia.
- E1 preserva bytes HTTP, SHA-256, metadados allowlisted e perfil interpretado separado em coletas locais imutáveis; testes locais não consultam a API real.
- PostgreSQL e sincronização entre perfil API e modelo da vila continuam pendentes.

## 2. Referências e leitura do agente

Seguir `AGENTS.md`, `README.md`, steering e documentos obrigatórios. Ler `requirements.md`, `design.md`, `tasks.md` e `progress.md` desta pasta antes de implementar. O prompt operacional está em `../../../NEXT_STEPS.md`.

O contrato da API foi analisado a partir do texto fornecido pelo usuário nesta sessão. Anexo de referência, quando disponível no ambiente: `C:/Users/joao.correa/.codex/attachments/2ef4efa8-4d92-49f2-8be4-c4316c219e3b/Pasted text.txt`. É uma cópia externa ao repositório, não uma dependência do build; não copiar credenciais nem exigir esse caminho em outra máquina. As conclusões úteis estão registradas em `design.md` e em `docs/17_api_e_exportacao.md`.

## 3. Ordem de execução e limites

| Etapa | Entrega | Dependência para avançar |
|---|---|---|
| E1 | Captura integral e auditável do perfil, reaproveitando o comando atual. | Corpo original preservado e testes HTTP locais aprovados. |
| F0 | Fundação Angular separada: workspace, navegação, tema inicial e contrato de API ainda simulado. | Repositório frontend com `main` e build local aprovado. |
| E2 | Banco PostgreSQL local dedicado, Flyway, migrações e repositórios Java de evidência/histórico. | Alvo dedicado configurado; idempotência e transações verificadas. |
| E3 | Mapeamento/reconciliação persistidos e correções de cálculo/Excel. | Dados comprovados, pendências recalculadas e métricas consistentes. |
| E4 | Comparação por período e Excel de evolução. | Duas observações comparáveis, seleção temporal e fontes rastreáveis. |
| E5 | **Implementada (MVP, 2026-11-03):** API Java/Spring Boot somente leitura para o dashboard, reutilizando `ReportApplication.analyze`. Histórico por período depende de E2–E4. | Contrato `/api/village/*` estável sobre o snapshot atual. |
| E6 | **Implementada (MVP, 2026-11-03):** dashboard Angular consumindo a API real, com progresso, categorias e upgrades. Filtros de período/gráficos temporais dependem de E4. | API E5 disponível e integração CORS verificada. |

F0 e E2 podem avançar em paralelo, pois F0 não consulta dados reais nem cria contratos definitivos. E2 não deve incluir a aplicação visual. O parser de exportação nativa aguarda amostra real e não bloqueia E1. Infraestrutura e evidência podem evoluir antes de catálogo completo, mas indicadores não podem ser publicados como exatos enquanto houver lacunas.

## 4. E1 — Captura integral da API (implementada em 2026-10-21)

### Resultado esperado

**Concluído.** Cada resposta recebida por `fetch-player` é guardada em `response.bin` antes do parse. `metadata.json` contém identificador UUID, hash, origem, tags, status, instantes, Content-Type/charset/Content-Encoding, tamanho e referências aos artefatos. `player-profile.json` existe somente após parse válido e correspondência da tag. Respostas rejeitadas mantêm bytes e diagnóstico sem perfil. A presença de `metadata.json` sela a coleta; coletas posteriores recebem diretório distinto.

Evoluir `JavaHttpSupercellPlayerClient`, `SupercellPlayerProfile`, `FetchPlayerProfileApplication` e seus resultados/testes somente onde necessário. Preservar compatibilidade de `fetch-player` e do modo offline.

- Separar corpo integral da resposta, metadados da requisição/coleta e perfil interpretado. Definir contrato de retorno sem duplicar o cliente.
- Preservar bytes do corpo recebido e calcular SHA-256 antes de decodificar/mapear, guardando codificação, origem, tag requisitada/retornada, status HTTP e instante de recebimento com `Clock` testável. Avaliar `BodyHandlers.ofByteArray` no cliente existente para não perder a representação original.
- Persistir evidência em destino local ignorado pelo Git, identificado por coleta; não sobrescrever coletas anteriores.
- Campo não mapeado não pode desaparecer do corpo bruto. Resposta inválida deve manter evidência e diagnóstico sem ser promovida a perfil/snapshot válido, conforme política de armazenamento documentada.
- Manter timeout, encoding da tag, classificação de falhas e tratamento de campos ausentes. Detectar tag incompatível sem associar a resposta ao jogador solicitado.
- Não registrar cabeçalho Authorization, chave ou variáveis sensíveis. Usar a chave escolhida pelo usuário exclusivamente via `CLASH_API_TOKEN`.

### Aceite e verificação

1. Servidor HTTP local devolve campo desconhecido, inclusive fora dos oito campos atuais: evidência integral preservada.
2. Ausência e array vazio continuam distintos; corpo malformado não gera observação validada.
3. Tag é codificada e tag divergente produz diagnóstico.
4. Duas coletas independentes não sobrescrevem arquivos, inclusive com relógio fixo nos testes.
5. Artefatos/logs não contêm a credencial sintética usada no teste.
6. Testes existentes de HTTP, CLI e relatório continuam aprovados.
7. Executar `.\mvnw.cmd verify` ou modo offline equivalente se dependências estiverem em cache; não declarar teste real se só usou mocks.

Consulta real manual, disponível quando o ambiente do processo estiver configurado (não executada nesta entrega):

```powershell
java -jar target\clash-village-tracker.jar fetch-player --tag "#U8J89QP2" --output output\coleta-api
```

`CLASH_API_TOKEN` estava ausente do ambiente desta sessão. A implementação e os testes locais foram concluídos; não buscar a chave em logs, histórico de chat, arquivos de sessão ou commits.

O detalhe verificável de arquivos e comandos desta entrega está em `progress.md`. Próximo incremento autorizado no roteiro: E2 (PostgreSQL local), após definir alvo dedicado/configuração, sem incluir comparação temporal nem frontend.

**Fora de E1:** executar migração, conectar ao PostgreSQL, criar frontend/backend web, interpretar exportação nativa, mapear arrays sem evidência e sincronizar automaticamente o perfil com o Excel. Essas entregas têm etapas próprias.

## 5. E2 — PostgreSQL, migrações e repositórios Java

- Confirmar versão local e alvo dedicado `clash_village_tracker_dev`; verificar a conexão efetiva antes de qualquer DDL. Configuração externa proposta: `CLASH_DB_URL`, `CLASH_DB_USER`, `CLASH_DB_PASSWORD`, `CLASH_DB_SCHEMA`.
- Adicionar pgJDBC e Flyway compatíveis com Java 17, com versões fixadas e scripts rastreáveis. Aplicar migrações explicitamente no banco/schema dedicado; nunca ao iniciar um comando offline.
- Se não houver configuração clara ou o banco dedicado não estiver disponível, concluir código, migrações e testes locais e deixar a criação/conexão física como pendência; não adivinhar usuário, senha, porta nem alvo.
- Implementar interfaces de repositório e adaptadores PostgreSQL usados pelos casos de uso. Não criar métodos CRUD sem consumidor, nem colocar SQL em cálculo, CLI ou Excel.
- Começar por coleta e evidência; expandir para fotografia consolidada, entidades, fila, observações por campo, catálogos, conflitos e execuções de relatório conforme os casos de uso forem implementados.
- Guardar bytes integrais com hash em `bytea` ou referência a artefato imutável, além de JSONB apenas quando útil. JSONB não preserva fielmente a representação original.
- Identificar a coleta independentemente do hash: reimportar o mesmo artefato não duplica o estado; JSON idêntico coletado em outra data ainda registra nova observação.
- Não reescrever snapshot antigo. Importação histórica conserva `observedDate`, precisão DATE e hora desconhecida, além do instante real de importação.
- Transações para estado consolidado e relações, restrições de identidade e índices de consulta por jogador/data.
- Migração explícita em alvo configurado; não alterar outros bancos/schemas locais nem iniciar DDL durante um relatório offline. Não apagar nem recriar o banco para corrigir uma migração já aplicada: criar migração nova.
- Documentar conexão pelo DBeaver, schema, tabelas, consultas SELECT e forma de executar migrações. Exemplos não devem conter dados de conexão reais.

**Aceite:** aplicar migrações no banco/schema dedicado, importar fixture pequena, reimportar sem duplicidade, registrar nova observação do mesmo conteúdo em coleta posterior, simular falha/rollback e comprovar preservação histórica. Sem alvo configurado, relatar os testes de integração não executados, mantendo os testes offline funcionais.

## 6. E3 — Condições para publicar métricas confiáveis

Problemas observados na análise atual, ainda sem correção:

| Problema | Resultado esperado |
|---|---|
| `CalculationEngine` usa quantidades pendentes legadas. | Novo nível/SC no snapshot recalcula as etapas; Bebê Dragão no máximo não mantém três melhorias antigas. |
| Java e fórmulas Excel tratam desconhecidos de maneira diferente. | Mesmas entradas e definição produzem os mesmos totais e percentuais. |
| Abas de categoria aparentam editáveis, mas são cópias. | Definir fonte editável e vínculos; testar alteração e recálculo. |
| Regeração substitui planilha no mesmo diretório. | Diretório por execução e contrato explícito para preservar/mesclar correções. |
| Merge preserva valores sem procedência temporal por campo e sem revalidação completa. | Comparar fontes equivalentes, jogador, data e confiança; preservar conflito e validar o resultado. |
| Diff não cobre `countAtMax`, filas, bônus e cobertura. | Comparação completa com valores anteriores/atuais e fontes. |
| Histórico por hash do snapshot não diferencia cenários/catálogos de relatório. | Execuções derivadas com identidade própria e referências imutáveis. |

O catálogo futuro será independente da vila, com versão e transições próprias. Progresso deve contar etapas aplicáveis, com cobertura separada. Etapa ausente impede total exato. FULL permanece bloqueado enquanto houver incompletude ou conflitos impeditivos.

Mapear somente campos observados na API. Confirmar significado de `name`, `village` e `maxLevel`, classificação de pets/guardiões e escopo de equipamentos; nomes desconhecidos vão para revisão. Ausência na API preserva dados manuais/exportados e sua data original.

## 7. E4 — O que significa comparar 15 dias atrás com hoje

A política completa está no contrato temporal de `design.md`:

- Datas são interpretadas em `America/Sao_Paulo`; últimos 15 dias usa `hoje.minusDays(15)` até hoje com relógio testável.
- Comparar a posição mais recente conhecida até cada data, respeitando instante de execução para hoje, precisão e idade máxima configurável.
- Exibir datas pedidas e observações realmente selecionadas. Não usar leitura futura para preencher o passado.
- Se não existe baseline ou há só uma observação, informar comparação indisponível. Não fabricar 15 dias de histórico a partir do snapshot de 19/10.
- Se baseline for mais antigo, informar defasagem: não é possível atribuir toda a variação exatamente ao período solicitado.
- Aumento observado é variação confirmada entre coletas, sem data exata de conclusão. Desconhecido para conhecido é enriquecimento; ausência de campo não prova ausência de mudança.
- Comparar agregados sem atribuir identidades físicas; `countAtMax` e SC parciais precisam aparecer.
- Mudança de catálogo/denominador fica separada da evolução do jogador.

### Excel

Exportar tabela histórica com datas reais e filtros, resumo do período e lista de alterações/conflitos. Incluir jogador, fotografias/IDs usados, precisão, defasagem, catálogos, níveis/quantidades/SC anteriores e atuais, origem e cobertura.

Inicialmente o período é selecionado na geração Java. O filtro da planilha atua nas linhas exportadas; ele não consulta o PostgreSQL automaticamente. Novo período fora do conjunto exportado exige nova geração ou futura interface. Um seletor que refaça toda a comparação dentro do Excel requer implementação e testes específicos.

**Aceite:** casos com duas coletas, sem baseline, mesma fotografia, intervalo sem coleta, precisão mista, mudança de catálogo, alteração agregada, conflito e virada de dia no fuso local. Java e Excel devem usar o mesmo par de fotografias e os mesmos resultados.

## 8. E5 — API de leitura e E6 — interface Angular

### E5 — API Java/Spring Boot (planejada)

- Avaliar a integração menos disruptiva de Spring Boot Web ao projeto Maven Java 17. Preservar o artefato/CLI offline e reutilizar casos de uso, domínio e repositórios; não duplicar regras numa aplicação web paralela.
- Expor somente leitura inicialmente, com DTOs independentes de Excel. Rotas propostas: resumo do dashboard por jogador/período, lista de snapshots e evolução agregada por categoria/entidade. Definir contrato e paginação antes de estabilizar.
- Validar datas, jogador, categoria, cobertura e ausência de baseline. A API deve devolver as datas/IDs efetivamente usados e avisos de lacunas.
- Credenciais do banco e `CLASH_API_TOKEN` ficam no backend. Limitar CORS ao endereço local do Angular durante desenvolvimento; não publicar a API nesta etapa.

### F0 — fundação do frontend Angular (iniciada)

- Repositório: `C:\Kiro\clash-village-tracker-frontend`, remoto GitHub próprio e branch `main`. A URL deve usar a autenticação SSH já configurada, não HTTPS sem credencial.
- Gerar workspace Angular 21 com SCSS e Router, usando Node 22.12.0. Não usar Angular novo com Node 12, pois essa combinação não é suportada.
- Criar estrutura de tela inicial sem dados falsos de evolução: shell, rota de dashboard, estados explícitos de API indisponível e histórico ainda indisponível. Não colocar token, senha de banco ou URL com credencial em `environment`.
- Criar serviço HTTP e tipos de resposta como contrato provisório, sem inventar uma API concluída. A URL local do backend será configurada externamente quando E5 existir.
- Commitar e publicar a fundação no repositório frontend depois de `npm run build` aprovado. A criação do banco, API Java, gráficos de evolução e filtros com dados reais permanecem fora de F0.

### E6 — frontend Angular com dados reais (planejado)

- Evoluir o workspace Angular no repositório `C:\Kiro\clash-village-tracker-frontend`. Usar Router e `HttpClient` para consumir exclusivamente a API E5. O navegador nunca acessa PostgreSQL nem recebe token/credenciais.
- Primeira tela: jogador e intervalo de datas (incluindo atalho de 15 dias), cartões de cobertura e pendências, gráfico temporal e tabela filtrável por categoria/entidade.
- Mostrar somente observações reais, indicar datas efetivas, baseline ausente, intervalos sem coleta e cobertura incompleta. Não preencher pontos de gráfico por interpolação nem apresentar snapshot histórico como consulta atual.
- Escolher biblioteca de gráficos ao implementar a primeira tela, comparando compatibilidade e manutenção com a versão Angular adotada.

E2 deve apenas registrar o contrato preliminar do frontend; F0 é uma fundação independente. A sequência dos dados é E2 → E3 → E4 → E5 → E6, com testes e documentação por etapa.

## 9. Entrega por sessão

Atualizar `tasks.md` e `progress.md` com arquivos alterados, comandos reais, resultados dos testes, limitações e próximo incremento. Separar teste local simulado, teste PostgreSQL e chamada real à API. Não marcar etapas futuras como concluídas por terem sido documentadas. Commit/push só quando solicitados.
