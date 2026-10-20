# Diagnóstico verificável do estado inicial

Data da análise: 2026-10-20. Nenhum arquivo de entrada foi alterado.

- Snapshot histórico: `data/snapshot_2026-10-19.json`, vila `#U8J89QP2`, CV18, 12 categorias, 123 registros, quantidade física declarada 541, 26 registros com `quantity > 1` e 6 linhas de fila.
- Cobertura ausente: 11 `current_level` nulos e 3 `max_level` nulos. A entrada `Bomba Aérea adicional` possui zero explícito e parcial; zero não foi reinterpretado como desconhecido.
- Identidade: o snapshot não contém IDs oficiais ou IDs físicos por instância. As chaves `legacy_ptbr_*` são técnicas e determinísticas, sempre combinadas com a categoria; não são identificadores Supercell.
- Catálogo legado: JSON e CSV possuem as mesmas 215 linhas. Há 205 linhas com duração-base e 10 com `null`; ponderadas por `count`, são 336 ocorrências conhecidas e 309 desconhecidas.
- Estado ativo: 5 linhas do catálogo estão marcadas em andamento; a fila do snapshot registra trabalhos de construtor e laboratório. O saldo observado é histórico e não recebe novo desconto do Passe.
- Versão: os arquivos não informam versão formal do catálogo. A aplicação registra `legacy-unversioned`, considera o escopo incompleto e não certifica `FULL` absoluto.
- Inconsistências relevantes: catálogo mistura definição da etapa com estado/quantidade do snapshot; não há custos/moedas completos; timers não têm hora/fuso; SC e níveis de construções repetidas estão agregados; a planilha de referência usa fórmulas/faixas fixas e possui cache inconsistente para o zero parcial.
- API: o recurso oficial de jogador é tratado em adaptador separado. Ele não é usado como fonte de construções, Sobrecargas, filas ou cronômetros.
