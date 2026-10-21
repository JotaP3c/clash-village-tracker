# API oficial, exportação da vila e integração

## API oficial
Portal: https://developer.clashofclans.com/ . Endpoint típico: `GET https://api.clashofclans.com/v1/players/%23U8J89QP2` com Bearer token. A tag `#` na URL deve ser escapada para `%23`.

Campos publicados/documentados no recurso de jogador: `tag`, `name`, `townHallLevel`, `expLevel`, `troops`, `spells`, `heroes`, `heroEquipment` (ver contrato vigente). **Não assumir** arrays de edifícios, SC e cronômetros de upgrades sem validação.

O comando local `fetch-player` captura o corpo HTTP como bytes antes de decodificar o JSON. Cada execução cria uma pasta única em `output/supercell/collections/` com `response.bin`, `metadata.json` (tag solicitada e retornada, endpoint, status, instantes, Content-Type/codificação, tamanho e SHA-256) e, somente após parse estrutural válido e tag correspondente, `player-profile.json` com o DTO interpretado. A presença de `metadata.json` sela uma coleção completa; diretórios sem ele resultam de falha de persistência e não são considerados coletas concluídas. Erros HTTP, JSON malformado e tag incompatível mantêm o corpo e o diagnóstico, sem perfil. Nenhuma coleta sobrescreve outra.

Execução manual no PowerShell (a chave fica somente no ambiente do processo):

```powershell
$env:CLASH_API_TOKEN = "<seu-token>"
java -jar target\clash-village-tracker.jar fetch-player --tag "#U8J89QP2" --output output
Remove-Item Env:CLASH_API_TOKEN
```

O perfil API não é mesclado automaticamente ao snapshot ou à planilha. Testes usam servidor HTTP local; uma chamada real só pode ser declarada após uma execução manual com o ambiente configurado.

## Exportação de vila (dado essencial para esta automação)
O jogo passou a oferecer opção de **exportar a vila para a área de transferência**; o Clash Ninja relata que essa exportação inclui níveis de construções e melhorias ativas e pode atualizar trackers. URL: https://www.clash.ninja/blog/one-tap-village-updating-is-here

### Fluxo recomendado
1. Solicitar ao usuário o export gerado por **Configurações do Clash of Clans**; não desenvolver parser às cegas.
2. Inspecionar a estrutura e preservar os bytes originais em fixture local sob `.gitignore` se houver identificadores.
3. Construir DTO Jackson que cubra `structures`, `upgradeJobs`, `quantity` etc. **somente após observar os nomes reais dos campos**; versionar `schemaVersion` e testes para diferentes versões.
4. Fundir com API oficial do player e overrides manuais. Não usar scraping ou interação automatizada no cliente do jogo.
5. Gerar Excel e diff, nunca sobrescrevendo campos manuais não representados nas fontes.

## Segurança
Token por `CLASH_API_TOKEN`, IP whitelist conforme a política atual, timeout e tratamento de 429. Proteger arquivos de exportação local; não enviar o token nem dumps privados a sites desconhecidos. Mesmo um CSV/planilha gerado localmente não deve publicar token.

## Exemplo de invocação
`java -jar target/clash-cv18-tracker.jar sync --tag "#U8J89QP2" --export ./input/village-export.json --out ./output`

## Dúvidas antes do parser
Formato real da exportação, IDs, atualização das Sobrecargas, se upgrades ativos incluem `endTime` ou duração restante, e se o perfil da API traz os pets e guardiões no momento. Registre evidência real antes de anunciar cobertura total.
