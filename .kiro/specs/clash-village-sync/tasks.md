# Backlog de implementação para Kiro

- [x] Criar projeto Maven Java 17/21 com Jackson, Java HTTP Client, Apache POI e testes JUnit.
- [x] Definir records/DTOs segundo `docs/16_modelo_dados.md`.
- [x] Implementar carregamento e validação de `snapshot_2026-10-19.json` e `upgrade_steps.json` sem conexões externas.
- [x] Implementar normalização de nomes/IDs legados e reconciliação de dados manuais.
- [x] Implementar `--offline` e geração de XLSX + Dashboard conectado aos dados.
- [x] Implementar coleta isolada do perfil pela API oficial com token em ambiente e testes HTTP simulados.
- [ ] Solicitar amostra real da exportação nativa da vila e, em seguida, construir parser versionado.
- [x] Implementar detecção de melhorias em andamento e preservação do nível concluído.
- [x] Implementar cálculo de tempos e cargas por fila sem descontar Passe duas vezes.
- [x] Gerar diferenças entre snapshots (JSON e resumo legível).
- [ ] Completar a regressão para os 12 cenários de `docs/19_testes_e_pendencias.md` (a suíte atual cobre os contratos centrais; ainda falta representação individual comprovada de quatro construções repetidas).
- [ ] Adicionar tarefa CLI semanal/manual opcional, sem executar automaticamente sem configuração.
- [ ] (Opcional) Relatório JasperReports, após o XLSX estar funcional.
