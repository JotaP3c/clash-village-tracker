# Fontes, verificação e limitações

Data de conferência: **19/10/2026**. Dados do jogador: prints fornecidos nesta conversa; máximo e tempos de terceiros **não são oficiais da Supercell**.

| Escopo | Fonte | Confiabilidade/uso |
|---|---|---|
| API oficial | https://developer.clashofclans.com/ | Fonte primária para campos retornados pelo endpoint de jogador. |
| Contrato de player | https://github.com/MasiaAntoine/clash-of-clan-api-doc-official/blob/en/API/players/get-player.md | Documentação ilustrativa dos campos; validar OpenAPI oficial ao implementar. |
| Exportação de vila | https://www.clash.ninja/blog/one-tap-village-updating-is-here | Relato de exportação de estruturas e upgrades ativos no jogo; conferir formato real. |
| Máximos CV18 | https://www.clash.ninja/guides/max-levels-for-each-th | Clash Ninja; confirmar após patches. |
| Sobrecargas | https://www.clash.ninja/guides/what-are-supercharge-levels | Clash Ninja; 2 cargas defesas, 3 coletores, tempos base. |
| Tropas | https://www.clashtrack.online/data/troops | ClashTrack; cada nível; dados marcados Jul/2026 e atualização Oct/2026; validar mudanças posteriores. |
| Feitiços | https://www.clashtrack.online/data/spells | ClashTrack; cada nível; revisões com o jogo se houver discrepâncias. |
| Cerco | https://www.clashtrack.online/data/sieges | ClashTrack; cada nível. |
| Heróis | https://clashprogress.com/database/heroes/ | ClashProgress; tempos por alvo de herói. |
| Pets | https://clashlevels.com/pet/ | Clash Levels; tempos por alvo de pet. |
| Morteiro | https://clashprogress.com/database/defenses/mortar | ClashProgress; upgrade 18 e SC. |

## Procedência por linha
Cada entrada de `data/upgrade_steps.csv` possui `source`, `confidence` e `base_days`. Quando fonte ou valor não foram verificados, `base_days` é nulo; não usar estimativa como certeza.

## Incertezas identificadas
- Tempos individuais de parte das tropas, demais edifícios, guardiões e armadilhas ainda exigem catálogo validado; foram geradas etapas pendentes com tempo nulo em vez de números inventados.
- Itens sem nível visível: estações artesanais e parte de construções auxiliares; não contam como FULL confirmado.
- Contagens de sobrecarga agrupadas são aproximadas conforme o menu de melhorias, não identificam cada instância física.
- O tempo dos upgrades que já estavam rodando é um saldo de screenshot, não duração base, e muda a cada minuto.