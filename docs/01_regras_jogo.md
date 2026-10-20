# Regras funcionais — Clash of Clans, Vila Principal CV18

## 1. Tipos de progresso
- **Permanente:** nível de prédio/defesa, armadilha, tropa, feitiço, cerco, herói, pet, guardião; geralmente exige concluir sequência 1→2→...→máximo.
- **Sobrecarga:** etapas adicionais 1 e 2 em defesas; 1, 2 e 3 em coletores. Exige o nível permanente máximo antes de iniciar. São **temporárias**: a disponibilização de novo nível permanente pode remover as cargas existentes.
- **Muros:** melhoria imediata (sem duração de construtor); progresso medido por número e custo. Neste snapshot: 300 de 325 ainda por elevar ao 19.
- **Defesas artesanais (crafted):** possuem atributos/módulos independentes, com níveis ainda não identificados; não preencher níveis padrão por suposição.

## 2. Filas paralelas
- Construtores permanentes: **6**; o Goblin Builder, quando disponível, é temporário e não integra o limite fixo.
- Os trabalhos de construção, certas melhorias de herói e guardião requerem construtor e consomem vagas compartilhadas quando aplicável.
- Laboratório tem **1 pesquisa por vez**, independente dos construtores.
- Casa dos Pets tem **1 melhoria de pet por vez**, independente das outras filas.
- Quando há K trabalhos em paralelo, somar seus dias é `dias-construtor`; dividir por seis é só limite inferior/estimativa, não previsão exata (há dependências, tempos indivisíveis, temporários e prioridades).

## 3. Temporizadores e Passe Dourado
- `base_days` é a duração sem Passe. `pass_days = base_days × (1 - bonus)` com bônus atual 0,20, sujeito a mudanças sazonais.
- `remaining_now_days` lido do cronômetro **já reflete** condições aplicadas no início ou ajustes do jogo e NÃO deve receber novo desconto.
- Boosts, Livros, Martelos e Poções precisam ser eventos no histórico; não altere tempos-base originais.

## 4. Semântica do nível
- `current_level` é o **último nível concluído**.
- `upgrade_in_progress.target_level` é o nível sendo construído, que só atualiza `current_level` no término.
- Um item com `level == max_level` só é FULL absoluto se todas as etapas de Sobrecarga aplicáveis também estiverem completas.
- O progresso baseado em nível desde o início é uma métrica gerencial, não um percentual oficial do jogo; pode não corresponder a recursos/tempo restantes.

## 5. Eventos e sincronização
- Todo snapshot deve registrar tag, data/hora e origem.
- Para SC use nível **individual por construção**, ou contagens agrupadas (`sc1_completed`,`sc2_completed`,`sc3_completed`) acompanhadas de confiabilidade; não inventar IDs físicos.
- Valores conflitantes entre print, export e API geram alerta, não sobrescrita silenciosa.

Fontes: [Supercharge](https://www.clash.ninja/guides/what-are-supercharge-levels), [exportação da vila](https://www.clash.ninja/blog/one-tap-village-updating-is-here).
