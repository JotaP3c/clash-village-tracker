# Design técnico sugerido — Java 17/21, Maven

## Componentes
- `client/supercell`: REST client `GET /v1/players/%23U8J89QP2` com Bearer token via ambiente; retries limitados/timeout/status 401,403,404,429.
- `importer/village`: parser por versão para o export nativo real; não inventar campos de JSON antes da primeira amostra.
- `catalog`: máximos, duração por nível, duração de SC, versão de patch e URLs das fontes.
- `domain`: VillageSnapshot, VillageItem, UpgradeJob, UpgradeDefinition, SourceInfo, Confidence.
- `merge`: reconciliação `export nativo > API (somente campos expostos) > manual > inferência`; conflitos são reportados.
- `planner`: geração de etapas restantes, bônus do Passe, capacidade de construtores e gargalos (lab, pets, builders).
- `output`: Apache POI para XLSX, Jackson para JSON, JasperReports opcional para relatório estático.

## Identificadores
Use `canonicalItemId` estável em inglês; a tabela visual pode usar PT-BR. Para múltiplas construções iguais, use instâncias identificadas pelo export quando possível ou agregados (quantidade/nível/cargas) até existir ID real.

## Progresso do Dashboard
`(soma níveis permanentes concluídos + soma etapas SC concluídas) / (soma níveis máximos das unidades incluídas + soma SC possíveis)`.
Exiba como `progresso catalogado por etapas`, nunca como métrica oficial nem como porcentagem do tempo restante. Coloque cobertura do cadastro em cartão separado.

## Estrutura de CLI
`sync --tag '#U8J89QP2' --export arquivo.txt --out ./output --pass-boost 0.20`
`report --snapshot arquivo.json --out ./output`
`validate-catalog --catalog data/upgrade_steps.json`

## Segurança
Não versionar API tokens nem payloads privados. Usar `CLASH_API_TOKEN`, `.gitignore`, redigir segredos em logs e testes com dados sintéticos.
