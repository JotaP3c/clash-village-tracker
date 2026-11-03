# Prompt para o próximo incremento — E2 PostgreSQL

Atualizado em 2026-10-21. E1 (captura integral e auditável da resposta da API) está implementada e verificada com `.\mvnw.cmd -o verify`. Este prompt inicia somente E2. A API web e o frontend são etapas futuras; não os implemente nesta execução.

Recomendação já registrada na especificação: PostgreSQL local, Flyway, API Java/Spring Boot somente leitura e frontend Angular. O frontend foi separado para `C:\Kiro\clash-village-tracker-frontend`; F0 cria a fundação Angular 21 sem dados reais. Este prompt continua sendo exclusivamente para E2.

Copie o bloco abaixo para o agente escolhido (Codex ou Kiro) com o repositório aberto.

```text
Trabalhe no projeto C:\Kiro\clash-village-tracker.

Leia AGENTS.md, README.md, .kiro/steering/clash-cv18.md e os documentos obrigatórios definidos em AGENTS.md. Leia todos os arquivos de .kiro/specs/clash-village-sync/, em especial next-steps.md, requirements.md, design.md, tasks.md e progress.md. Inspecione o código, o pom.xml, os testes e os dados reais antes de editar. Reaproveite a captura E1 existente em fetch-player; não recrie o cliente nem mapeie campos da API sem evidência.

O usuário quer consultar evolução por datas, armazenar histórico e futuramente ver gráficos numa aplicação Angular no repositório separado `C:\Kiro\clash-village-tracker-frontend`. Recomende uma API Java de leitura com Spring Boot entre o navegador e PostgreSQL. Repositórios aqui significa a camada Java de persistência; não crie outro repositório Git agora. O browser nunca deve acessar o PostgreSQL ou receber CLASH_API_TOKEN/credenciais do banco.

NESTA EXECUÇÃO IMPLEMENTE SOMENTE E2: persistência PostgreSQL dedicada para evidências/coletas e observações históricas, com migrações versionadas, repositórios realmente utilizados e um caminho explícito de importação/armazenamento. Atualize o mapa E3–E6 para a próxima implementação. Não implemente API web, Angular, gráficos ou comparação temporal nesta etapa.

1. Confirme que a branch atual é develop antes de editar; não troque de branch, não faça commit nem push. Inspecione a estrutura Maven atual para escolher a integração de menor impacto, preservando o JAR, a CLI e os testes offline.
2. Antes de acessar PostgreSQL, confirme que as variáveis externas identificam um destino local dedicado chamado clash_village_tracker_dev. Use CLASH_DB_URL, CLASH_DB_USER, CLASH_DB_PASSWORD e CLASH_DB_SCHEMA. Não imprima senhas/URLs com credenciais. Não escolha um banco existente por suposição. Nunca altere, limpe, recrie ou migre outro banco/schema local.
3. Adicione pgJDBC e Flyway com versões compatíveis com Java 17 e fixadas no Maven. Crie migrações SQL incrementais, documentadas e imutáveis depois de aplicadas. A execução da migração deve ser explícita; não rode DDL no startup, em report ou fetch-player. Uma migração já aplicada deve ser corrigida por outra migração, sem apagar dados.
4. Modele somente o necessário ao incremento: identidade de coleta/importação, jogador, origem, resultado/status, instante de recebimento, hash e bytes integrais da resposta (bytea ou referência imutável ao response.bin), snapshot, precisão temporal e observações de entidades/campos com procedência. JSONB pode ajudar consultas, mas não substitui a representação bruta original. Não use hash sozinho como identidade da coleta: duas observações iguais em datas diferentes são distintas.
5. Implemente interfaces/repositórios Java em persistence e adaptadores PostgreSQL usados por um caso de uso real. Preserve o perfil interpretado separado dos bytes brutos. Reimportar a mesma coleta deve ser idempotente; nova coleta em outro instante deve criar nova observação. Gravações do snapshot e relações devem ser transacionais. Acrescente índices e restrições de identidade úteis a consultas por jogador/data.
6. Prepare a importação do snapshot legado data/snapshot_2026-10-19.json sem modificar o arquivo: observedDate=2026-10-19, precisão DATE e hora desconhecida. Registre o instante da importação separadamente; não invente uma coleta nos 15 dias anteriores nem promova a data histórica a horário atual. Não sincronize automaticamente o DTO parcial da API com construções ou com o Excel.
7. DBeaver será usado para inspeção. Documente banco/schema, variáveis, execução explícita das migrações, conexão no DBeaver sem segredos versionados e SELECTs de verificação. Crie o banco dedicado somente se a conexão/permissão e o alvo estiverem claramente configurados. Se faltarem variáveis, acesso ou PostgreSQL, conclua migrações/código/testes locais e informe a criação/conexão física pendente; não adivinhe e não alegue teste integrado.
8. Teste unitariamente configuração, repositórios e idempotência. Com PostgreSQL dedicado configurado, aplique migrações, grave fixture sintética, reimporte, inspecione nova observação e prove rollback. Caso não haja alvo configurado, não tente acessar bancos locais: marque os testes de integração como não executados e mantenha todos os testes offline. Rode o Maven Wrapper com Java 17, incluindo regressão report/validate-catalog/fetch-player sem token.
9. Atualize tasks.md, progress.md, design.md e next-steps.md com fatos verificáveis, resultados e limitações. Mantenha etapas futuras separadas: E3 domínio/reconciliação; E4 comparação temporal/Excel; E5 API de leitura Spring Boot; E6 frontend Angular com dados reais. F0 já prepara o workspace Angular no repositório separado; não o misture com o escopo de persistência.

Preserve data/*.json, data/*.csv, planilha original, histórico e modo offline. Não grave ou exiba tokens. Não invente entidades/instâncias/campos de resposta. Não afirme chamada real à API sem executá-la com CLASH_API_TOKEN configurado no ambiente. Não comece frontend nem API web. Não faça commit/push nesta execução.

Na resposta final, explique a arquitetura implementada e a razão das tabelas/repositorios, liste arquivos alterados, comandos reais e testes (aprovados/falhos/não executados), indique se o banco dedicado foi realmente criado/migrado, limitações e o próximo passo. Diferencie documentação planejada de funcionalidade implementada.
```
