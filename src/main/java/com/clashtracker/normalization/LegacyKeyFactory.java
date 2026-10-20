package com.clashtracker.normalization;

import java.text.Normalizer;
import java.util.Locale;

public final class LegacyKeyFactory {
    private LegacyKeyFactory() {
    }

    public static String fromLegacyName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "legacy_ptbr_unknown";
        }
        String normalized = Normalizer.normalize(rawName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
        return "legacy_ptbr_" + (normalized.isBlank() ? "unknown" : normalized);
    }

    public static String categoryKey(String rawCategory) {
        if (rawCategory == null) {
            return "UNKNOWN";
        }
        return switch (rawCategory) {
            case "Defesas" -> "DEFENSES";
            case "Recursos" -> "RESOURCES";
            case "Armadilhas" -> "TRAPS";
            case "Muros" -> "WALLS";
            case "Heróis" -> "HEROES";
            case "Guardiões" -> "GUARDIANS";
            case "Pets" -> "PETS";
            case "Tropas" -> "TROOPS";
            case "Feitiços" -> "SPELLS";
            case "Máquinas de Cerco" -> "SIEGE_MACHINES";
            case "Defesas Artesanais" -> "CRAFTED_DEFENSES";
            case "Construções Auxiliares" -> "SUPPORT_BUILDINGS";
            case "Sobrecargas" -> "SUPERCHARGES";
            default -> "LEGACY_" + fromLegacyName(rawCategory).substring("legacy_ptbr_".length()).toUpperCase(Locale.ROOT);
        };
    }
}
