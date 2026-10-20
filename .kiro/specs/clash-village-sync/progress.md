# Checkpoint de desenvolvimento

Data: 2026-10-20

## Implementado

- Projeto Java 17/Maven com Wrapper 3.9.9 e JAR executável.
- Pipeline offline de snapshot e catálogo: importação JSON/CSV, validação, normalização, reconciliação, cálculo, XLSX e persistência histórica.
- Preservação de `null/UNKNOWN` para estados de Sobrecarga; chave composta por categoria e nome legado no cálculo/diff.
- Cliente `JavaHttpSupercellPlayerClient` e caso de uso `FetchPlayerProfileApplication`.
- Comando `fetch-player`, token apenas em `CLASH_API_TOKEN`, timeout, encoding da tag e classificação de 401/403/404/429/5xx.
- DTO da API restrito aos campos de topo documentados; arrays internos permanecem opacos e nenhum campo de construções/SC/filas é presumido.

## Testado

- Build limpo e testes Maven com Java 17.
- Importadores reais JSON/CSV, cálculos, reconciliação, CLI, Excel e validação.
- API contra servidor HTTP local simulado; nenhuma credencial ou chamada externa usada nos testes.

## Limitações conhecidas

- Catálogo `legacy-unversioned`, parcial e com durações ausentes; `FULL` absoluto permanece bloqueado.
- O perfil oficial não fornece neste adaptador construções, Sobrecargas, filas ou cronômetros.
- A exportação nativa ainda não possui amostra real; nenhum parser foi inventado.
- Alterações manuais em `report.xlsx` ainda não possuem política segura de merge e podem ser substituídas por nova geração no mesmo diretório.
- Entidades repetidas continuam agregadas quando a fonte não comprova identidade física.

## Próximos passos

1. Receber uma exportação nativa real e sanitizada para descobrir o schema.
2. Evoluir procedência/reconciliação por campo antes de combinar API e snapshot manual.
3. Versionar um catálogo completo para progresso por transições e certificação de cobertura.
4. Definir política de merge para edições manuais do Excel.

## Recuperação após exclusão acidental — 2026-10-20

- Restaurados a partir do histórico local do Kiro: `FetchPlayerProfileApplication.java`, `CatalogImporter.java` e `VillageEntity.java`.
- Inventário Maven confirmado: 64/64 fontes de produção presentes e 9/9 fontes de teste presentes.
- Build limpo recompôs `target/clash-village-tracker.jar`; 8 suítes, 38 testes, 0 falhas, 0 erros e 0 ignorados.
- IntelliJ alinhado localmente ao SDK registrado `ms-17`, language level 17 e recarga Maven automática.
- Kiro configurado para JavaSE-17, importação Maven automática e Maven Wrapper.
- O log do Kiro registrou conflito de bundle ASM entre `redhat.java 1.58.0` e `vscode-java-test 0.47.0`; foi selecionado `vscode-java-test 0.45.0`, sem o bundle duplicado. O cache JDT/OSGi corrompido foi copiado para pastas `*.backup-*` e esvaziado. É necessário recarregar a janela do Kiro uma vez para iniciar o novo servidor Java.
