# Requisitos — Clash Village Sync (Java)

## Objetivo
Executar sob demanda uma rotina Java que reconcilie dados oficiais do jogador, uma exportação da vila obtida manualmente no jogo e complementos manuais, e gere a planilha XLSX com histórico e Dashboard de evolução cumulativa até o FULL do CV18, incluindo Sobrecargas.

## Requisitos funcionais
- RF01: Identificar a vila pela tag `#U8J89QP2` e armazenar a data/hora da leitura.
- RF02: Importar API oficial somente para campos presentes no JSON retornado, incluindo heróis, tropas e feitiços quando disponíveis.
- RF03: Importar *village export* do Clash of Clans com esquema descoberto a partir de amostra real; preservar o payload bruto.
- RF04: Incorporar correções manuais com marcação de fonte e sem sobrescrever dados ausentes na API.
- RF05: Preservar estado de upgrade iniciado, estágio, nível alvo e tempo restante; proibir subir o nível permanentemente antes da conclusão.
- RF06: Modelar 6 construtores fixos, Goblin temporário opcional, 1 fila de laboratório e 1 fila de pets. Os tempos em *builder-days* diferem dos dias de calendário.
- RF07: Modelar nível permanente e SC1, SC2, SC3 individualmente; supercargas podem zerar após atualização do jogo.
- RF08: Calcular tempos sem Passe e com bônus parametrizável. Não reduzir novamente um saldo já observado no jogo.
- RF09: Gerar XLSX mantendo abas Dashboard, Controle Geral, Fila Ativa, Etapas por Nível e categorias separadas, com gráfico de pizza vinculado às fórmulas.
- RF10: Gerar relatório de diferenças (`diff.json`, opcional JasperReports) e preservar histórico imutável de snapshots.
- RF11: Trabalhar offline com arquivos JSON/CSV e emitir avisos claros para dados desconhecidos/versões incompatíveis.

## Critérios de aceitação
Seguir os cenários em `docs/19_testes_e_pendencias.md`. Os números base são `data/snapshot_2026-10-19.json`; não tratar como estado atual depois de 19/10 sem novo snapshot.
