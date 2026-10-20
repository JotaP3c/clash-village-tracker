# Modelo de dados sugerido (Java 17+)

```java
record VillageSnapshot(String playerTag, int townHall, Instant observedAt,
    List<VillageItem> items, List<UpgradeJob> activeJobs, SourceInfo source) {}
record VillageItem(String id, Category category, int quantity,
    Integer levelCompleted, Integer maxLevel,
    Integer countAtMax, int maxSupercharge,
    int sc1Completed, int sc2Completed, int sc3Completed,
    Evidence confidence) {}
record UpgradeJob(String itemId, String instanceId, Integer targetLevel,
    String stage, Instant endAt, Long remainingMinutes, Evidence evidence) {}
record UpgradeDefinition(String itemId, int targetLevel,
    Long baseMinutes, Long cost, String currency, String sourceUrl,
    String catalogVersion) {}
record StepProjection(String itemId, int targetLevel, Long baseMinutes,
    Long passMinutes, Long remainingMinutes, boolean timeVerified) {}
record SourceInfo(String kind, String urlOrFile, Instant observedAt) {}
enum Evidence { GAME_EXPORT, OFFICIAL_API, SCREENSHOT, INFERRED, UNKNOWN }
```

## Esquema da carga
`data/snapshot_2026-10-19.json` contém objetos nomeados em português (snapshot ilustrativo), mas a camada Java deve usar IDs canônicos em inglês. `data/upgrade_steps.json` inclui `base_days` e `pass_days` opcionais: `null` significa falta de tempo confirmado. `confidence=print` descreve certeza do **nível**, não necessariamente do custo/duração.

## Regras de dados
- `quantity >= 1`; `0 <= countAtMax <= quantity`.
- `0 <= sc3Completed <= sc2Completed <= sc1Completed <= countAtMax` quando todas as unidades com SC são base FULL. Se o objeto está com mistura de níveis, preservar contagem e validar individualmente.
- Em nível base pendente, `countAtMax` é quantidade já no nível máximo; as demais unidades têm `currentLevel` homogêneo apenas como aproximação do snapshot. Substituir por instâncias ou distribuição de níveis quando a exportação trouxer.
- Se `currentLevel == null`, item é desconhecido e nunca pode ser FULL confirmado.
- `remainingMinutes` no job tem precedência para previsão de fim de etapa iniciada; `baseMinutes` permanece imutável.
- Snapshot inicial não substitui histórico imutável; cada sincronização cria cópia datada.
