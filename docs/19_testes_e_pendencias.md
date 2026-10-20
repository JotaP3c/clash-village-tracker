# Testes de aceitação e pendências

## Casos funcionais obrigatórios
1. Torre do Mago `15/17`: não marcar FULL; gerar 15→16 e 16→17.
2. Morteiro `4 total, 2 já no 18`: só duas melhorias permanentes 17→18; Sobrecargas SC1 e SC2 planejadas para quatro, respeitando disponibilidade.
3. Defesa Aérea `4`, uma SC1 concluída: pendem SC1×3 e SC2×4; conferir toda progressão ao editar SC2.
4. Mina de Ouro `7`, uma SC1 concluída e duas SC1 em andamento: gráfico só sobe quando os timers forem concluídos/importados como finalizados.
5. Longshot e Logger `5/5`: FULL; Smasher `3/5`, upgrade para 4 em andamento.
6. Laboratório Veneno `8→9 em andamento`: o saldo 22h32m não recebe desconto extra do Passe; fila única.
7. Pets Diggy 10/15, Lagarto 10/15, Sneezy 8/10 e Raven 2/10; Yak 15 FULL.
8. 6 construtores fixos; Goblin temporário não aumenta capacidade de base.
9. 300 muros no 18 e 25 no 19: 25/325 no nível alvo, 300 pendentes.
10. Duração nula: célula deve dizer `A VALIDAR`, jamais zero dia; soma só de tempos confirmados deve ser chamada `subtotal verificado`.
11. A fórmula do Dashboard deve mudar quando aumentar um nível ou contagem de SC concluída no Controle Geral; zero não equivale a desconhecido.
12. Dados antigos nunca sobrescrevem snapshot mais recente sem registrar divergência; deduplicar mesmas imagens/capturas repetidas.

## Pendências antes de 100% de confiança
- Exportação nativa da vila real e exemplo do JSON bruto para schema do parser.
- Confirmar nível de cada construção auxiliar e atributos da Crafting Station (vários prints mostram categorias, não o nível do atributo).
- Confirmar linha do Smasher 4→5 com tempo atualizado depois do progresso atual.
- Completar o catálogo de duração/recursos dos níveis faltantes de tropas e demais itens; há células com tempo nulo marcadas como tal.
- Conferir SCs individuais restantes se houver atualização feita após os prints de 19/10.
- Validar se o Passe está efetivamente em 20% em todas as categorias e até quando; permitir boost por data.
- Atualizar teto do jogo se patch liberar novos níveis; proibir usar snapshot de outubro como limite eterno.

## Critério de pronto
O MVP gera XLSX sem erros de fórmula, com 9 categorias e gráfico de pizza ligado ao controle, informa origem de cada campo, exporta diff, trata offline e tem testes que provam os 12 casos acima.
