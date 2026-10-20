# Backlog de implementação para Kiro

- [ ] Criar projeto Maven Java 17/21 com Jackson, Java HTTP Client, Apache POI e testes JUnit.
- [ ] Definir records/DTOs segundo `docs/16_modelo_dados.md`.
- [ ] Implementar carregamento e validação de `snapshot_2026-10-19.json` e `upgrade_steps.json` sem conexões externas.
- [ ] Implementar normalização de nomes/IDs e reconciliar dados manuais.
- [ ] Implementar `--offline` e geração de XLSX + Dashboard conectado aos dados.
- [ ] Implementar coleta da API oficial com token em ambiente e testes HTTP simulados.
- [ ] Solicitar amostra real da exportação nativa da vila e, em seguida, construir/parser versionado.
- [ ] Implementar detecção de melhorias em andamento e eventos de conclusão.
- [ ] Implementar cálculo de tempos e gargalos sem descontar Passe duas vezes.
- [ ] Gerar diferenças entre snapshots (JSON e resumo legível).
- [ ] Adicionar regressão para os 12 cenários de `docs/19_testes_e_pendencias.md`.
- [ ] Adicionar tarefa CLI semanal/manual opcional, sem executar automaticamente sem configuração.
- [ ] (Opcional) Relatório JasperReports, após o XLSX estar funcional.
