# Requisitos — Clash Village Sync (Java)

Atualizado em 2026-10-21. Este documento define requisitos; o estado implementado está em [progress.md](progress.md). A ordem de execução está em [next-steps.md](next-steps.md).

## Objetivo e decisões do usuário

Executar localmente uma aplicação Java que importe evidências da vila, reconcilie fontes, acompanhe evolução e gere XLSX reproduzível. Evoluir para PostgreSQL local, comparação por período e, posteriormente, uma interface de consulta.

- PostgreSQL é a persistência escolhida para a próxima etapa; DBeaver será usado para administrar e inspecionar os dados. A aplicação se conectará via JDBC.
- O modo offline JSON/CSV deve continuar funcionando sem banco, token ou internet.
- O snapshot de 2026-10-19 é histórico. O banco não cria observações de datas anteriores às fontes disponíveis.
- A fundação da interface será Angular 21, considerando a experiência prévia do usuário, no repositório separado `clash-village-tracker-frontend`. O consumo de dados reais só começa após uma API de leitura estável.
- Recomendação para a API de leitura: Spring Boot em Java, mantendo regras de domínio e CLI offline existentes. Confirmar a integração menos disruptiva com o Maven atual antes de criar módulos ou mover pacotes.
- O navegador chama somente a API HTTP. Credenciais do PostgreSQL e `CLASH_API_TOKEN` permanecem no backend/processo local e nunca são incluídas no frontend.
- A ferramenta recomendada para migrações versionadas é Flyway; confirmar compatibilidade e fixar versão na etapa E2.

## Requisitos funcionais

- **RF01 — Identidade e observação:** identificar jogador, origem, instante ou data realmente observada e precisão temporal. Preservar agrupamentos sem inventar IDs físicos.
- **RF02 — Perfil oficial:** consumir apenas campos comprovados pelo contrato/resposta real. Reaproveitar `fetch-player` e o cliente HTTP existentes.
- **RF03 — Exportação nativa:** implementar parser versionado somente após receber uma amostra real; sua ausência não bloqueia a coleta de perfil.
- **RF04 — Correções e procedência:** registrar correção manual, campo, valor, autor quando disponível, motivo e data. Campo omitido não apaga valor conhecido nem renova sua data de observação.
- **RF05 — Melhorias ativas:** preservar nível concluído, alvo, quantidade e saldo observado separadamente. Prazo previsto vencido não confirma conclusão.
- **RF06 — Filas:** seis construtores permanentes no cenário de referência; Goblin temporário configurável; laboratório e pets com filas próprias. Carga de trabalho não equivale a cronograma.
- **RF07 — Sobrecargas:** controlar etapas elegíveis por versão e instância ou agregado. Reset ou mudança de elegibilidade gera reconciliação rastreável.
- **RF08 — Tempos:** aplicar bônus somente em etapas futuras elegíveis; saldo observado não recebe novo desconto. Duração desconhecida produz subtotal e pendência.
- **RF09 — Excel:** manter Dashboard, Visão Geral, fila, etapas, categorias, fontes e pendências. Definir entradas editáveis, fórmulas coerentes e política que preserve correções manuais.
- **RF10 — Histórico:** registrar fotografias imutáveis e diferenças de níveis, `countAtMax`, distribuições, SC, filas, cobertura e configuração aplicável.
- **RF11 — Execução offline:** preservar os comandos atuais de arquivos locais e avisos de catálogo incompleto.
- **RF12 — Evidência da API:** preservar resposta integral recebida, hash, origem, tag requisitada/retornada, resultado HTTP, instante de recebimento e identificação da coleta. O DTO parcial não substitui a evidência bruta.
- **RF13 — PostgreSQL:** adicionar configuração externa, migrações versionadas e repositórios isolados de cálculo/Excel. Cada gravação de estado consolidado e suas relações deve ser transacional.
- **RF14 — Idempotência:** reimportar a mesma coleta não duplica entidades ou snapshots. Duas coletas independentes em datas distintas continuam observações distintas mesmo com JSON idêntico.
- **RF15 — Reprodutibilidade:** relacionar relatório a snapshots, catálogo, versão da métrica, cenário e bônus. Mudanças dessas entradas não reescrevem o relatório histórico nem sua base.
- **RF16 — Comparação por período:** selecionar duas posições conhecidas do mesmo jogador e expor período solicitado, fotografias efetivamente usadas, precisão e defasagem conforme `design.md`.
- **RF17 — Histórico insuficiente:** ausência de baseline, de segunda observação ou de ordenação confiável gera diagnóstico; não equivale a nenhuma mudança confirmada.
- **RF18 — XLSX temporal:** gerar histórico e comparação com datas filtráveis, valores anterior/atual, diferenças, fontes e conflitos. Filtro do Excel atua nos dados exportados; nova consulta ao banco exige geração Java ou futura interface.
- **RF19 — Catálogos diferentes:** separar alteração do jogador de alteração de teto/escopo. Comparação de percentuais deve identificar denominadores e versões diferentes.
- **RF20 — API de leitura:** consultas de histórico, comparação e métricas devem retornar DTOs independentes do XLSX, com filtros de jogador, categoria e período.
- **RF21 — Interface Angular:** consumir somente a API de leitura; apresentar filtros por data, cartões de cobertura/pendências e gráficos ligados às observações efetivamente selecionadas.

## Requisitos técnicos e limites

- Usar Java 17/Maven e os pacotes existentes; acrescentar JDBC/Flyway na etapa PostgreSQL e Spring Boot Web somente na etapa da API.
- Manter a aplicação Angular em seu repositório próprio, sem compartilhar credenciais ou acessar PostgreSQL diretamente.
- Credenciais somente por configuração externa: `CLASH_API_TOKEN`, `CLASH_DB_URL`, `CLASH_DB_USER`, `CLASH_DB_PASSWORD` e schema configurado. Não incluir senhas na URL, arquivos versionados, logs ou relatórios.
- JSON/CSV históricos e planilha de referência permanecem preservados. Correções geram novas evidências.
- Não conectar a bancos locais não configurados nem executar DDL automaticamente durante `report` ou `fetch-player`.
- Toda funcionalidade nova deve distinguir implementado, testado, planejado e dependência externa.

## Critérios de aceitação

Além de `docs/19_testes_e_pendencias.md`, executar os aceites por etapa de `next-steps.md`:

1. Resposta com campo não mapeado é preservada integralmente; token não aparece nos artefatos.
2. Reimportação da mesma coleta é idempotente; coleta posterior sem mudança mantém nova observação.
3. Importar hoje o snapshot histórico preserva observação em 2026-10-19 e hora desconhecida.
4. Falha transacional não deixa snapshot consolidado com relações incompletas.
5. Pendência recalculada acompanha níveis e SC novos; Java e fórmulas Excel concordam.
6. Comparação com datas esparsas mostra fotografias reais e defasagem; sem histórico retorna diagnóstico.
7. Desconhecido para conhecido representa enriquecimento; agregado que muda `countAtMax` produz diff sem ID físico inventado.
8. Mudança de catálogo é distinguida de progresso do jogador; timer vencido não confirma término.
9. Relatório temporal e futura API usam a mesma consulta e os mesmos resultados de domínio.
10. Testes offline não exigem PostgreSQL nem acesso real à Supercell; testes PostgreSQL usam alvo dedicado e são relatados separadamente.
